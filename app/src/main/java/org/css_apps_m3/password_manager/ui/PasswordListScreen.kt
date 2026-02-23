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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.css_apps_m3.password_manager.AddFab
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(
    navController: NavController,
    onClick: (String, List<PasswordEntry>) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { PasswordRepository(context) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var passwords by remember { mutableStateOf(repo.loadPasswords()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                passwords = repo.loadPasswords()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load passwords automatically when the screen is opened
    LaunchedEffect(currentRoute) {
        if (currentRoute == "list") {
            passwords = repo.loadPasswords()
        }
    }

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val grouped = passwords
        .filter { it.url.isNotBlank() && !it.url.startsWith("android://") }
        .groupBy { extractDomain(it.url) }                  // Domain as Key
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)         // Alphabetic

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
        floatingActionButton = { AddFab(navController) }
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
                shape = RoundedCornerShape(50), // Round Search field
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

fun extractDomain(raw: String): String {
    val url = raw.trim()

    return try {
        val normalized =
            if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val host = android.net.Uri.parse(normalized).host ?: url
        host.removePrefix("www.").trim()
    } catch (_: Exception) {
        url.substringAfterLast("@")
            .substringAfter("://")
            .substringBefore("/")
            .removePrefix("www.")
            .trim()
    }
}
fun Int.toColor(): Color = Color(this)