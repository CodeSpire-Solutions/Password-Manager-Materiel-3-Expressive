package org.css_apps_m3.password_manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.*
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.ui.PasswordDetailScreen
import org.css_apps_m3.password_manager.ui.PasswordListScreen
import org.css_apps_m3.password_manager.ui.UnlockScreen
import org.css_apps_m3.password_manager.ui.theme.PasswordViewerTheme
import org.css_apps_m3.password_manager.util.CsvReader

@SuppressLint("RestrictedApi")
class MainActivity : FragmentActivity() {
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
            PasswordViewerTheme {
                var unlocked by remember { mutableStateOf(false) }
                var passwords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }

                var pendingUnlock by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri: Uri? ->
                        uri?.let {
                            val list = CsvReader.readPasswordsFromUri(this, it)
                            repo.saveLocal(list)
                            passwords = list
                            unlocked = true
                        }
                        pendingUnlock = false
                    }
                )

                val navController = rememberNavController()

                if (!unlocked) {
                    UnlockScreen {
                        if (repo.hasLocalData()) {
                            passwords = repo.loadPasswords()
                            unlocked = true
                        } else {
                            pendingUnlock = true
                            launcher.launch(arrayOf("text/*", "application/csv"))
                        }
                    }
                } else {
                    NavHost(navController, startDestination = "list") {
                        composable("list") {
                            PasswordListScreen(passwords) { domain, accounts ->
                                navController.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("accounts", accounts)
                                navController.navigate("detail/$domain")
                            }

                        }
                        composable("detail/{domain}") { backStackEntry ->
                            val domain = backStackEntry.arguments?.getString("domain") ?: ""
                            val accounts = navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.get<List<PasswordEntry>>("accounts")
                                ?: emptyList()

                            PasswordDetailScreen(
                                domain = domain,
                                accounts = accounts,
                                onBack = { navController.popBackStack() }
                            )
                        }

                    }
                }
            }
        }
    }
}