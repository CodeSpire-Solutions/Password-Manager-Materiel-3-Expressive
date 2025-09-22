package org.css_apps_m3.password_manager.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.data.PasswordRepository
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { PasswordRepository(context) }

    var newPassword by remember { mutableStateOf("") }
    var biometricsEnabled by remember { mutableStateOf(false) }

    // SharedPreferences für Theme
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    // CSV Export Launcher
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
                            // Header
                            writer.appendLine("name,url,username,password,note")
                            // Data
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { /*
            // Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dark Mode")
                Switch(
                    checked = darkMode,
                    onCheckedChange = {
                        darkMode = it
                        prefs.edit().putBoolean("dark_mode", darkMode).apply()
                        Toast.makeText(
                            context,
                            if (darkMode) "Dark Mode Enabled" else "Light Mode Enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            Divider()
            */

            // Change Password
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Master Password") },
                modifier = Modifier.fillMaxWidth()
            )

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

            Divider()

            // Export Button
            Button(
                onClick = {
                    exportLauncher.launch("passwords_export.csv")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Passwords as CSV")
            }
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
