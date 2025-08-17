package org.css_apps_m3.password_manager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    passwordManager: PasswordManager = viewModel()
) {
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(false) }
    var csvUri by remember { mutableStateOf<Uri?>(null) }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        csvUri = uri
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Setup", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = masterPassword,
            onValueChange = { masterPassword = it },
            label = { Text("Master Password") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") }
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = biometricEnabled,
                onCheckedChange = { biometricEnabled = it }
            )
            Text("Biometrische Entsperrung aktivieren")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { csvPicker.launch("text/csv") }) {
            Text("CSV Datei importieren")
        }
        csvUri?.let {
            Text("CSV ausgewählt: ${it.lastPathSegment}")
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (masterPassword == confirmPassword && masterPassword.isNotEmpty()) {
                    passwordManager.saveMasterPassword(masterPassword, biometricEnabled)
                    csvUri?.let { passwordManager.importCsv(it) }
                    onSetupComplete()
                }
            },
            enabled = masterPassword.isNotEmpty() && masterPassword == confirmPassword
        ) {
            Text("Setup abschließen")
        }
    }
}