package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavController
import org.css_apps_m3.password_manager.model.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(
    navController: NavController,
    onClick: (String, List<PasswordEntry>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { org.css_apps_m3.password_manager.data.PasswordRepository(context) }

    var passwords by remember { mutableStateOf(emptyList<PasswordEntry>()) }

    // Load passwords automatically when the screen is opened
    LaunchedEffect(Unit) {
        passwords = repo.loadPasswords()
    }

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val grouped = passwords
        .map { it.copy(url = extractDomain(it.url)) }
        .filter { !it.url.startsWith("android://") }
        .groupBy { it.url }

    val filtered = grouped.filter { (domain, entries) ->
        domain.contains(searchQuery.text, ignoreCase = true) ||
                entries.any { it.username.contains(searchQuery.text, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passwords") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("add") }) {
                Icon(Icons.Default.Add, contentDescription = "Add Password")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true,
                shape = RoundedCornerShape(50), // Runde Search-Leiste
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered.toList()) { (domain, accounts) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { onClick(domain, accounts) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${accounts.size} account${if (accounts.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

fun extractDomain(url: String): String {
    return try {
        val host = java.net.URI(url).host ?: url
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (e: Exception) {
        url.substringAfterLast("@").substringAfter("://")
    }
}
