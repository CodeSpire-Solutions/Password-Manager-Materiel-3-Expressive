package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import org.css_apps_m3.password_manager.data.SqlSyncManager
import org.css_apps_m3.password_manager.model.PasswordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(
    navController: NavController,
    onClick: (String, List<PasswordEntry>) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { PasswordRepository(context) }
    val syncManager = remember { SqlSyncManager(context) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var passwords by remember { mutableStateOf(emptyList<PasswordEntry>()) }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    passwords = withContext(Dispatchers.IO) { repo.loadPasswords() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load passwords automatically when the screen is opened
    LaunchedEffect(currentRoute) {
        if (currentRoute == "list") {
            passwords = withContext(Dispatchers.IO) { repo.loadPasswords() }
            while (isActive) {
                val cfg = SqlSyncManager.loadConfig(context)
                if (cfg.autoSync) {
                    runCatching { withContext(Dispatchers.IO) { syncManager.syncFromCloud(cfg).first } }
                        .getOrNull()
                        ?.let { cloudEntries ->
                            if (cloudEntries != passwords) {
                                withContext(Dispatchers.IO) { repo.saveLocal(cloudEntries) }
                                passwords = cloudEntries
                            }
                        }
                }
                delay(60_000)
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val grouped by remember(passwords) {
        derivedStateOf {
            passwords
                .filter { it.url.isNotBlank() && !it.url.startsWith("android://") }
                .groupBy { extractDomain(it.url) }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        }
    }

    val filtered by remember(grouped, searchQuery) {
        derivedStateOf {
            grouped.filter { (domain, entries) ->
                domain.contains(searchQuery, ignoreCase = true) ||
                    entries.any { it.username.contains(searchQuery, ignoreCase = true) }
            }.toList()
        }
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(
                    items = filtered,
                    key = { (domain, _) -> domain }
                ) { (domain, accounts) ->
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
