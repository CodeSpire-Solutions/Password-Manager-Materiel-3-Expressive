package org.css_apps_m3.password_manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.ui.theme.PasswordViewerTheme
import org.css_apps_m3.password_manager.util.CsvReader

class SetupActivity : ComponentActivity() {

    private lateinit var repo: PasswordRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PasswordRepository(this)

        var importedPasswords: List<org.css_apps_m3.password_manager.model.PasswordEntry>? = null

        val csvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                importedPasswords = CsvReader.readPasswordsFromUri(this, it)
                if (importedPasswords.isNullOrEmpty()) {
                    Toast.makeText(this, "CSV couldnt be read.", Toast.LENGTH_SHORT).show()
                } else {
                    repo.saveLocal(importedPasswords!!)
                    Toast.makeText(this, "${importedPasswords!!.size} Passwords imported", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            PasswordViewerTheme {
                var password by remember { mutableStateOf("") }
                var csvImported by remember { mutableStateOf(false) }
                var biometricsEnabled by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Master-Password") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Checkbox(
                            checked = biometricsEnabled,
                            onCheckedChange = { biometricsEnabled = it }
                        )
                        Text(
                            text = "Activate Biometric Authentication",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { csvLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import CSV")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (password.isBlank()) {
                                Toast.makeText(this@SetupActivity, "Please enter Password", Toast.LENGTH_SHORT).show()
                            } else if (repo.loadPasswords().isEmpty()) {
                                Toast.makeText(this@SetupActivity, "Please import CSV", Toast.LENGTH_SHORT).show()
                            } else {
                                saveMasterPassword(password, biometricsEnabled)
                                getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("setup_done", true)
                                    .apply()
                                startActivity(Intent(this@SetupActivity, MainActivity::class.java))
                                finish()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = repo.loadPasswords().isNotEmpty()
                    ) {
                        Text("Complete Setups")
                    }
                }
            }
        }
    }

    private fun saveMasterPassword(password: String, biometricsEnabled: Boolean) {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPrefs = EncryptedSharedPreferences.create(
            this,
            "vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        encryptedPrefs.edit()
            .putString("master_password", password)
            .putBoolean("biometrics_enabled", biometricsEnabled)
            .apply()

        //Log.d("VaultDebug", "SetupActivity -> Password saved: $password")
        //Log.d("VaultDebug", "SetupActivity -> biometrics_enabled: $biometricsEnabled")
    }
}
