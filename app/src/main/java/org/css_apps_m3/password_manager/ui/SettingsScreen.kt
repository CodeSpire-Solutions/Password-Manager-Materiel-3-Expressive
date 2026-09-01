package org.css_apps_m3.password_manager.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch
import org.css_apps_m3.password_manager.AccentColorPicker
import org.css_apps_m3.password_manager.AppPrefs
import org.css_apps_m3.password_manager.MainActivity
import org.css_apps_m3.password_manager.data.DbType
import org.css_apps_m3.password_manager.data.SqlSyncConfig
import org.css_apps_m3.password_manager.data.SqlSyncManager
import org.css_apps_m3.password_manager.data.SyncResult
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.autofill.AutofillUtils
import org.css_apps_m3.password_manager.util.CsvReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity ?: return

    val repo           = remember { PasswordRepository(context) }
    val syncManager    = remember { SqlSyncManager(context) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // --- Appearance prefs ---
    var newPassword    by remember { mutableStateOf("") }
    val prefs          = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var darkMode       by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var dynamicTheme   by remember { mutableStateOf(prefs.getBoolean("dynamic_theme", false)) }
    var customAccent   by remember { mutableStateOf(prefs.getInt("custom_accent", 0xFF6200EE.toInt())) }
    var cornerRadius   by remember { mutableStateOf(prefs.getFloat("corner_radius", 12f)) }
    var haptics        by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }

    // --- CSV import state ---
    var pendingImport          by remember { mutableStateOf<List<PasswordEntry>?>(null) }
    var showImportConflictDialog by remember { mutableStateOf(false) }

    // --- SQL Sync state ---
    var selectedDbType   by remember { mutableStateOf(DbType.MYSQL) }
    var sqlHost          by remember { mutableStateOf("") }
    var sqlPort          by remember { mutableStateOf(DbType.MYSQL.defaultPort.toString()) }
    var sqlDatabase      by remember { mutableStateOf("") }
    var sqlUser          by remember { mutableStateOf("") }
    var sqlPassword      by remember { mutableStateOf("") }
    var sqlAutoSync      by remember { mutableStateOf(false) }
    var lastSyncAt       by remember { mutableStateOf("") }
    var lastSyncOk       by remember { mutableStateOf(true) }
    var isSyncing        by remember { mutableStateOf(false) }
    var isDownloading    by remember { mutableStateOf(false) }
    var isTesting        by remember { mutableStateOf(false) }
    var showDbTypeMenu   by remember { mutableStateOf(false) }
    var showSqlPassword  by remember { mutableStateOf(false) }
    var syncFeedback     by remember { mutableStateOf("") }
    var isAutofillDefault by remember { mutableStateOf(false) }

    // Load saved SQL config once
    LaunchedEffect(Unit) {
        val cfg      = SqlSyncManager.loadConfig(context)
        selectedDbType = cfg.type
        sqlHost        = cfg.host
        sqlPort        = cfg.port.toString()
        sqlDatabase    = cfg.database
        sqlUser        = cfg.dbUser
        sqlPassword    = cfg.dbPassword
        sqlAutoSync    = cfg.autoSync
        lastSyncAt     = cfg.lastSyncAt
        lastSyncOk     = cfg.lastSyncOk
        isAutofillDefault = AutofillUtils.isOurServiceDefault(context)
    }

    /** Persist current SQL config to EncryptedSharedPreferences. */
    fun saveCurrentSqlConfig() {
        SqlSyncManager.saveConfig(
            context,
            SqlSyncConfig(
                type       = selectedDbType,
                host       = sqlHost,
                port       = sqlPort.toIntOrNull() ?: selectedDbType.defaultPort,
                database   = sqlDatabase,
                dbUser     = sqlUser,
                dbPassword = sqlPassword,
                autoSync   = sqlAutoSync,
                lastSyncAt = lastSyncAt,
                lastSyncOk = lastSyncOk
            )
        )
    }

    // --- CSV EXPORT ---
    val exportLauncher = remember(activity) {
        activity.activityResultRegistry.register(
            "export_csv",
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            if (uri == null) return@register
            val count = repo.exportPasswordsToCsv(activity, uri)
            Toast.makeText(
                activity,
                if (count == 0) "No passwords to export" else "Exported $count passwords",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --- CSV IMPORT ---
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = CsvReader.readPasswordsFromUri(context, uri)
        if (imported.isNullOrEmpty()) {
            Toast.makeText(context, "CSV could not be read", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val existing     = repo.loadPasswords()
        val hasDuplicates = imported.any { imp ->
            existing.any { it.url == imp.url && it.username == imp.username }
        }
        if (hasDuplicates) {
            pendingImport = imported
            showImportConflictDialog = true
        } else {
            repo.saveLocal(existing + imported)
            Toast.makeText(context, "Imported ${imported.size} passwords", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Import conflict dialog ---
    if (showImportConflictDialog && pendingImport != null) {
        AlertDialog(
            onDismissRequest = { showImportConflictDialog = false; pendingImport = null },
            title = { Text("Duplicate passwords found") },
            text  = { Text("Some passwords already exist.\n\nReplace existing entries or skip duplicates?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val existing = repo.loadPasswords().toMutableList()
                        pendingImport!!.forEach { imp ->
                            val idx = existing.indexOfFirst { it.url == imp.url && it.username == imp.username }
                            if (idx >= 0) existing[idx] = imp else existing.add(imp)
                        }
                        repo.saveLocal(existing)
                        Toast.makeText(context, "Imported (duplicates replaced)", Toast.LENGTH_SHORT).show()
                        showImportConflictDialog = false; pendingImport = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val existing = repo.loadPasswords().toMutableList()
                    pendingImport!!.forEach { imp ->
                        if (existing.none { it.url == imp.url && it.username == imp.username }) existing.add(imp)
                    }
                    repo.saveLocal(existing)
                    Toast.makeText(context, "Imported (duplicates skipped)", Toast.LENGTH_SHORT).show()
                    showImportConflictDialog = false; pendingImport = null
                }) { Text("Skip") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item {
                ExpressiveHero(
                    eyebrow = "Control center",
                    title = "Make the vault yours.",
                    supportingText = "Appearance, Autofill and encrypted sync stay in one place."
                )
            }

            // â”€â”€â”€ Appearance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp))
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dark Mode")
                    Switch(checked = darkMode, onCheckedChange = {
                        darkMode = it; AppPrefs.saveDarkMode(context, darkMode)
                    })
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dynamic Material You")
                    Switch(checked = dynamicTheme, onCheckedChange = {
                        dynamicTheme = it
                        AppPrefs.saveDynamicTheme(context, it)
                        })
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Haptic Feedback")
                    Switch(checked = haptics, onCheckedChange = {
                        haptics = it
                        AppPrefs.saveHaptics(context, it)
                    })
                }
            }

            item {
                Text("Custom Accent Color", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AccentColorPicker(selectedColor = customAccent, onColorSelected = {
                    customAccent = it
                    AppPrefs.saveCustomAccent(context, it)
                })
            }

            item {
                Text("Corner Radius: ${cornerRadius.toInt()}dp")
                Slider(
                    value = cornerRadius,
                    onValueChange = {
                        val old = cornerRadius.toInt(); cornerRadius = it
                        AppPrefs.updateCornerRadius(it)
                        if (haptics && old != it.toInt())
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    valueRange = 4f..32f,
                    onValueChangeFinished = { AppPrefs.saveCornerRadius(context, cornerRadius) }
                )
            }

            item { HorizontalDivider() }

            // â”€â”€â”€ Master Password â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            item {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Master Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        if (newPassword.isBlank()) {
                            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            saveMasterPassword(context, newPassword)
                            Toast.makeText(context, "Master password updated", Toast.LENGTH_SHORT).show()
                            newPassword = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Change Master Password") }
            }

            item { HorizontalDivider() }

            // â”€â”€â”€ CSV Export / Import â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            item {
                Button(
                    onClick = { exportLauncher.launch("passwords_export.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export Passwords as CSV") }
                Spacer(Modifier.height(4.dp))
                Text(
                    "WARNING: Passwords get exported decrypted and in plain text!",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            item {
                Button(
                    onClick = {
                        MainActivity.ignoreNextPause = true
                        importLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "text/csv"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import Passwords from CSV") }
            }

            item { HorizontalDivider() }

            // --- Autofill ---
            if (AutofillUtils.isAutofillSupported()) {
                item {
                    Text("Autofill", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isAutofillDefault) "Status: Default Autofill active"
                        else "Status: Not set as default",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutofillDefault) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isAutofillDefault) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tip: On Android 14, open 'Passwords, passkeys & accounts' and set this app as your preferred service.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            AutofillUtils.openAutofillSettings(context)
                            isAutofillDefault = AutofillUtils.isOurServiceDefault(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isAutofillDefault) "Open Autofill settings" else "Set as default Autofill")
                    }
                }
                item { HorizontalDivider() }
            }

            // â”€â”€â”€ Cloud SQL Sync â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            item {
                Text(
                    "Cloud SQL Sync",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sync your passwords directly to your own SQL database (MySQL or a local SQLite file).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // DB Type Dropdown
            item {
                Text("Database type", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = showDbTypeMenu,
                    onExpandedChange = { showDbTypeMenu = it }
                ) {
                    OutlinedTextField(
                        value = selectedDbType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Database type") },
                        trailingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showDbTypeMenu,
                        onDismissRequest = { showDbTypeMenu = false }
                    ) {
                        DbType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedDbType = type
                                    sqlPort        = type.defaultPort.toString()
                                    showDbTypeMenu = false
                                    saveCurrentSqlConfig()
                                }
                            )
                        }
                    }
                }
            }


            // Host + Port (only for remote DBs)
            if (selectedDbType != DbType.SQLITE) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sqlHost,
                            onValueChange = { sqlHost = it },
                            label = { Text("Host / IP") },
                            placeholder = { Text("db.example.com") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        OutlinedTextField(
                            value = sqlPort,
                            onValueChange = { sqlPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.width(90.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            // Database name
            item {
                OutlinedTextField(
                    value = sqlDatabase,
                    onValueChange = { sqlDatabase = it },
                    label = {
                        Text(if (selectedDbType == DbType.SQLITE) "Filename (e.g. backup.db)" else "Database name")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // User + Password (only for remote DBs)
            if (selectedDbType != DbType.SQLITE) {
                item {
                    OutlinedTextField(
                        value = sqlUser,
                        onValueChange = { sqlUser = it },
                        label = { Text("Database user") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = sqlPassword,
                        onValueChange = { sqlPassword = it },
                        label = { Text("Database password") },
                        singleLine = true,
                        visualTransformation = if (showSqlPassword) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSqlPassword = !showSqlPassword }) {
                                Icon(
                                    imageVector = if (showSqlPassword) Icons.Default.VisibilityOff
                                                  else Icons.Default.Visibility,
                                    contentDescription = if (showSqlPassword) "Hide password"
                                                         else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Auto-sync toggle
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-Sync")
                        Text(
                            "Automatically sync after every change",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = sqlAutoSync, onCheckedChange = {
                        sqlAutoSync = it; saveCurrentSqlConfig()
                    })
                }
            }

            // Save config button
            item {
                Button(
                    onClick = {
                        saveCurrentSqlConfig()
                        Toast.makeText(context, "Configuration saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) { Text("Save configuration") }
            }

            // Test Connection button
            item {
                OutlinedButton(
                    onClick = {
                        saveCurrentSqlConfig()
                        isTesting = true
                        syncFeedback = ""
                        val cfg = SqlSyncConfig(
                            type       = selectedDbType,
                            host       = sqlHost,
                            port       = sqlPort.toIntOrNull() ?: selectedDbType.defaultPort,
                            database   = sqlDatabase,
                            dbUser     = sqlUser,
                            dbPassword = sqlPassword,
                            autoSync   = sqlAutoSync
                        )
                        coroutineScope.launch {
                            val result = syncManager.testConnection(cfg)
                            isTesting = false
                            syncFeedback = when (result) {
                                is SyncResult.Success -> "OK: Connection successful!"
                                is SyncResult.Error   -> "ERROR: ${result.message}"
                            }
                        }
                    },
                    enabled  = !isTesting && !isSyncing && !isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test connection")
                }
            }

            // Sync Now button
            item {
                Button(
                    onClick = {
                        saveCurrentSqlConfig()
                        isSyncing = true
                        syncFeedback = ""
                        val cfg = SqlSyncConfig(
                            type       = selectedDbType,
                            host       = sqlHost,
                            port       = sqlPort.toIntOrNull() ?: selectedDbType.defaultPort,
                            database   = sqlDatabase,
                            dbUser     = sqlUser,
                            dbPassword = sqlPassword,
                            autoSync   = sqlAutoSync
                        )
                        coroutineScope.launch {
                            val passwords = repo.loadPasswords()
                            val result    = syncManager.sync(cfg, passwords)
                            isSyncing  = false
                            when (result) {
                                is SyncResult.Success -> {
                                    lastSyncAt   = result.timestamp
                                    lastSyncOk   = true
                                    syncFeedback = "OK: Synced ${result.rowsAffected} entries (${result.timestamp})"
                                    SqlSyncManager.saveSyncStatus(context, result.timestamp, true)
                                }
                                is SyncResult.Error -> {
                                    lastSyncOk   = false
                                    syncFeedback = "ERROR: ${result.message}"
                                    SqlSyncManager.saveSyncStatus(context, "", false)
                                }
                            }
                        }
                    },
                    enabled  = !isSyncing && !isTesting && !isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSyncing) "Syncing..." else "Sync now")
                }
            }

            // Sync from cloud button
            item {
                OutlinedButton(
                    onClick = {
                        saveCurrentSqlConfig()
                        isDownloading = true
                        syncFeedback = ""
                        val cfg = SqlSyncConfig(
                            type       = selectedDbType,
                            host       = sqlHost,
                            port       = sqlPort.toIntOrNull() ?: selectedDbType.defaultPort,
                            database   = sqlDatabase,
                            dbUser     = sqlUser,
                            dbPassword = sqlPassword,
                            autoSync   = sqlAutoSync
                        )
                        coroutineScope.launch {
                            runCatching {
                                syncManager.syncFromCloud(cfg)
                            }.onSuccess { (entries, timestamp) ->
                                repo.saveLocal(entries)
                                lastSyncAt = timestamp
                                lastSyncOk = true
                                syncFeedback = "OK: Downloaded ${entries.size} entries ($timestamp)"
                                SqlSyncManager.saveSyncStatus(context, timestamp, true)
                            }.onFailure { error ->
                                lastSyncOk = false
                                syncFeedback = "ERROR: ${error.message ?: "Download failed"}"
                                SqlSyncManager.saveSyncStatus(context, "", false)
                            }
                            isDownloading = false
                        }
                    },
                    enabled  = !isSyncing && !isTesting && !isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isDownloading) "Downloading..." else "Sync from cloud")
                }
            }

            // Feedback / status row
            if (syncFeedback.isNotEmpty()) {
                item {
                    val isError = syncFeedback.startsWith("ERROR:")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            syncFeedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Last sync info (from persisted state)
            if (lastSyncAt.isNotEmpty()) {
                item {
                    Text(
                        "Last sync: $lastSyncAt ${if (lastSyncOk) "✓" else "✗"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun saveMasterPassword(context: Context, password: String) {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "vault_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    encryptedPrefs.edit().putString("master_password", password).apply()
}
