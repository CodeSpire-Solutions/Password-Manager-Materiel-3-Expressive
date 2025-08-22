package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.css_apps_m3.password_manager.data.PasswordRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    repo: PasswordRepository
) {
    var darkMode by remember { mutableStateOf(false) }
    var biometric by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Optik", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dunkles Design")
                Switch(checked = darkMode, onCheckedChange = { darkMode = it })
            }

            Spacer(Modifier.height(24.dp))

            Text("Sicherheit", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Biometrische Entsperrung")
                Switch(checked = biometric, onCheckedChange = { biometric = it })
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    // TODO: Exportfunktion implementieren
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Passwörter als CSV exportieren")
            }
        }
    }
}
