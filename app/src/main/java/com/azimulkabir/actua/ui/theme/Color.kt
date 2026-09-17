package com.azimulkabir.actua.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Actua brand palette: a restrained teal accent (money, growth) over neutral
// surfaces, following Material 3 tonal conventions. Kept in one place so the
// whole app shares a single, deliberate color language in both themes.

private val TealPrimary = Color(0xFF00695C)
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealPrimaryContainer = Color(0xFFB2DFDB)
private val TealOnPrimaryContainer = Color(0xFF00251A)

private val SageSecondary = Color(0xFF4C6359)
private val SageOnSecondary = Color(0xFFFFFFFF)
private val SageSecondaryContainer = Color(0xFFCEE9DA)
private val SageOnSecondaryContainer = Color(0xFF092017)

private val SlateTertiary = Color(0xFF3D6373)
private val SlateOnTertiary = Color(0xFFFFFFFF)
private val SlateTertiaryContainer = Color(0xFFC0E9FB)
private val SlateOnTertiaryContainer = Color(0xFF001F29)

private val ErrorRed = Color(0xFFBA1A1A)
private val OnErrorRed = Color(0xFFFFFFFF)
private val ErrorContainerRed = Color(0xFFFFDAD6)
private val OnErrorContainerRed = Color(0xFF410002)

private val TealPrimaryDark = Color(0xFF4DB6AC)
private val TealOnPrimaryDark = Color(0xFF003731)
private val TealPrimaryContainerDark = Color(0xFF00504A)
private val TealOnPrimaryContainerDark = Color(0xFFB2DFDB)

private val SageSecondaryDark = Color(0xFFB3CCC0)
private val SageOnSecondaryDark = Color(0xFF1F352C)
private val SageSecondaryContainerDark = Color(0xFF354B41)
private val SageOnSecondaryContainerDark = Color(0xFFCEE9DA)

private val SlateTertiaryDark = Color(0xFFA5CCDD)
private val SlateOnTertiaryDark = Color(0xFF073543)
private val SlateTertiaryContainerDark = Color(0xFF244C5B)
private val SlateOnTertiaryContainerDark = Color(0xFFC0E9FB)

private val ErrorRedDark = Color(0xFFFFB4AB)
private val OnErrorRedDark = Color(0xFF690005)
private val ErrorContainerRedDark = Color(0xFF93000A)
private val OnErrorContainerRedDark = Color(0xFFFFDAD6)

val ActuaLightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    inversePrimary = Color(0xFF80CBC4),
    secondary = SageSecondary,
    onSecondary = SageOnSecondary,
    secondaryContainer = SageSecondaryContainer,
    onSecondaryContainer = SageOnSecondaryContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateTertiaryContainer,
    onTertiaryContainer = SlateOnTertiaryContainer,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainerRed,
    onErrorContainer = OnErrorContainerRed,
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF7FAF9),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3230),
    inverseOnSurface = Color(0xFFEFF1EF),
    surfaceDim = Color(0xFFD6DAD8),
    surfaceBright = Color(0xFFF7FAF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F3),
    surfaceContainer = Color(0xFFEBEFED),
    surfaceContainerHigh = Color(0xFFE5EAE7),
    surfaceContainerHighest = Color(0xFFDFE4E1),
)

val ActuaDarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    inversePrimary = TealPrimary,
    secondary = SageSecondaryDark,
    onSecondary = SageOnSecondaryDark,
    secondaryContainer = SageSecondaryContainerDark,
    onSecondaryContainer = SageOnSecondaryContainerDark,
    tertiary = SlateTertiaryDark,
    onTertiary = SlateOnTertiaryDark,
    tertiaryContainer = SlateTertiaryContainerDark,
    onTertiaryContainer = SlateOnTertiaryContainerDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorContainerRedDark,
    onErrorContainer = OnErrorContainerRedDark,
    background = Color(0xFF101413),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF899791),
    outlineVariant = Color(0xFF3F4945),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDEE4E1),
    inverseOnSurface = Color(0xFF2D3230),
    surfaceDim = Color(0xFF101413),
    surfaceBright = Color(0xFF363A38),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D211F),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323635),
)
