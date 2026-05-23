package org.css_apps_m3.password_manager.data

/**
 * Supported database types for direct SQL synchronisation.
 *
 * | Type        | JDBC URL pattern                                          | Default port |
 * |-------------|-----------------------------------------------------------|-------------|
 * | MYSQL       | jdbc:mysql://host:port/database                           | 3306        |
 * | SQLITE      | Local .db file exported via Android Storage Access (SAF)  | –           |
 */
enum class DbType(
    val displayName: String,
    val defaultPort: Int,
    val jdbcDriver: String,
    val urlTemplate: String // placeholders: {host}, {port}, {database}
) {
    MYSQL(
        displayName  = "MySQL / MariaDB",
        defaultPort  = 3306,
        jdbcDriver   = "com.mysql.jdbc.Driver",
        urlTemplate  = "jdbc:mysql://{host}:{port}/{database}?useSSL=true&sslMode=PREFERRED&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=15000"
    ),
    SQLITE(
        displayName  = "SQLite (local .db file)",
        defaultPort  = 0,
        jdbcDriver   = "",            // unused – handled via Android SQLiteDatabase API
        urlTemplate  = ""
    );

    /** Build the final JDBC connection URL from the given config. */
    fun buildUrl(host: String, port: Int, database: String): String =
        urlTemplate
            .replace("{host}", host)
            .replace("{port}", port.toString())
            .replace("{database}", database)
}

/**
 * All settings the user enters to connect to a remote (or local) SQL database.
 * Sensitive fields (dbPassword) are stored in EncryptedSharedPreferences –
 * this data class is only used in memory.
 */
data class SqlSyncConfig(
    val type: DbType        = DbType.MYSQL,
    val host: String        = "",
    val port: Int           = DbType.MYSQL.defaultPort,
    val database: String    = "",
    val dbUser: String      = "",
    val dbPassword: String  = "",
    /** If true, the sync runs automatically whenever a password is saved/edited/deleted. */
    val autoSync: Boolean   = false,
    /** ISO-8601 timestamp of the last successful sync (empty = never). */
    val lastSyncAt: String  = "",
    val lastSyncOk: Boolean = true
) {
    val isRemote: Boolean get() = type != DbType.SQLITE

    /** Returns true when the config has enough data to attempt a connection. */
    fun isComplete(): Boolean = when (type) {
        DbType.SQLITE     -> true   // no connection params needed
        DbType.MYSQL      -> host.isNotBlank() && database.isNotBlank() && dbUser.isNotBlank()
    }
}

/** Result returned from a sync attempt. */
sealed class SyncResult {
    data class Success(val rowsAffected: Int, val timestamp: String) : SyncResult()
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
}
