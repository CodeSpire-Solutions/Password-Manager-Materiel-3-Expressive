package org.css_apps_m3.password_manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Default fallback colors
private val DarkColors = darkColorScheme()
private val LightColors = lightColorScheme()

@Composable
fun PasswordViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicTheme: Boolean = false,
    customAccent: Int? = null,
    cornerRadius: Float = 12f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Decide color scheme
    val colorScheme = when {
        // Dynamic Material You (Android 12+)
        dynamicTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Custom accent applied
        customAccent != null -> {
            val accent = Color(customAccent)
            if (darkTheme) {
                darkColorScheme(
                    primary = accent,
                    secondary = accent,
                    tertiary = accent
                )
            } else {
                lightColorScheme(
                    primary = accent,
                    secondary = accent,
                    tertiary = accent
                )
            }
        }
        // Default system theme
        else -> if (darkTheme) DarkColors else LightColors
    }

    // Custom shapes based on corner radius
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp)
    )

    // Status/navigation bar theming
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = shapes,
        content = content
    )
}
