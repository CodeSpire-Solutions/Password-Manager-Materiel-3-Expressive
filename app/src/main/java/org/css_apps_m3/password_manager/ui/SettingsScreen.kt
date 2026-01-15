package org.css_apps_m3.password_manager.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.AccentColorPicker
import org.css_apps_m3.password_manager.AppPrefs
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.ui.theme.md_theme_light_secondary
import org.css_apps_m3.password_manager.util.CsvReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
        ?: return

    val repo = remember { PasswordRepository(context) }
    val hapticFeedback = LocalHapticFeedback.current

    var newPassword by remember { mutableStateOf("") }
    var biometricsEnabled by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var dynamicTheme by remember { mutableStateOf(prefs.getBoolean("dynamic_theme", false)) }
    var customAccent by remember { mutableStateOf(prefs.getInt("custom_accent", 0xFF6200EE.toInt())) }
    var cornerRadius by remember { mutableStateOf(prefs.getFloat("corner_radius", 12f)) }
    var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }

    var pendingImport by remember { mutableStateOf<List<PasswordEntry>?>(null) }
    var showImportConflictDialog by remember { mutableStateOf(false) }

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
                if (count == 0) "No passwords to export"
                else "Exported $count passwords",
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

        val existing = repo.loadPasswords()

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

    if (showImportConflictDialog && pendingImport != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConflictDialog = false
                pendingImport = null
            },
            title = {
                Text("Duplicate passwords found")
            },
            text = {
                Text(
                    "Some passwords already exist.\n\n" +
                            "Do you want to replace existing entries or skip duplicates?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val existing = repo.loadPasswords().toMutableList()

                        pendingImport!!.forEach { imp ->
                            val index = existing.indexOfFirst {
                                it.url == imp.url && it.username == imp.username
                            }
                            if (index >= 0) {
                                existing[index] = imp // REPLACE
                            } else {
                                existing.add(imp)
                            }
                        }

                        repo.saveLocal(existing)

                        Toast.makeText(
                            context,
                            "Passwords imported (duplicates replaced)",
                            Toast.LENGTH_SHORT
                        ).show()

                        showImportConflictDialog = false
                        pendingImport = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val existing = repo.loadPasswords().toMutableList()

                        pendingImport!!.forEach { imp ->
                            val exists = existing.any {
                                it.url == imp.url && it.username == imp.username
                            }
                            if (!exists) existing.add(imp)
                        }

                        repo.saveLocal(existing)

                        Toast.makeText(
                            context,
                            "Passwords imported (duplicates skipped)",
                            Toast.LENGTH_SHORT
                        ).show()

                        showImportConflictDialog = false
                        pendingImport = null
                    }
                ) {
                    Text("Skip")
                }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            item {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // --- Dark Mode ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dark Mode")
                    Switch(
                        checked = darkMode,
                        onCheckedChange = {
                            darkMode = it
                            AppPrefs.saveDarkMode(context, darkMode)
                        }
                    )
                }
            }

            // --- Dynamic Theme ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dynamic Material You")
                        Switch(
                            checked = dynamicTheme,
                            onCheckedChange = {
                                dynamicTheme = it
                                prefs.edit()
                                    .putBoolean("dynamic_theme", dynamicTheme)
                                    .apply()
                            }
                        )
                    }
                }
            }

            // --- Haptics ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Haptic Feedback")
                    Switch(
                        checked = haptics,
                        onCheckedChange = {
                            haptics = it
                            prefs.edit().putBoolean("haptics", haptics).apply()
                        }
                    )
                }
            }

            // --- Accent Color ---
            item {
                Text("Custom Accent Color", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AccentColorPicker(
                    selectedColor = customAccent,
                    onColorSelected = {
                        customAccent = it
                        prefs.edit().putInt("custom_accent", customAccent).apply()
                    }
                )
            }

            // --- Corner Radius ---
            item {
                Text("Corner Radius: ${cornerRadius.toInt()}dp")
                Slider(
                    value = cornerRadius,
                    onValueChange = {
                        val old = cornerRadius.toInt()
                        cornerRadius = it
                        if (haptics && old != it.toInt()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    valueRange = 4f..32f,
                    onValueChangeFinished = {
                        prefs.edit().putFloat("corner_radius", cornerRadius).apply()
                    }
                )
            }

            item { Divider() }

            // --- Master Password ---
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
                ) {
                    Text("Change Master Password")
                }
            }

            item { Divider() }

            // --- EXPORT ---
            item {
                Button(
                    onClick = { exportLauncher.launch("passwords_export.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Passwords as CSV")
                }
                Spacer(Modifier.height(4.dp))
                Text("WARNING: Passwords get exported decrypted and in plain text!", style = MaterialTheme.typography.titleSmall)
            }

            // --- IMPORT ---
            //item {
            //    Button(
            //        onClick = {
            //            importLauncher.launch(arrayOf("text/*"))
            //        },
            //        modifier = Modifier.fillMaxWidth()
            //    ) {
            //        Text("Import Passwords from CSV")
            //    }
            //}
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

    encryptedPrefs.edit()
        .putString("master_password", password)
        .apply()
}