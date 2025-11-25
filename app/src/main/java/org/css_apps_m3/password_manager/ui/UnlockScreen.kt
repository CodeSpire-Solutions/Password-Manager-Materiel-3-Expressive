package org.css_apps_m3.password_manager.ui

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.AppThemed
import org.css_apps_m3.password_manager.ui.theme.PasswordViewerTheme
import org.css_apps_m3.password_manager.ui.ui.theme.PasswordManagerTheme
import java.util.concurrent.Executor

@Composable
fun UnlockScreen(onUnlock: () -> Unit) {
    AppThemed {
        UnlockContent(onUnlock = onUnlock)
    }
}

@Composable
private fun UnlockContent(
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Setup EncryptedSharedPreferences
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "vault_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val storedPassword = encryptedPrefs.getString("master_password", null)
    val biometricsEnabled = encryptedPrefs.getBoolean("biometrics_enabled", false)

    if (biometricsEnabled) {
        LaunchedEffect(Unit) {
            if (context is FragmentActivity) {
                triggerBiometricAuth(context) {
                    onUnlock()
                }
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // <-- Add this
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Passwords",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (storedPassword != null && password == storedPassword) {
                    onUnlock()
                } else {
                    error = "False Password"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.background)
        }

        Spacer(Modifier.height(16.dp))

        if (biometricsEnabled) {
            BiometricButton(context) {
                onUnlock()
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
    }

@Composable
fun BiometricButton(context: Context, onUnlock: () -> Unit) {
    if (context is FragmentActivity) {
        val executor: Executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            context,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlock()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock with Biometric")
            .setSubtitle("Use fingerprint or face recognition to unlock")
            .setNegativeButtonText("Abort")
            .build()

        Button(
            onClick = { biometricPrompt.authenticate(promptInfo) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock with Biometric",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.background)
        }
    } else {
        //Log.e("VaultDebug", "BiometricButton -> No FragmentActivity Context!")
        Text("Biometric Authentication not available", color = MaterialTheme.colorScheme.error)
    }
}

// Help function for auto start
fun triggerBiometricAuth(context: FragmentActivity, onUnlock: () -> Unit) {
    val executor: Executor = ContextCompat.getMainExecutor(context)

    val biometricPrompt = BiometricPrompt(
        context,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlock()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock with Biometric")
        .setSubtitle("Use fingerprint or face recognition to unlock")
        .setNegativeButtonText("Abort")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
