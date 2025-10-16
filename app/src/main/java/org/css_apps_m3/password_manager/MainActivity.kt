package org.css_apps_m3.password_manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isSetupDone = prefs.getBoolean("setup_done", false)

        if (!isSetupDone) {
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        val repo = PasswordRepository(this)

        setContent {
            AppThemed {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri: Uri? ->
                        uri?.let {
                            val list = CsvReader.readPasswordsFromUri(this, it)
                            repo.saveLocal(list)
                            unlocked = true
                        }
                    }
                )

                val navController = rememberNavController()

                if (!unlocked) {
                    UnlockScreen {
                        if (repo.hasLocalData()) {
                            repo.loadPasswords()
                            unlocked = true
                        } else {
                            launcher.launch(arrayOf("text/*", "application/csv"))
                        }

                    }
                } else {
                    NavHost(navController, startDestination = "list") {
                        composable(
                            "list",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) {
                            PasswordListScreen(
                                navController = navController,
                                onClick = { domain, accounts ->
                                    navController.currentBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("accounts", accounts)
                                    navController.navigate("detail/$domain")
                                }
                            )
                        }
                        composable(
                            "detail/{domain}",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) { backStackEntry ->
                            val domain = backStackEntry.arguments?.getString("domain") ?: ""
                            val accounts = navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.get<List<PasswordEntry>>("accounts")
                                ?: emptyList()

                            PasswordDetailScreen(
                                domain = domain,
                                accounts = accounts,
                                onBack = { navController.popBackStack() },
                                onEdit = { selectedAccounts ->
                                    navController.currentBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("edit_accounts", selectedAccounts)
                                    navController.navigate("edit/${domain}")
                                }
                            )
                        }
                        composable(
                            "add",
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
                            "settings",
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
                            "edit/{domain}",
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
                        ) { backStackEntry ->
                            val domain = backStackEntry.arguments?.getString("domain")
                            val entry = repo.loadPasswords().find { it.url == domain } // single entry

                            entry?.let {
                                EditPasswordScreen(
                                    entry = it, // pass the single entry
                                    onSave = { updated ->
                                        repo.updatePassword(updated) // update the entry in repository
                                        navController.popBackStack()
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
        // When the app goes into the background → Lock
        unlocked = false
    }
}
