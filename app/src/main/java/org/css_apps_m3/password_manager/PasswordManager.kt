package org.css_apps_m3.password_manager

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PasswordManager(private val context: Context) : ViewModel() {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "password_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveMasterPassword(password: String, biometricEnabled: Boolean) {
        prefs.edit().putString("master_password", password).apply()
        prefs.edit().putBoolean("biometric_enabled", biometricEnabled).apply()
    }

    fun importCsv(uri: Uri) {
        // CSV Parsing Logik hier implementieren
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("biometric_enabled", false)
}
