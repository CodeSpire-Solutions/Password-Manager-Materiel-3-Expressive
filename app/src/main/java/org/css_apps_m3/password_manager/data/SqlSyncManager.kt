package org.css_apps_m3.password_manager.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * and local SQLite databases to synchronise password entries.
 *
 * ## How the sync works
 * 1. A [Connection] is opened via [DriverManager] using the JDBC URL built from [SqlSyncConfig].
 * 2. The `passwords` table is created (CREATE TABLE IF NOT EXISTS) on first use.
 * 3. Each [PasswordEntry] is inserted or updated (UPSERT) keyed on (url, username).
 *    Rows that no longer exist locally are removed from the remote table (full replace).
 * 4. The connection is closed and a [SyncResult] is returned.
 *
 * ## SQLite mode
 * SQLite is handled via Android's [SQLiteDatabase] API instead of JDBC
 * (the file path is written to the app's files directory).
 *
 * ## Security notes
 * - Database credentials are stored in [EncryptedSharedPreferences] (AES-256).
 * - All network I/O runs on [Dispatchers.IO] â€“ never on the main thread.
 * - Use TLS/SSL on your database server. For MySQL, add `useSSL=true` to the
 */
class SqlSyncManager(private val context: Context) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Synchronise [passwords] to the database described by [config].
     * Must be called from a coroutine â€“ internally switches to [Dispatchers.IO].
     */
    suspend fun sync(
        config: SqlSyncConfig,
        passwords: List<PasswordEntry>
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            if (!config.isComplete()) {
                return@withContext SyncResult.Error(
                    "Configuration incomplete. Please provide host, database, and user."
                )
            }
            val masterPassword = loadMasterPassword()
                ?: return@withContext SyncResult.Error(
                    "Master password missing. Please set or unlock your master password first."
                )
            val cloudKey = resolveCloudCryptoKey(config, masterPassword)
            val cryptoSession = SyncCrypto.newSession(cloudKey)
            val encryptedPayload = passwords.map { it.encryptForSync(cryptoSession) }

            val affected = when (config.type) {
                DbType.SQLITE     -> syncSqlite(encryptedPayload)
                DbType.MYSQL      -> syncRemote(config, encryptedPayload)
            }

            val ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))

            SyncResult.Success(affected, ts)

        } catch (e: ClassNotFoundException) {
            SyncResult.Error(
                "JDBC driver not found: ${e.message}. Please reinstall the app.",
                e
            )
        } catch (e: NoClassDefFoundError) {
            // Some JDBC drivers are designed for Java SE and rely on APIs that Android does not ship
            SyncResult.Error(
                "This JDBC library uses Java SE classes that are missing on Android: ${e.message}. " +
                        "Direct DB sync is not available on this device.",
                e
            )
        } catch (e: java.sql.SQLException) {
            SyncResult.Error(
                "SQL error: ${e.message ?: "Unknown error"}" +
                        (if (e.errorCode != 0) " (Code ${e.errorCode})" else ""),
                e
            )
        } catch (e: Exception) {
            SyncResult.Error("Connection error: ${e.message ?: "Unknown error"}", e)
        }
    }

    /**
     * Tests the connection without writing any data.
     * Returns [SyncResult.Success] with rowsAffected = 0 on success.
     */
    suspend fun testConnection(config: SqlSyncConfig): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                if (!config.isComplete()) {
                    return@withContext SyncResult.Error(
                        "Configuration incomplete."
                    )
                }
                when (config.type) {
                    DbType.SQLITE -> {
                        // Just try to open the local db
                        openLocalSqlite().close()
                        SyncResult.Success(0, "â€“")
                    }
                    DbType.MYSQL -> {
                        openConnection(config).use { /* connection test only */ }
                        SyncResult.Success(0, "â€“")
                    }
                }
            } catch (e: NoClassDefFoundError) {
                SyncResult.Error(
                    "This JDBC library uses Java SE classes that are missing on Android: ${e.message}.",
                    e
                )
            } catch (e: Exception) {
                SyncResult.Error("Connection failed: ${e.message}", e)
            }
        }

    /**
     * Downloads all password entries from the configured database.
     */
    suspend fun syncFromCloud(config: SqlSyncConfig): Pair<List<PasswordEntry>, String> =
        withContext(Dispatchers.IO) {
            if (!config.isComplete()) {
                throw IllegalStateException("Configuration incomplete.")
            }
            val masterPassword = loadMasterPassword()
                ?: throw IllegalStateException("Master password missing.")
            val candidateKeys = resolveCloudCryptoKeysForDecrypt(config, masterPassword)
            val sessionsByKey = candidateKeys.associateWith { SyncCrypto.newSession(it) }
            val entries = when (config.type) {
                DbType.SQLITE -> readFromSqlite()
                DbType.MYSQL -> readFromRemote(config)
            }.map { it.decryptFromSync(candidateKeys, sessionsByKey) }
            val ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
            entries to ts
        }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------

    private fun syncRemote(config: SqlSyncConfig, passwords: List<PasswordEntry>): Int {
        openConnection(config).use { conn ->
            conn.autoCommit = false

            // 1. Ensure the table exists
            createTableIfNeeded(conn, config.type)

            // 2. Full-replace strategy: delete all rows, then re-insert
            //    (simpler and safer than row-by-row upsert for small datasets)
            conn.createStatement().use { it.executeUpdate("DELETE FROM passwords") }

            // 3. Bulk insert
            val sql = when (config.type) {
                DbType.MYSQL -> """
                    INSERT INTO passwords (name, url, username, password, note)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()

                DbType.SQLITE -> throw IllegalStateException("Use syncSqlite()")
            }

            var count = 0
            conn.prepareStatement(sql).use { stmt ->
                passwords.forEach { entry ->
                    stmt.setString(1, entry.name)
                    stmt.setString(2, entry.url)
                    stmt.setString(3, entry.username)
                    stmt.setString(4, entry.password)
                    stmt.setString(5, entry.note ?: "")
                    stmt.addBatch()
                    count++
                }
                if (count > 0) stmt.executeBatch()
            }

            conn.commit()
            return count
        }
    }

    private fun openConnection(config: SqlSyncConfig): Connection {
        // Load JDBC driver class (legacy first for Android compatibility)
        if (config.type == DbType.MYSQL) {
            val loaded = runCatching { Class.forName("com.mysql.jdbc.Driver") }.isSuccess ||
                runCatching { Class.forName("com.mysql.cj.jdbc.Driver") }.isSuccess
            if (!loaded) throw ClassNotFoundException("MySQL JDBC driver class not found")
        } else {
            Class.forName(config.type.jdbcDriver)
        }
        val url = config.type.buildUrl(config.host, config.port, config.database)
        return DriverManager.getConnection(url, config.dbUser, config.dbPassword)
    }

    private fun createTableIfNeeded(conn: Connection, type: DbType) {
        val ddl = when (type) {
            DbType.MYSQL -> """
                CREATE TABLE IF NOT EXISTS passwords (
                    id         INT AUTO_INCREMENT PRIMARY KEY,
                    name       VARCHAR(255)  NOT NULL DEFAULT '',
                    url        VARCHAR(500)  NOT NULL DEFAULT '',
                    username   VARCHAR(255)  NOT NULL DEFAULT '',
                    password   TEXT          NOT NULL,
                    note       TEXT,
                    synced_at  DATETIME      DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """.trimIndent()

            DbType.SQLITE -> throw IllegalStateException("Use syncSqlite()")
        }
        conn.createStatement().use { it.execute(ddl) }
    }

    private fun readFromRemote(config: SqlSyncConfig): List<PasswordEntry> {
        openConnection(config).use { conn ->
            createTableIfNeeded(conn, config.type)
            val sql = "SELECT name, url, username, password, note FROM passwords"
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val result = mutableListOf<PasswordEntry>()
                    while (rs.next()) {
                        result.add(
                            PasswordEntry(
                                name = rs.getString("name") ?: "",
                                url = rs.getString("url") ?: "",
                                username = rs.getString("username") ?: "",
                                password = rs.getString("password") ?: "",
                                note = rs.getString("note")
                            )
                        )
                    }
                    return result
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Local SQLite
    // -------------------------------------------------------------------------

    private fun syncSqlite(passwords: List<PasswordEntry>): Int {
        openLocalSqlite().use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS passwords (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    name      TEXT NOT NULL DEFAULT '',
                    url       TEXT NOT NULL DEFAULT '',
                    username  TEXT NOT NULL DEFAULT '',
                    password  TEXT NOT NULL,
                    note      TEXT,
                    synced_at TEXT DEFAULT (datetime('now'))
                )
                """.trimIndent()
            )

            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM passwords")
                passwords.forEach { entry ->
                    db.execSQL(
                        "INSERT INTO passwords (name, url, username, password, note) VALUES (?,?,?,?,?)",
                        arrayOf(entry.name, entry.url, entry.username, entry.password, entry.note ?: "")
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            return passwords.size
        }
    }

    private fun readFromSqlite(): List<PasswordEntry> {
        openLocalSqlite().use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS passwords (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    name      TEXT NOT NULL DEFAULT '',
                    url       TEXT NOT NULL DEFAULT '',
                    username  TEXT NOT NULL DEFAULT '',
                    password  TEXT NOT NULL,
                    note      TEXT,
                    synced_at TEXT DEFAULT (datetime('now'))
                )
                """.trimIndent()
            )
            val result = mutableListOf<PasswordEntry>()
            db.rawQuery(
                "SELECT name, url, username, password, note FROM passwords",
                null
            ).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val urlIndex = cursor.getColumnIndexOrThrow("url")
                val usernameIndex = cursor.getColumnIndexOrThrow("username")
                val passwordIndex = cursor.getColumnIndexOrThrow("password")
                val noteIndex = cursor.getColumnIndexOrThrow("note")
                while (cursor.moveToNext()) {
                    result.add(
                        PasswordEntry(
                            name = cursor.getString(nameIndex) ?: "",
                            url = cursor.getString(urlIndex) ?: "",
                            username = cursor.getString(usernameIndex) ?: "",
                            password = cursor.getString(passwordIndex) ?: "",
                            note = cursor.getString(noteIndex)
                        )
                    )
                }
            }
            return result
        }
    }

    private fun openLocalSqlite(): SQLiteDatabase {
        val dbFile = java.io.File(context.filesDir, "passwords_sync.db")
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    private fun loadMasterPassword(): String? {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return prefs.getString("master_password", null)?.takeIf { it.isNotBlank() }
    }

    private fun resolveCloudCryptoKey(config: SqlSyncConfig, masterPassword: String): String {
        return config.dbPassword.takeIf { it.isNotBlank() } ?: masterPassword
    }

    private fun resolveCloudCryptoKeysForDecrypt(config: SqlSyncConfig, masterPassword: String): List<String> {
        val keys = mutableListOf<String>()
        if (config.dbPassword.isNotBlank()) keys.add(config.dbPassword)
        keys.add(masterPassword)
        return keys.distinct()
    }

    // -------------------------------------------------------------------------
    // Config persistence (EncryptedSharedPreferences)
    // -------------------------------------------------------------------------

    companion object {
        private const val PREFS_NAME = "sql_sync_prefs"

        fun saveConfig(context: Context, config: SqlSyncConfig) {
            val prefs = openEncryptedPrefs(context)
            prefs.edit()
                .putString("db_type",     config.type.name)
                .putString("db_host",     config.host)
                .putInt   ("db_port",     config.port)
                .putString("db_name",     config.database)
                .putString("db_user",     config.dbUser)
                .putString("db_password", config.dbPassword)
                .putBoolean("auto_sync",  config.autoSync)
                .putString("last_sync_at", config.lastSyncAt)
                .putBoolean("last_sync_ok", config.lastSyncOk)
                .apply()
        }

        fun loadConfig(context: Context): SqlSyncConfig {
            val prefs = openEncryptedPrefs(context)
            val typeName = prefs.getString("db_type", DbType.MYSQL.name) ?: DbType.MYSQL.name
            val type = try { DbType.valueOf(typeName) } catch (_: Exception) { DbType.MYSQL }
            if (typeName != type.name) prefs.edit().putString("db_type", type.name).apply()
            return SqlSyncConfig(
                type       = type,
                host       = prefs.getString("db_host", "")     ?: "",
                port       = prefs.getInt   ("db_port", type.defaultPort),
                database   = prefs.getString("db_name", "")     ?: "",
                dbUser     = prefs.getString("db_user", "")     ?: "",
                dbPassword = prefs.getString("db_password", "") ?: "",
                autoSync   = prefs.getBoolean("auto_sync", false),
                lastSyncAt = prefs.getString("last_sync_at", "") ?: "",
                lastSyncOk = prefs.getBoolean("last_sync_ok", true)
            )
        }

        fun saveSyncStatus(context: Context, timestamp: String, ok: Boolean) {
            openEncryptedPrefs(context).edit()
                .putString("last_sync_at", timestamp)
                .putBoolean("last_sync_ok", ok)
                .apply()
        }

        private fun openEncryptedPrefs(context: Context): android.content.SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}

private fun PasswordEntry.encryptForSync(session: SyncCrypto.Session): PasswordEntry =
    copy(
        name = SyncCrypto.encrypt(name, session),
        url = SyncCrypto.encrypt(url, session),
        username = SyncCrypto.encrypt(username, session),
        password = SyncCrypto.encrypt(password, session),
        note = note?.let { SyncCrypto.encrypt(it, session) }
    )

private fun PasswordEntry.decryptFromSync(
    candidateKeys: List<String>,
    sessionsByKey: Map<String, SyncCrypto.Session>
): PasswordEntry =
    copy(
        name = decryptField(name, candidateKeys, sessionsByKey),
        url = decryptField(url, candidateKeys, sessionsByKey),
        username = decryptField(username, candidateKeys, sessionsByKey),
        password = decryptField(password, candidateKeys, sessionsByKey),
        note = note?.let { decryptField(it, candidateKeys, sessionsByKey) }
    )

private fun decryptField(
    value: String,
    keys: List<String>,
    sessionsByKey: Map<String, SyncCrypto.Session>
): String {
    val version = SyncCrypto.versionOf(value) ?: return value
    if (version != "v1" && version != "v2") return value
    var lastError: Throwable? = null
    keys.forEach { key ->
        val session = sessionsByKey[key] ?: return@forEach
        runCatching {
            SyncCrypto.decrypt(value, session, key)
        }.onSuccess { return it }
            .onFailure { lastError = it }
    }
    throw IllegalStateException("Unable to decrypt cloud record with configured keys", lastError)
}
