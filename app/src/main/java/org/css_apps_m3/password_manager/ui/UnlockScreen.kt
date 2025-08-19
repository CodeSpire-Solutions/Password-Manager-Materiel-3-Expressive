package org.css_apps_m3.password_manager.ui

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricPrompt
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
import java.util.concurrent.Executor

@Composable
fun UnlockScreen(
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

    //Log.d("VaultDebug", "UnlockScreen -> storedPassword: $storedPassword")
    //Log.d("VaultDebug", "UnlockScreen -> biometricsEnabled: $biometricsEnabled")
    //Log.d("VaultDebug", "UnlockScreen -> context: $context")
    //Log.d("VaultDebug", "UnlockScreen -> is FragmentActivity: ${context is FragmentActivity}")

    // When biometric authentication is enabled -> start automatically
    if (biometricsEnabled) {
        LaunchedEffect(Unit) {
            if (context is FragmentActivity) {
                triggerBiometricAuth(context) {
                    onUnlock()
                }
            } else {
                //Log.e("VaultDebug", "UnlockScreen -> Context is no FragmentActivity, Biometric Auth not possible")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Passwords", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master Passwort") },
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
            Text("Unlock")
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
            Text("Unlock with Biometric")
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
