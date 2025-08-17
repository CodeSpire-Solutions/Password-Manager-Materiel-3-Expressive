package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(passwords: List<PasswordEntry>, onClick: (String, List<PasswordEntry>) -> Unit) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    // Gruppiere Passwörter nach Domain
    val grouped = passwords
        .map { it.copy(url = extractDomain(it.url)) } // nur Domain behalten
        .filter { !it.url.startsWith("android://") } // Android-URIs rausfiltern
        .groupBy { it.url }

    // Filter
    val filtered = grouped.filter { (domain, entries) ->
        domain.contains(searchQuery.text, ignoreCase = true) ||
                entries.any { it.username.contains(searchQuery.text, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passwords") }
            )
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
        // Falls URI ungültig ist, gib einfach zurück
        url.substringAfterLast("@").substringAfter("://")
    }
}
