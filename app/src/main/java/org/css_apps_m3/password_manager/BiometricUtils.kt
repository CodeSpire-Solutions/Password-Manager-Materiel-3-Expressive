package org.css_apps_m3.password_manager

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricUtils {
    fun isBiometricAvailable(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }
}
