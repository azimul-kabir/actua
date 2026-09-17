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
//
// The in-app tones below are a brighter, more saturated take on that seed —
// higher chroma primary/secondary/tertiary and tinted (rather than flat gray)
// surfaces — so the UI reads as vivid rather than the muted default Material
// tonal palette Compose would otherwise generate from the seed.

val ActuaBrandSeed = Color(0xFF4B18D1)

private val VioletPrimary = Color(0xFF6E11FF)
private val VioletOnPrimary = Color(0xFFFFFFFF)
private val VioletPrimaryContainer = Color(0xFFE0CCFF)
private val VioletOnPrimaryContainer = Color(0xFF2E0090)

private val SlateSecondary = Color(0xFF6C5CE0)
private val SlateOnSecondary = Color(0xFFFFFFFF)
private val SlateSecondaryContainer = Color(0xFFE3DBFF)
private val SlateOnSecondaryContainer = Color(0xFF1D1147)

private val OrchidTertiary = Color(0xFFC026D3)
private val OrchidOnTertiary = Color(0xFFFFFFFF)
private val OrchidTertiaryContainer = Color(0xFFFAD1FF)
private val OrchidOnTertiaryContainer = Color(0xFF4A0060)

private val ErrorRed = Color(0xFFE0201A)
private val OnErrorRed = Color(0xFFFFFFFF)
private val ErrorContainerRed = Color(0xFFFFD9D6)
private val OnErrorContainerRed = Color(0xFF410002)

private val VioletPrimaryDark = Color(0xFFD4BBFF)
private val VioletOnPrimaryDark = Color(0xFF3D008A)
private val VioletPrimaryContainerDark = Color(0xFF5B1EE0)
private val VioletOnPrimaryContainerDark = Color(0xFFF0E4FF)

private val SlateSecondaryDark = Color(0xFFCFC2FF)
private val SlateOnSecondaryDark = Color(0xFF35226B)
private val SlateSecondaryContainerDark = Color(0xFF5A3FA0)
private val SlateOnSecondaryContainerDark = Color(0xFFEAE0FF)

private val OrchidTertiaryDark = Color(0xFFF3A6FF)
private val OrchidOnTertiaryDark = Color(0xFF57006B)
private val OrchidTertiaryContainerDark = Color(0xFF7D2E96)
private val OrchidOnTertiaryContainerDark = Color(0xFFFCD9FF)

private val ErrorRedDark = Color(0xFFFFA199)
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
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFEDE3FF),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFF7A6F99),
    outlineVariant = Color(0xFFD6C9FF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF312F35),
    inverseOnSurface = Color(0xFFF4EFF4),
    surfaceDim = Color(0xFFDED8E0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2FF),
    surfaceContainer = Color(0xFFF1E7FF),
    surfaceContainerHigh = Color(0xFFEAD9FF),
    surfaceContainerHighest = Color(0xFFE2CEFF),
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
    surfaceVariant = Color(0xFF4A3F66),
    onSurfaceVariant = Color(0xFFD6C9FF),
    outline = Color(0xFFA898D9),
    outlineVariant = Color(0xFF4A3F66),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF312F35),
    surfaceDim = Color(0xFF141318),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0D0B12),
    surfaceContainerLow = Color(0xFF1D1A27),
    surfaceContainer = Color(0xFF241F33),
    surfaceContainerHigh = Color(0xFF2F2840),
    surfaceContainerHighest = Color(0xFF3A324F),
)

// Material 3 has no built-in "success"/"warning" roles, but Actua needs both for
// paid/cleared and due-soon status across Accounts, Transactions, Schedules and
// Bills. These live here rather than as scattered hex literals per screen, and
// pick a theme-appropriate tone from the current background rather than a
// single hardcoded value that only reads correctly in light mode.
private val SuccessGreenLight = Color(0xFF16A34A)
private val SuccessGreenDark = Color(0xFF4ADE80)
private val WarningAmberLight = Color(0xFFF97316)
private val WarningAmberDark = Color(0xFFFFA726)

val ColorScheme.success: Color
    @Composable get() = if (background.luminance() > 0.5f) SuccessGreenLight else SuccessGreenDark

val ColorScheme.warning: Color
    @Composable get() = if (background.luminance() > 0.5f) WarningAmberLight else WarningAmberDark
