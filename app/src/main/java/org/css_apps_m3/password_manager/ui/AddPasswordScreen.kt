package org.css_apps_m3.password_manager.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.css_apps_m3.password_manager.R
import org.css_apps_m3.password_manager.R.drawable.visibility_24px
import org.css_apps_m3.password_manager.R.drawable.visibility_off_24px
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPasswordScreen(
    navController: NavController,
    repository: PasswordRepository
) {
    var domain by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neues Passwort") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (domain.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                val newEntry = PasswordEntry(
                                    name = domain,
                                    url = domain,
                                    username = username,
                                    password = password,
                                    note = note
                                )

                                val currentList = repository.loadPasswords().toMutableList()
                                currentList.add(newEntry)
                                repository.saveLocal(currentList)

                                Log.d("AddPasswordScreen", "Neues Passwort gespeichert: $newEntry")

                                navController.popBackStack()
                            } else {
                                Log.w("AddPasswordScreen", "Eingaben unvollständig")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern")
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
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain / App") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username / Email") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                id = if (passwordVisible) visibility_24px else visibility_off_24px
                            ),
                            contentDescription = if (passwordVisible) "Passwort verstecken" else "Passwort anzeigen"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 4
            )
        }
    }
}
