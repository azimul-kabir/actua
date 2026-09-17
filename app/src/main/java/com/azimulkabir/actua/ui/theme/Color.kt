package com.azimulkabir.actua.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Actua's default color scheme is derived from the app's own launcher icon
// (a vivid indigo/violet, #4B18D1 — see actua_launcher_background.xml) so the
// in-app palette and the icon on a user's home screen read as one brand.
// This is the default; users can opt into Material You (wallpaper-derived
// dynamic color) instead via Settings > Display.

val ActuaBrandSeed = Color(0xFF4B18D1)

private val VioletPrimary = ActuaBrandSeed
private val VioletOnPrimary = Color(0xFFFFFFFF)
private val VioletPrimaryContainer = Color(0xFFE4DBFF)
private val VioletOnPrimaryContainer = Color(0xFF1B0060)

private val SlateSecondary = Color(0xFF5F5C71)
private val SlateOnSecondary = Color(0xFFFFFFFF)
private val SlateSecondaryContainer = Color(0xFFE4DFFF)
private val SlateOnSecondaryContainer = Color(0xFF1A1830)

private val OrchidTertiary = Color(0xFF7C5295)
private val OrchidOnTertiary = Color(0xFFFFFFFF)
private val OrchidTertiaryContainer = Color(0xFFF6D8FF)
private val OrchidOnTertiaryContainer = Color(0xFF300442)

private val ErrorRed = Color(0xFFBA1A1A)
private val OnErrorRed = Color(0xFFFFFFFF)
private val ErrorContainerRed = Color(0xFFFFDAD6)
private val OnErrorContainerRed = Color(0xFF410002)

private val VioletPrimaryDark = Color(0xFFC6B7FF)
private val VioletOnPrimaryDark = Color(0xFF34008A)
private val VioletPrimaryContainerDark = Color(0xFF34059B)
private val VioletOnPrimaryContainerDark = Color(0xFFE4DBFF)

private val SlateSecondaryDark = Color(0xFFC8C3DC)
private val SlateOnSecondaryDark = Color(0xFF302E41)
private val SlateSecondaryContainerDark = Color(0xFF474459)
private val SlateOnSecondaryContainerDark = Color(0xFFE4DFFF)

private val OrchidTertiaryDark = Color(0xFFE9B9FF)
private val OrchidOnTertiaryDark = Color(0xFF48195F)
private val OrchidTertiaryContainerDark = Color(0xFF613177)
private val OrchidOnTertiaryContainerDark = Color(0xFFF6D8FF)

private val ErrorRedDark = Color(0xFFFFB4AB)
private val OnErrorRedDark = Color(0xFF690005)
private val ErrorContainerRedDark = Color(0xFF93000A)
private val OnErrorContainerRedDark = Color(0xFFFFDAD6)

val ActuaLightColorScheme = lightColorScheme(
    primary = VioletPrimary,
    onPrimary = VioletOnPrimary,
    primaryContainer = VioletPrimaryContainer,
    onPrimaryContainer = VioletOnPrimaryContainer,
    inversePrimary = Color(0xFFC6B7FF),
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = OrchidTertiary,
    onTertiary = OrchidOnTertiary,
    tertiaryContainer = OrchidTertiaryContainer,
    onTertiaryContainer = OrchidOnTertiaryContainer,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC9C5D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF312F35),
    inverseOnSurface = Color(0xFFF4EFF4),
    surfaceDim = Color(0xFFDED8E0),
    surfaceBright = Color(0xFFFFFBFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2FA),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E1E9),
)

val ActuaDarkColorScheme = darkColorScheme(
    primary = VioletPrimaryDark,
    onPrimary = VioletOnPrimaryDark,
    primaryContainer = VioletPrimaryContainerDark,
    onPrimaryContainer = VioletOnPrimaryContainerDark,
    inversePrimary = VioletPrimary,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = OrchidTertiaryDark,
    onTertiary = OrchidOnTertiaryDark,
    tertiaryContainer = OrchidTertiaryContainerDark,
    onTertiaryContainer = OrchidOnTertiaryContainerDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorContainerRedDark,
    onErrorContainer = OnErrorContainerRedDark,
    background = Color(0xFF141318),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141318),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF312F35),
    surfaceDim = Color(0xFF141318),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
)

// Material 3 has no built-in "success"/"warning" roles, but Actua needs both for
// paid/cleared and due-soon status across Accounts, Transactions, Schedules and
// Bills. These live here rather than as scattered hex literals per screen, and
// pick a theme-appropriate tone from the current background rather than a
// single hardcoded value that only reads correctly in light mode.
private val SuccessGreenLight = Color(0xFF2E7D32)
private val SuccessGreenDark = Color(0xFF81C995)
private val WarningAmberLight = Color(0xFFF57C00)
private val WarningAmberDark = Color(0xFFFFB74D)

val ColorScheme.success: Color
    @Composable get() = if (background.luminance() > 0.5f) SuccessGreenLight else SuccessGreenDark

val ColorScheme.warning: Color
    @Composable get() = if (background.luminance() > 0.5f) WarningAmberLight else WarningAmberDark
