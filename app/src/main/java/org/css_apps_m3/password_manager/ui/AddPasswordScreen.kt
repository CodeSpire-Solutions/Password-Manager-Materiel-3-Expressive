package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.css_apps_m3.password_manager.R
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.data.SqlSyncManager
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.model.CustomField
import org.css_apps_m3.password_manager.util.PasswordGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPasswordScreen(
    navController: NavController,
    repository: PasswordRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncManager = remember { SqlSyncManager(context) }
    var domain by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var customFields by remember { mutableStateOf(emptyList<CustomField>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Password") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                                    note = note.trim().ifBlank { null },
                                    customFields = customFields.cleaned()
                                )

                                val currentList = repository.loadPasswords().toMutableList()
                                currentList.add(newEntry)
                                repository.saveLocal(currentList)
                                val syncConfig = SqlSyncManager.loadConfig(context)
                                if (syncConfig.autoSync) {
                                    coroutineScope.launch {
                                        syncManager.sync(syncConfig, currentList)
                                    }
                                }

                                //Log.d("AddPasswordScreen", "New Password Saved: $newEntry")

                                navController.popBackStack()
                            } else {
                                //Log.w("AddPasswordScreen", "Input incomplete")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveHero(
                eyebrow = "New entry",
                title = "Save a new account",
                supportingText = "Use a strong generated password and add the details you need."
            )
            ExpressiveSection(title = "Account", icon = Icons.Default.Person) {
                OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text("Domain / App") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            }
            ExpressiveSection(title = "Credentials", icon = Icons.Default.Key) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            TextButton(onClick = { password = PasswordGenerator.generate() }) { Text("Generate") }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                val iconId = if (passwordVisible) R.drawable.visibility else R.drawable.visibilityoff
                                Icon(painter = painterResource(iconId), contentDescription = if (passwordVisible) "Hide password" else "Show password")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium
                )
                Text("Generate creates a 20-character password with letters, numbers and symbols.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 4, shape = MaterialTheme.shapes.medium)
                CustomFieldsEditor(customFields = customFields, onCustomFieldsChange = { customFields = it })
            }
        }
    }
}

private fun List<CustomField>.cleaned(): List<CustomField> =
    map { it.copy(label = it.label.trim(), value = it.value.trim()) }
        .filter { it.label.isNotBlank() }
