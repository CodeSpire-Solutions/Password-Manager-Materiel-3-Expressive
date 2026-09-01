package org.css_apps_m3.password_manager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.data.SqlSyncManager
import org.css_apps_m3.password_manager.model.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(navController: NavController, onClick: (String, List<PasswordEntry>) -> Unit) {
    val context = LocalContext.current
    val repo = remember { PasswordRepository(context) }
    val syncManager = remember { SqlSyncManager(context) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var passwords by remember { mutableStateOf(emptyList<PasswordEntry>()) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { passwords = withContext(Dispatchers.IO) { repo.loadPasswords() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == "list") {
            passwords = withContext(Dispatchers.IO) { repo.loadPasswords() }
            while (isActive) {
                val cfg = SqlSyncManager.loadConfig(context)
                if (cfg.autoSync) {
                    runCatching { withContext(Dispatchers.IO) { syncManager.syncFromCloud(cfg).first } }
                        .getOrNull()?.let { cloudEntries ->
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

    val grouped by remember(passwords) {
        derivedStateOf {
            passwords.filter { it.url.isNotBlank() && !it.url.startsWith("android://") }
                .groupBy { extractDomain(it.url) }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        }
    }
    val filtered by remember(grouped, searchQuery) {
        derivedStateOf {
            grouped.filter { (domain, entries) ->
                domain.contains(searchQuery, ignoreCase = true) || entries.any { it.username.contains(searchQuery, true) }
            }.toList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault", style = MaterialTheme.typography.titleLarge) },
                actions = { IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add") },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Default.Add, "Add password") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 12.dp, 20.dp, 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ExpressiveHero(
                    eyebrow = "Your private space",
                    title = "Passwords, protected.",
                    supportingText = "${passwords.size} saved account${if (passwords.size == 1) "" else "s"}"
                )
            }
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.cardElevation(0.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search your vault") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(14.dp, 8.dp, 14.dp, 16.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("${filtered.size} services", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (filtered.isEmpty()) item { ExpressiveEmptyState(searchQuery.isNotBlank()) }
            items(filtered, key = { (domain, _) -> domain }) { (domain, accounts) ->
                AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onClick(domain, accounts) },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(Icons.Default.Key, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(domain, style = MaterialTheme.typography.titleMedium)
                                Text("${accounts.size} account${if (accounts.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveEmptyState(hasSearch: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(if (hasSearch) "No matching passwords" else "Your vault is ready", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(if (hasSearch) "Try a different service or username." else "Add your first account to keep credentials close and protected.", color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

fun extractDomain(raw: String): String {
    val url = raw.trim()
    return try {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        (android.net.Uri.parse(normalized).host ?: url).removePrefix("www.").trim()
    } catch (_: Exception) {
        url.substringAfterLast("@").substringAfter("://").substringBefore("/").removePrefix("www.").trim()
    }
}
