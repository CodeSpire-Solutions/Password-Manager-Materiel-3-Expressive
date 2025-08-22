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
import androidx.navigation.NavController
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(
    passwords: List<PasswordEntry>,
    onClick: (String, List<PasswordEntry>) -> Unit,
    navController: NavController
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    // Group passwords by domain
    val grouped = passwords
        .map { it.copy(url = extractDomain(it.url)) } // only keep domain
        .filter { !it.url.startsWith("android://") } // Filter out Android URIs
        .groupBy { it.url }

    // Filter
    val filtered = grouped.filter { (domain, entries) ->
        domain.contains(searchQuery.text, ignoreCase = true) ||
                entries.any { it.username.contains(searchQuery.text, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passwords") },
                actions = {
                    /*IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }

                     */
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add") }
            ) {
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
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
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

/**
 * Extracts the top-level domain (like "google.com") from a given URL
 */
fun extractDomain(url: String): String {
    return try {
        val host = URI(url).host ?: url
        if (host.startsWith("www.")) host.substring(4) else host
    } catch (e: Exception) {
        // If URI is invalid, simply return
        url.substringAfterLast("@").substringAfter("://")
    }
}