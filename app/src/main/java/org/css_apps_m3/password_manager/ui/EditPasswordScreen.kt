package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.css_apps_m3.password_manager.model.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPasswordScreen(
    domain: String,
    accounts: List<PasswordEntry>,
    initiallySelectedUsername: String?,
    onSave: (oldEntry: PasswordEntry, updatedEntry: PasswordEntry) -> Unit,
    onDelete: (PasswordEntry) -> Unit,
    onCancel: () -> Unit
) {
    val usernames = remember(accounts) {
        accounts.map { it.username }.distinct().sortedBy { it.lowercase() }
    }

    val initialSelection = remember(usernames, initiallySelectedUsername) {
        if (usernames.size <= 1) {
            usernames.firstOrNull()
        } else {
            // Force explicit selection when multiple accounts exist
            null
        }
    }

    var selectedUsername by remember(usernames, initialSelection) {
        mutableStateOf(initialSelection)
    }

    // Original entry from list (this is the "oldEntry" key!)
    val selectedEntry = remember(accounts, selectedUsername) {
        selectedUsername?.let { username ->
            accounts.firstOrNull { it.username == username }
        }
    }

    // Safe copy for UI (never null/blank)
    val safeEntry = remember(selectedEntry) {
        selectedEntry?.copy(
            name = selectedEntry.name.ifBlank { "" },
            url = selectedEntry.url.ifBlank { "" },
            username = selectedEntry.username.ifBlank { "" },
            password = selectedEntry.password.ifBlank { "" },
            note = selectedEntry.note ?: ""
        ) ?: PasswordEntry(
            name = "",
            url = "",
            username = "",
            password = "",
            note = ""
        )
    }

    // Editable fields
    var username by remember(safeEntry) { mutableStateOf(safeEntry.username) }
    var password by remember(safeEntry) { mutableStateOf(safeEntry.password) }
    var note by remember(safeEntry) { mutableStateOf(safeEntry.note ?: "") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    if (showDeleteDialog && selectedEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Password") },
            text = { Text("Are you sure you want to delete this password? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        // Delete the currently selected ORIGINAL entry
                        onDelete(selectedEntry)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit: $domain") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = selectedEntry != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Password",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Account selector (only when multiple exist)
            if (usernames.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = accountMenuExpanded,
                    onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedUsername ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        placeholder = { Text("Select an account") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded)
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        usernames.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    accountMenuExpanded = false
                                    selectedUsername = u
                                }
                            )
                        }
                    }
                }
            } else {
                Text("Account: ${safeEntry.username}", style = MaterialTheme.typography.bodyMedium)
            }

            if (usernames.size > 1 && selectedEntry == null) {
                Text(
                    text = "Please select an account to edit or delete.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                enabled = selectedEntry != null
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                enabled = selectedEntry != null
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                minLines = 3,
                enabled = selectedEntry != null
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val updatedEntry = safeEntry.copy(
                        username = username.trim(),
                        password = password,
                        note = note.trim().ifBlank { null }
                    )

                    // IMPORTANT: pass oldEntry (selectedEntry) + updatedEntry
                    selectedEntry?.let { oldEntry ->
                        onSave(oldEntry, updatedEntry)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                enabled = selectedEntry != null
            ) {
                Text("Save Changes")
            }
        }
    }
}
