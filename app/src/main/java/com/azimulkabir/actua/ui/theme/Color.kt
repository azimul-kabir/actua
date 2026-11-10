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

/** The primary, secondary and tertiary roles an accent palette overrides, each with its on-color and container. */
class AccentColors(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color, val tertiaryContainer: Color, val onTertiaryContainer: Color,
    // Card and container surfaces, tinted to the accent so cards match it.
    val surfaceVariant: Color, val onSurfaceVariant: Color, val outline: Color, val outlineVariant: Color,
    val surfaceContainerLow: Color, val surfaceContainer: Color, val surfaceContainerHigh: Color, val surfaceContainerHighest: Color,
)

/**
 * Accent palettes a supporter can choose (Play build). [Violet] is Actua's own default scheme; the
 * others override the primary, secondary and tertiary roles and tint the card and container
 * surfaces to match, keeping the neutral page background and the error colors.
 * Every on-color is at least 4.5:1 against its color (checked by AccentPaletteTest).
 */
enum class AccentPalette(private val light: AccentColors?, private val dark: AccentColors?) {
    Violet(null, null),
    Teal(
        light = AccentColors(
            Color(0xFF0A766B), Color(0xFFFFFFFF), Color(0xFFC6FAF5), Color(0xFF053933),
            Color(0xFF3D7670), Color(0xFFFFFFFF), Color(0xFFD7EAE8), Color(0xFF152826),
            Color(0xFF356DB6), Color(0xFFFFFFFF), Color(0xFFD0DEF1), Color(0xFF0E1C2F),
            Color(0xFFE1F4F2), Color(0xFF3D514F), Color(0xFF6C8986), Color(0xFFC6E7E3),
            Color(0xFFF3FCFB), Color(0xFFE7F9F7), Color(0xFFDBF5F2), Color(0xFFD0F1EE),
        ),
        dark = AccentColors(
            Color(0xFFA1F7EF), Color(0xFF05423C), Color(0xFF08685E), Color(0xFFD9FCF8),
            Color(0xFFBCDCD9), Color(0xFF182F2D), Color(0xFF264A46), Color(0xFFE4F1F0),
            Color(0xFFB0C8E8), Color(0xFF102137), Color(0xFF193457), Color(0xFFDFE9F6),
            Color(0xFF304B48), Color(0xFFCDEAE7), Color(0xFF83B9B4), Color(0xFF304B48),
            Color(0xFF142422), Color(0xFF1A2E2C), Color(0xFF223A37), Color(0xFF2B4643),
        ),
    ),
    Amber(
        light = AccentColors(
            Color(0xFF92610C), Color(0xFFFFFFFF), Color(0xFFFAE7C6), Color(0xFF392605),
            Color(0xFF806942), Color(0xFFFFFFFF), Color(0xFFEAE3D7), Color(0xFF282115),
            Color(0xFF5A7321), Color(0xFFFFFFFF), Color(0xFFE7F1D0), Color(0xFF252F0E),
            Color(0xFFF4EDE1), Color(0xFF514A3D), Color(0xFF897E6C), Color(0xFFE7DBC6),
            Color(0xFFFCF9F3), Color(0xFFF9F2E7), Color(0xFFF5EBDB), Color(0xFFF1E5D0),
        ),
        dark = AccentColors(
            Color(0xFFF7D8A1), Color(0xFF422C05), Color(0xFF684508), Color(0xFFFCEFD9),
            Color(0xFFDCD0BC), Color(0xFF2F2718), Color(0xFF4A3D26), Color(0xFFF1ECE4),
            Color(0xFFD7E8B0), Color(0xFF2C3710), Color(0xFF445719), Color(0xFFEFF6DF),
            Color(0xFF4B4130), Color(0xFFEADFCD), Color(0xFFB9A583), Color(0xFF4B4130),
            Color(0xFF241E14), Color(0xFF2E261A), Color(0xFF3A3122), Color(0xFF463C2B),
        ),
    ),
    Rose(
        light = AccentColors(
            Color(0xFFD41152), Color(0xFFFFFFFF), Color(0xFFFAC6D8), Color(0xFF390516),
            Color(0xFF9B5069), Color(0xFFFFFFFF), Color(0xFFEAD7DD), Color(0xFF28151B),
            Color(0xFFA65730), Color(0xFFFFFFFF), Color(0xFFF1DBD0), Color(0xFF2F190E),
            Color(0xFFF4E1E8), Color(0xFF513D44), Color(0xFF896C76), Color(0xFFE7C6D1),
            Color(0xFFFCF3F6), Color(0xFFF9E7ED), Color(0xFFF5DBE4), Color(0xFFF1D0DB),
        ),
        dark = AccentColors(
            Color(0xFFF7A1BE), Color(0xFF42051A), Color(0xFF680828), Color(0xFFFCD9E5),
            Color(0xFFDCBCC7), Color(0xFF2F1820), Color(0xFF4A2632), Color(0xFFF1E4E8),
            Color(0xFFE8C3B0), Color(0xFF371D10), Color(0xFF572E19), Color(0xFFF6E7DF),
            Color(0xFF4B3039), Color(0xFFEACDD7), Color(0xFFB98395), Color(0xFF4B3039),
            Color(0xFF241419), Color(0xFF2E1A20), Color(0xFF3A222A), Color(0xFF462B34),
        ),
    ),
    Forest(
        light = AccentColors(
            Color(0xFF0A7B2C), Color(0xFFFFFFFF), Color(0xFFC6FAD6), Color(0xFF053914),
            Color(0xFF3D764E), Color(0xFFFFFFFF), Color(0xFFD7EADC), Color(0xFF15281B),
            Color(0xFF227774), Color(0xFFFFFFFF), Color(0xFFD0F1F0), Color(0xFF0E2F2E),
            Color(0xFFE1F4E7), Color(0xFF3D5143), Color(0xFF6C8975), Color(0xFFC6E7D0),
            Color(0xFFF3FCF6), Color(0xFFE7F9EC), Color(0xFFDBF5E3), Color(0xFFD0F1DA),
        ),
        dark = AccentColors(
            Color(0xFFA1F7BB), Color(0xFF054218), Color(0xFF086825), Color(0xFFD9FCE4),
            Color(0xFFBCDCC5), Color(0xFF182F1F), Color(0xFF264A31), Color(0xFFE4F1E8),
            Color(0xFFB0E8E6), Color(0xFF103736), Color(0xFF195755), Color(0xFFDFF6F5),
            Color(0xFF304B38), Color(0xFFCDEAD6), Color(0xFF83B993), Color(0xFF304B38),
            Color(0xFF142419), Color(0xFF1A2E20), Color(0xFF223A29), Color(0xFF2B4633),
        ),
    ),
    Ocean(
        light = AccentColors(
            Color(0xFF106EC1), Color(0xFFFFFFFF), Color(0xFFC6E2FA), Color(0xFF052039),
            Color(0xFF4B7091), Color(0xFFFFFFFF), Color(0xFFD7E1EA), Color(0xFF151F28),
            Color(0xFF4635B6), Color(0xFFFFFFFF), Color(0xFFD4D0F1), Color(0xFF120E2F),
            Color(0xFFE1EBF4), Color(0xFF3D4851), Color(0xFF6C7B89), Color(0xFFC6D7E7),
            Color(0xFFF3F8FC), Color(0xFFE7F0F9), Color(0xFFDBE9F5), Color(0xFFD0E2F1),
        ),
        dark = AccentColors(
            Color(0xFFA1CFF7), Color(0xFF052642), Color(0xFF083B68), Color(0xFFD9ECFC),
            Color(0xFFBCCDDC), Color(0xFF18242F), Color(0xFF26394A), Color(0xFFE4EBF1),
            Color(0xFFB7B0E8), Color(0xFF151037), Color(0xFF211957), Color(0xFFE2DFF6),
            Color(0xFF303E4B), Color(0xFFCDDCEA), Color(0xFF83A0B9), Color(0xFF303E4B),
            Color(0xFF141D24), Color(0xFF1A242E), Color(0xFF222F3A), Color(0xFF2B3946),
        ),
    ),
    Mono(
        light = AccentColors(
            Color(0xFF333333), Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF1A1A1A),
            Color(0xFF525252), Color(0xFFFFFFFF), Color(0xFFEBEBEB), Color(0xFF1F1F1F),
            Color(0xFF6B6B6B), Color(0xFFFFFFFF), Color(0xFFD6D6D6), Color(0xFF1F1F1F),
            Color(0xFFEBEBEB), Color(0xFF474747), Color(0xFF7A7A7A), Color(0xFFD6D6D6),
            Color(0xFFF7F7F7), Color(0xFFF0F0F0), Color(0xFFE8E8E8), Color(0xFFE0E0E0),
        ),
        dark = AccentColors(
            Color(0xFFDBDBDB), Color(0xFF1A1A1A), Color(0xFF3D3D3D), Color(0xFFEDEDED),
            Color(0xFFC2C2C2), Color(0xFF1F1F1F), Color(0xFF333333), Color(0xFFEBEBEB),
            Color(0xFFA8A8A8), Color(0xFF1F1F1F), Color(0xFF474747), Color(0xFFEDEDED),
            Color(0xFF3D3D3D), Color(0xFFDBDBDB), Color(0xFF9E9E9E), Color(0xFF3D3D3D),
            Color(0xFF1C1C1C), Color(0xFF242424), Color(0xFF2E2E2E), Color(0xFF383838),
        ),
    ),
    ;

    /** [ActuaLightColorScheme] or [ActuaDarkColorScheme] with this palette's accent roles applied. */
    fun colorScheme(darkTheme: Boolean): ColorScheme {
        val base = if (darkTheme) ActuaDarkColorScheme else ActuaLightColorScheme
        val accent = (if (darkTheme) dark else light) ?: return base
        return base.copy(
            primary = accent.primary, onPrimary = accent.onPrimary,
            primaryContainer = accent.primaryContainer, onPrimaryContainer = accent.onPrimaryContainer,
            inversePrimary = if (darkTheme) (light ?: return base).primary else (dark ?: return base).primary,
            secondary = accent.secondary, onSecondary = accent.onSecondary,
            secondaryContainer = accent.secondaryContainer, onSecondaryContainer = accent.onSecondaryContainer,
            surfaceVariant = accent.surfaceVariant, onSurfaceVariant = accent.onSurfaceVariant,
            outline = accent.outline, outlineVariant = accent.outlineVariant,
            surfaceContainerLow = accent.surfaceContainerLow, surfaceContainer = accent.surfaceContainer,
            surfaceContainerHigh = accent.surfaceContainerHigh, surfaceContainerHighest = accent.surfaceContainerHighest,
            tertiary = accent.tertiary, onTertiary = accent.onTertiary,
            tertiaryContainer = accent.tertiaryContainer, onTertiaryContainer = accent.onTertiaryContainer,
        )
    }

    companion object {
        /** The id the accent registers its supporter-status reset under. */
        const val RESET_ID = "accent"

        fun fromName(name: String?): AccentPalette = entries.firstOrNull { it.name == name } ?: Violet
    }
}

// Material 3 has no built-in "success"/"warning" roles, but Actua needs both for
// paid/cleared and due-soon status across Accounts, Transactions, Schedules and
// Bills. These live here rather than as scattered hex literals per screen, and
// pick a theme-appropriate tone from the current background rather than a
// single hardcoded value that only reads correctly in light mode.
private val SuccessGreenLight = Color(0xFF16A34A)
private val SuccessGreenDark = Color(0xFF4ADE80)
private val WarningAmberLight = Color(0xFFF97316)
private val WarningAmberDark = Color(0xFFFFA726)
// The Material dark error tone is a pale salmon meant for error text, which doesn't read as red
// in a fill or status color, so overspending uses this saturated red in both themes instead.
private val DangerRedLight = Color(0xFFE0201A)
private val DangerRedDark = Color(0xFFEF4444)

val ColorScheme.success: Color
    @Composable get() = if (background.luminance() > 0.5f) SuccessGreenLight else SuccessGreenDark

val ColorScheme.warning: Color
    @Composable get() = if (background.luminance() > 0.5f) WarningAmberLight else WarningAmberDark

val ColorScheme.danger: Color
    @Composable get() = if (background.luminance() > 0.5f) DangerRedLight else DangerRedDark
