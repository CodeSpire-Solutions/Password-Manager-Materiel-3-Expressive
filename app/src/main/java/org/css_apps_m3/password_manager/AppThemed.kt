package org.css_apps_m3.password_manager

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import org.css_apps_m3.password_manager.ui.theme.PasswordViewerTheme

// A simple global object to hold preferences as reactive StateFlows
object AppPrefs {
    val darkMode = MutableStateFlow(true)
    val dynamicTheme = MutableStateFlow(true)
    val customAccent = MutableStateFlow(0xFF6200EE.toInt())
    val cornerRadius = MutableStateFlow(12f)
    val haptics = MutableStateFlow(true)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        darkMode.value = prefs.getBoolean("dark_mode", true)
        dynamicTheme.value = prefs.getBoolean("dynamic_theme", true)
        customAccent.value = prefs.getInt("custom_accent", 0xFF6200EE.toInt())
        cornerRadius.value = prefs.getFloat("corner_radius", 12f)
        haptics.value = prefs.getBoolean("haptics", true)
    }

    fun saveDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dark_mode", enabled)
            .apply()
        darkMode.value = enabled
    }
}

@Composable
fun AppThemed(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // Load prefs once when composable enters composition
    LaunchedEffect(Unit) {
        AppPrefs.load(context)
    }

    val darkMode by AppPrefs.darkMode.collectAsState()
    val dynamicTheme by AppPrefs.dynamicTheme.collectAsState()
    val customAccent by AppPrefs.customAccent.collectAsState()
    val cornerRadius by AppPrefs.cornerRadius.collectAsState()
    val haptics by AppPrefs.haptics.collectAsState()

    PasswordViewerTheme(
        darkTheme = darkMode,
        dynamicTheme = dynamicTheme,
        customAccent = customAccent,
        cornerRadius = cornerRadius
    ) {
        if (haptics) {
            CompositionLocalProvider(
                LocalHapticFeedback provides LocalHapticFeedback.current
            ) {
                content()
            }
        } else {
            content()
        }
    }
}
