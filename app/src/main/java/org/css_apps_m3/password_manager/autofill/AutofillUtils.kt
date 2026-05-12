package org.css_apps_m3.password_manager.autofill

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object AutofillUtils {
    fun isAutofillSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun isOurServiceDefault(context: Context): Boolean {
        if (!isAutofillSupported()) return false
        val serviceName = Settings.Secure.getString(context.contentResolver, "autofill_service") ?: return false
        val expected = ComponentName(context, PasswordAutofillService::class.java).flattenToString()
        return serviceName == expected
    }

    fun openAutofillSettings(context: Context) {
        if (!isAutofillSupported()) return
        val component = ComponentName(context, PasswordAutofillService::class.java)
        val intents = listOf(
            Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE").apply {
                putExtra("android.provider.extra.AUTOFILL_SERVICE_COMPONENT_NAME", component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.AUTOFILL_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.SYNC_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            val worked = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (worked) return
        }
    }
}
