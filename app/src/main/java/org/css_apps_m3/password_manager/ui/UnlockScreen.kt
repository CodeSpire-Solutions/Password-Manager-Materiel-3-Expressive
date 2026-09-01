package org.css_apps_m3.password_manager.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.css_apps_m3.password_manager.AppThemed
import java.security.KeyStore
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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
                triggerBiometricAuth(context, onError = { error = it }) {
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp).size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text("Welcome back", style = MaterialTheme.typography.headlineLarge)
                Text("Unlock your private vault.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = {
                        if (storedPassword != null && password == storedPassword) onUnlock() else error = "Incorrect password"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Unlock vault") }
                if (biometricsEnabled) {
                    BiometricButton(context, onError = { error = it }) { onUnlock() }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private const val BIOMETRIC_KEY_ALIAS = "password_manager_biometric_unlock_key"
private const val BIOMETRIC_TRANSFORMATION =
    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"

private fun getOrCreateBiometricSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existingKey = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
    if (existingKey != null) return existingKey

    val keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    )
    val keySpec = KeyGenParameterSpec.Builder(
        BIOMETRIC_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .build()

    keyGenerator.init(keySpec)
    return keyGenerator.generateKey()
}

private fun buildBiometricCipher(): Cipher {
    return Cipher.getInstance(BIOMETRIC_TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, getOrCreateBiometricSecretKey())
    }
}

private fun recreateBiometricKey() {
    KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
        deleteEntry(BIOMETRIC_KEY_ALIAS)
    }
    getOrCreateBiometricSecretKey()
}

private fun createBiometricPrompt(
    activity: FragmentActivity,
    onError: (String) -> Unit,
    onUnlock: () -> Unit
): BiometricPrompt {
    val executor: Executor = ContextCompat.getMainExecutor(activity)

    return BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onError("Biometric authentication failed")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher == null) {
                    onError("Biometric authentication could not verify secure unlock")
                    return
                }

                try {
                    authenticatedCipher.doFinal(ByteArray(0))
                    onUnlock()
                } catch (_: Exception) {
                    onError("Biometric authentication could not verify secure unlock")
                }
            }
        }
    )
}

private fun biometricPromptInfo(): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock with Biometric")
        .setSubtitle("Use fingerprint or face recognition to unlock")
        .setNegativeButtonText("Abort")
        .build()
}

private fun authenticateWithBiometricCrypto(
    prompt: BiometricPrompt,
    promptInfo: BiometricPrompt.PromptInfo,
    onError: (String) -> Unit
) {
    try {
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(buildBiometricCipher()))
    } catch (_: KeyPermanentlyInvalidatedException) {
        runCatching { recreateBiometricKey() }
        onError("Biometric settings changed. Try biometric unlock again.")
    } catch (_: Exception) {
        onError("Biometric authentication is not available")
    }
}

@Composable
fun BiometricButton(context: Context, onError: (String) -> Unit, onUnlock: () -> Unit) {
    if (context is FragmentActivity) {
        val biometricPrompt = remember(context) {
            createBiometricPrompt(context, onError, onUnlock)
        }
        val promptInfo = remember { biometricPromptInfo() }

        Button(
            onClick = {
                authenticateWithBiometricCrypto(biometricPrompt, promptInfo, onError)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Unlock with Biometric")
        }
    } else {
        //Log.e("VaultDebug", "BiometricButton -> No FragmentActivity Context!")
        Text("Biometric Authentication not available", color = MaterialTheme.colorScheme.error)
    }
}

// Help function for auto start
fun triggerBiometricAuth(context: FragmentActivity, onError: (String) -> Unit, onUnlock: () -> Unit) {
    val biometricPrompt = createBiometricPrompt(context, onError, onUnlock)
    authenticateWithBiometricCrypto(biometricPrompt, biometricPromptInfo(), onError)
}
