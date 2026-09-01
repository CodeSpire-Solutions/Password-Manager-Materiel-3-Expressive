package org.css_apps_m3.password_manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.ui.ExpressiveHero
import org.css_apps_m3.password_manager.ui.ExpressiveSection
import org.css_apps_m3.password_manager.util.CsvReader

class SetupActivity : ComponentActivity() {

    private lateinit var repo: PasswordRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = PasswordRepository(this)

        var importedPasswords: List<PasswordEntry>? = null
        val csvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                importedPasswords = CsvReader.readPasswordsFromUri(this, it)
                if (importedPasswords.isNullOrEmpty()) {
                    Toast.makeText(this, "CSV could not be read.", Toast.LENGTH_SHORT).show()
                } else {
                    repo.saveLocal(importedPasswords!!)
                    Toast.makeText(this, "${importedPasswords!!.size} passwords imported", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            AppThemed {
                var password by remember { mutableStateOf("") }
                var biometricsEnabled by remember { mutableStateOf(false) }

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(12.dp))
                        ExpressiveHero(
                            eyebrow = "Step 1 of 2",
                            title = "Create your private vault.",
                            supportingText = "Your passwords stay encrypted on this device. You can import an existing vault anytime."
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "Your master password never leaves this device.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        ExpressiveSection(title = "Vault protection", icon = Icons.Default.Lock) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Master password") },
                                supportingText = { Text("Choose a password you will remember.") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                shape = MaterialTheme.shapes.medium
                            )
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text("Biometric unlock", style = MaterialTheme.typography.titleSmall)
                                        Text("Use fingerprint or face recognition after setup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = biometricsEnabled, onCheckedChange = { biometricsEnabled = it })
                                }
                            }
                        }
                        ExpressiveSection(title = "Bring your data", icon = Icons.Default.CloudUpload) {
                            OutlinedButton(
                                onClick = { csvLauncher.launch("text/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) { Text("Import passwords from CSV") }
                            Text("You can skip this and add accounts later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                if (password.isBlank()) {
                                    Toast.makeText(this@SetupActivity, "Enter a master password", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (repo.loadPasswords().isEmpty()) repo.saveLocal(emptyList())
                                    saveMasterPassword(password, biometricsEnabled)
                                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("setup_done", true).apply()
                                    startActivity(Intent(this@SetupActivity, DisclaimerActivity::class.java))
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("Continue") }
                    }
                }
            }
        }
    }

    private fun saveMasterPassword(password: String, biometricsEnabled: Boolean) {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encryptedPrefs = EncryptedSharedPreferences.create(
            this, "vault_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        encryptedPrefs.edit().putString("master_password", password).putBoolean("biometrics_enabled", biometricsEnabled).apply()
    }
}
