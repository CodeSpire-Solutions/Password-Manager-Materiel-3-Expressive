package org.css_apps_m3.password_manager

//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(passwordManager: PasswordManager = viewModel()) {
    var masterPassword by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(passwordManager.isBiometricEnabled()) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = masterPassword,
            onValueChange = { masterPassword = it },
            label = { Text("Master Passwort ändern") }
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = biometricEnabled,
                onCheckedChange = { biometricEnabled = it }
            )
            Text("Biometrische Entsperrung")
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (masterPassword.isNotEmpty()) {
                passwordManager.saveMasterPassword(masterPassword, biometricEnabled)
            }
        }) {
            Text("Speichern")
        }
    }
}
