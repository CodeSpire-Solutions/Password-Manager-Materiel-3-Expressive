package org.css_apps_m3.password_manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.ui.*
import org.css_apps_m3.password_manager.util.CsvReader

@SuppressLint("RestrictedApi")
class MainActivity : FragmentActivity() {

    companion object {
        // Flag to prevent the app from locking when opening the file picker
        var ignoreNextPause = false
    }

    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isSetupDone = prefs.getBoolean("setup_done", false)

        if (!isSetupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        val repo = PasswordRepository(this)

        setContent {
            AppThemed {
                val navController = rememberNavController()

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        val list = CsvReader.readPasswordsFromUri(this, it)
                        if (!list.isNullOrEmpty()) {
                            repo.saveLocal(list)
                            unlocked = true
                        }
                    }
                }

                if (!unlocked) {
                    UnlockScreen {
                        if (repo.hasLocalData()) {
                            // Just ensure file can be read
                            repo.loadPasswords()
                            unlocked = true
                        } else {
                            importLauncher.launch(arrayOf("text/*", "application/csv"))
                        }
                    }
                } else {
                    NavHost(navController = navController, startDestination = "list") {

                        composable(
                            route = "list",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) {
                            PasswordListScreen(
                                navController = navController,
                                onClick = { domain, _ ->
                                    navController.navigate("detail/$domain")
                                }
                            )
                        }

                        composable(
                            route = "detail/{domain}",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) { backStackEntry ->
                            val domainArg = backStackEntry.arguments?.getString("domain") ?: ""

                            // Always read fresh from repo (single source of truth)
                            val accounts = remember(domainArg) {
                                repo.loadPasswords().filter { extractDomainStable(it.url) == domainArg }
                            }

                            // Never allow an "empty" detail to render a blank screen
                            if (accounts.isEmpty()) {
                                // If nothing exists for this domain (e.g., after delete), go back to list.
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            } else {
                                PasswordDetailScreen(
                                    domain = domainArg,
                                    accounts = accounts,
                                    onBack = { navController.popBackStack() },
                                    onEdit = { selectedEntry ->
                                        navController.navigate("edit/${domainArg}/${Uri.encode(selectedEntry.username)}")
                                    }
                                )
                            }
                        }

                        composable(
                            route = "add",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) {
                            AddPasswordScreen(
                                navController = navController,
                                repository = repo
                            )
                        }

                        composable(
                            route = "settings",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "edit/{domain}/{username}",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) { backStackEntry ->
                            val domainArg = backStackEntry.arguments?.getString("domain") ?: ""
                            val usernameArg = backStackEntry.arguments?.getString("username") ?: ""
                            val decodedUsername = Uri.decode(usernameArg)

                            val accounts = repo.loadPasswords().filter { extractDomainStable(it.url) == domainArg }

                            if (accounts.isEmpty()) {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            } else {
                                EditPasswordScreen(
                                    domain = domainArg,
                                    accounts = accounts,
                                    initiallySelectedUsername = decodedUsername,

                                    // Now oldEntry + updatedEntry
                                    onSave = { oldEntry, updated ->
                                        repo.updatePasswordWithOldKey(oldEntry.url, oldEntry.username, updated)

                                        navController.navigate("detail/$domainArg") {
                                            popUpTo("detail/$domainArg") { inclusive = true }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    },
                                    onDelete = { entryToDelete ->
                                        repo.deletePassword(entryToDelete)

                                        val remaining = repo.loadPasswords().any { extractDomainStable(it.url) == domainArg }
                                        if (!remaining) {
                                            navController.navigate("list") {
                                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        } else {
                                            navController.navigate("detail/$domainArg") {
                                                popUpTo("detail/$domainArg") { inclusive = true }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                    },
                                    onCancel = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (ignoreNextPause) {
            // Reset the flag but do NOT lock the app
            ignoreNextPause = false
        } else {
            // Normal behavior: lock the app when it goes to the background
            unlocked = false
        }
    }
}

/**
 * Robust domain extraction: handles raw domains, full URLs, whitespace and CRLF from CSV.
 */
private fun extractDomainStable(raw: String): String {
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
