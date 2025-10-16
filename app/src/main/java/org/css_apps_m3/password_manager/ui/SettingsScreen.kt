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
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
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

    // --- CSV Export & Sync prefs as before ---
    val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    var serverUrl by remember { mutableStateOf(syncPrefs.getString("server_url", "") ?: "") }
    var syncUser by remember { mutableStateOf(syncPrefs.getString("sync_user", "") ?: "") }
    var syncPass by remember { mutableStateOf(syncPrefs.getString("sync_pass", "") ?: "") }
    var syncEnabled by remember { mutableStateOf(syncPrefs.getBoolean("sync_enabled", false)) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            if (uri != null) {
                val passwords = repo.loadPasswords()
                if (passwords.isEmpty()) {
                    Toast.makeText(context, "No passwords to export", Toast.LENGTH_SHORT).show()
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        OutputStreamWriter(out).use { writer ->
                            writer.appendLine("name,url,username,password,note")
                            passwords.forEach {
                                writer.appendLine("${it.name},${it.url},${it.username},${it.password},${it.note}")
                            }
                        }
                    }
                    Toast.makeText(context, "Passwords exported", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

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
            contentPadding = PaddingValues(bottom = 32.dp) // extra padding at bottom
        ) {
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
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
                                prefs.edit().putBoolean("dynamic_theme", dynamicTheme).apply()
                                // Apply theme globally
                                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                                    if (darkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                    else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                                )
                            }
                        )

                    }
                }
            }

            // --- Haptic feedback ---
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
                    onValueChange = { newRadius ->
                        val oldRadiusInt = cornerRadius.toInt()
                        cornerRadius = newRadius
                        if (haptics && cornerRadius.toInt() != oldRadiusInt) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    valueRange = 4f..32f,
                    onValueChangeFinished = {
                        if (haptics) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        prefs.edit().putFloat("corner_radius", cornerRadius).apply()
                    }
                )
            }

            item { Divider() }

            // --- Password Management ---
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
                            saveMasterPassword(context, newPassword, biometricsEnabled)
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

            // --- Export ---
            item {
                Button(
                    onClick = { exportLauncher.launch("passwords_export.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export Passwords as CSV") }
            }

            item { Divider() }
/*
            // --- Sync Section ---
            item { Text("Database Sync", style = MaterialTheme.typography.titleMedium) }

            item {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = syncUser,
                    onValueChange = { syncUser = it },
                    label = { Text("Sync Username") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = syncPass,
                    onValueChange = { syncPass = it },
                    label = { Text("Sync Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Sync")
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = { syncEnabled = it }
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        syncPrefs.edit()
                            .putString("server_url", serverUrl)
                            .putString("sync_user", syncUser)
                            .putString("sync_pass", syncPass)
                            .putBoolean("sync_enabled", syncEnabled)
                            .apply()
                        Toast.makeText(context, "Sync settings saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Sync Settings")
                }
            }

 */
        }
    }
}
private fun saveMasterPassword(context: Context, password: String, biometricsEnabled: Boolean) {
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
        .putBoolean("biometrics_enabled", biometricsEnabled)
        .apply()
}