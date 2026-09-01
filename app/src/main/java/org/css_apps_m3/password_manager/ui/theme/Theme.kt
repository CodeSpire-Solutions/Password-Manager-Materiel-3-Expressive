package org.css_apps_m3.password_manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Default fallback colors
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7F3D0),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFC3FFDE),
    secondary = Color(0xFFB9CCBE),
    secondaryContainer = Color(0xFF253B2F),
    tertiary = Color(0xFFFFD9A0),
    tertiaryContainer = Color(0xFF614000),
    background = Color(0xFF101512),
    surface = Color(0xFF171D19),
    surfaceVariant = Color(0xFF39443D)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF8C7),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4E6356),
    secondaryContainer = Color(0xFFD1E8D8),
    tertiary = Color(0xFF765A00),
    tertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFFF7FBF5),
    surface = Color(0xFFF7FBF5),
    surfaceVariant = Color(0xFFDDE7DE)
)

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
        extraSmall = RoundedCornerShape((cornerRadius * 0.65f).dp),
        small = RoundedCornerShape(cornerRadius.dp),
        medium = RoundedCornerShape((cornerRadius + 8f).dp),
        large = RoundedCornerShape((cornerRadius + 16f).dp),
        extraLarge = RoundedCornerShape((cornerRadius + 28f).dp)
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
