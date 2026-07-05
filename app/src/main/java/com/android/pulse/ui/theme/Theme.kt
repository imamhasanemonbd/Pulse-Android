package com.android.pulse.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB2BE),
    onPrimary = Color(0xFF670023),
    primaryContainer = Color(0xFF910034),
    onPrimaryContainer = Color(0xFFFFDADE),
    secondary = Color(0xFFE5BDC1),
    onSecondary = Color(0xFF43292D),
    secondaryContainer = Color(0xFF5D3F43),
    onSecondaryContainer = Color(0xFFFFDADE),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290B),
    tertiaryContainer = Color(0xFF623F20),
    onTertiaryContainer = Color(0xFFFFDDB6),
    background = Color(0xFF201A1B),
    onBackground = Color(0xFFECE0E0),
    surface = Color(0xFF201A1B),
    onSurface = Color(0xFFECE0E0),
    surfaceVariant = Color(0xFF524344),
    onSurfaceVariant = Color(0xFFD7C1C2),
    outline = Color(0xFF9F8C8D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB90045),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADE),
    onPrimaryContainer = Color(0xFF400014),
    secondary = Color(0xFF76565A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADE),
    onSecondaryContainer = Color(0xFF2C1518),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2D1600),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF3DDDE),
    onSurfaceVariant = Color(0xFF524344),
    outline = Color(0xFF847374)
)

@Composable
fun PulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PulseShapes,
        content = content
    )
}
