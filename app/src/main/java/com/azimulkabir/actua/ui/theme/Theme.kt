package com.azimulkabir.actua.ui.theme

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun ActuaTheme(
    appearance: String = "System",
    // Material You (wallpaper-derived) dynamic color, opt-in and only available on Android 12+.
    // Actua's own brand palette (ActuaLightColorScheme/ActuaDarkColorScheme) is the default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearance) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> ActuaDarkColorScheme
        else -> ActuaLightColorScheme
    }

    // enableEdgeToEdge's default style reads dark mode from the system configuration, so an
    // explicit Light/Dark appearance would leave unreadable bar icons. Re-apply it whenever the
    // resolved theme changes so the bars follow the app without recreating the activity.
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, darkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                detectDarkMode = { darkTheme },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                NavigationBarLightScrim,
                NavigationBarDarkScrim,
                detectDarkMode = { darkTheme },
            ),
        )
        onDispose {}
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ActuaShapes,
        content = content
    )
}

// The scrims enableEdgeToEdge() uses by default for three-button navigation before Android 10.
private val NavigationBarLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavigationBarDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
