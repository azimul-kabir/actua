package com.azimulkabir.actua.ui.theme

import androidx.compose.ui.graphics.Color

// The Catppuccin palette, copied from the official palette source: https://github.com/catppuccin/palette
// (palette.json, version 1.8.0, commit 07d02aa110ef9eb7e7427afca5c73ba9cf7f8ebd). Catppuccin is MIT-licensed,
// Copyright (c) 2021 Catppuccin; see NOTICE.md. Only the colors Actua uses are listed. Do not edit the hex values:
// where a pair needs more contrast, CatppuccinScheme picks a different on-color instead.

/** One Catppuccin flavor: its 14 accent colors and the neutral ramp from Text down to Crust. */
internal class CatppuccinPalette(
    val accents: Map<CatppuccinAccent, Color>,
    val text: Color, val subtext1: Color, val subtext0: Color,
    val overlay2: Color, val overlay1: Color, val overlay0: Color,
    val surface2: Color, val surface1: Color, val surface0: Color,
    val base: Color, val mantle: Color, val crust: Color,
)

/** The 14 Catppuccin accents, in the palette's own order (which the picker and the chart series follow). */
enum class CatppuccinAccent(val displayName: String) {
    Rosewater("Rosewater"),
    Flamingo("Flamingo"),
    Pink("Pink"),
    Mauve("Mauve"),
    Red("Red"),
    Maroon("Maroon"),
    Peach("Peach"),
    Yellow("Yellow"),
    Green("Green"),
    Teal("Teal"),
    Sky("Sky"),
    Sapphire("Sapphire"),
    Blue("Blue"),
    Lavender("Lavender"),
;
}

internal val CatppuccinLattePalette = CatppuccinPalette(
    accents = mapOf(
        CatppuccinAccent.Rosewater to Color(0xFFDC8A78),
        CatppuccinAccent.Flamingo to Color(0xFFDD7878),
        CatppuccinAccent.Pink to Color(0xFFEA76CB),
        CatppuccinAccent.Mauve to Color(0xFF8839EF),
        CatppuccinAccent.Red to Color(0xFFD20F39),
        CatppuccinAccent.Maroon to Color(0xFFE64553),
        CatppuccinAccent.Peach to Color(0xFFFE640B),
        CatppuccinAccent.Yellow to Color(0xFFDF8E1D),
        CatppuccinAccent.Green to Color(0xFF40A02B),
        CatppuccinAccent.Teal to Color(0xFF179299),
        CatppuccinAccent.Sky to Color(0xFF04A5E5),
        CatppuccinAccent.Sapphire to Color(0xFF209FB5),
        CatppuccinAccent.Blue to Color(0xFF1E66F5),
        CatppuccinAccent.Lavender to Color(0xFF7287FD),
    ),
    text = Color(0xFF4C4F69),
    subtext1 = Color(0xFF5C5F77),
    subtext0 = Color(0xFF6C6F85),
    overlay2 = Color(0xFF7C7F93),
    overlay1 = Color(0xFF8C8FA1),
    overlay0 = Color(0xFF9CA0B0),
    surface2 = Color(0xFFACB0BE),
    surface1 = Color(0xFFBCC0CC),
    surface0 = Color(0xFFCCD0DA),
    base = Color(0xFFEFF1F5),
    mantle = Color(0xFFE6E9EF),
    crust = Color(0xFFDCE0E8),
)

internal val CatppuccinFrappePalette = CatppuccinPalette(
    accents = mapOf(
        CatppuccinAccent.Rosewater to Color(0xFFF2D5CF),
        CatppuccinAccent.Flamingo to Color(0xFFEEBEBE),
        CatppuccinAccent.Pink to Color(0xFFF4B8E4),
        CatppuccinAccent.Mauve to Color(0xFFCA9EE6),
        CatppuccinAccent.Red to Color(0xFFE78284),
        CatppuccinAccent.Maroon to Color(0xFFEA999C),
        CatppuccinAccent.Peach to Color(0xFFEF9F76),
        CatppuccinAccent.Yellow to Color(0xFFE5C890),
        CatppuccinAccent.Green to Color(0xFFA6D189),
        CatppuccinAccent.Teal to Color(0xFF81C8BE),
        CatppuccinAccent.Sky to Color(0xFF99D1DB),
        CatppuccinAccent.Sapphire to Color(0xFF85C1DC),
        CatppuccinAccent.Blue to Color(0xFF8CAAEE),
        CatppuccinAccent.Lavender to Color(0xFFBABBF1),
    ),
    text = Color(0xFFC6D0F5),
    subtext1 = Color(0xFFB5BFE2),
    subtext0 = Color(0xFFA5ADCE),
    overlay2 = Color(0xFF949CBB),
    overlay1 = Color(0xFF838BA7),
    overlay0 = Color(0xFF737994),
    surface2 = Color(0xFF626880),
    surface1 = Color(0xFF51576D),
    surface0 = Color(0xFF414559),
    base = Color(0xFF303446),
    mantle = Color(0xFF292C3C),
    crust = Color(0xFF232634),
)

internal val CatppuccinMacchiatoPalette = CatppuccinPalette(
    accents = mapOf(
        CatppuccinAccent.Rosewater to Color(0xFFF4DBD6),
        CatppuccinAccent.Flamingo to Color(0xFFF0C6C6),
        CatppuccinAccent.Pink to Color(0xFFF5BDE6),
        CatppuccinAccent.Mauve to Color(0xFFC6A0F6),
        CatppuccinAccent.Red to Color(0xFFED8796),
        CatppuccinAccent.Maroon to Color(0xFFEE99A0),
        CatppuccinAccent.Peach to Color(0xFFF5A97F),
        CatppuccinAccent.Yellow to Color(0xFFEED49F),
        CatppuccinAccent.Green to Color(0xFFA6DA95),
        CatppuccinAccent.Teal to Color(0xFF8BD5CA),
        CatppuccinAccent.Sky to Color(0xFF91D7E3),
        CatppuccinAccent.Sapphire to Color(0xFF7DC4E4),
        CatppuccinAccent.Blue to Color(0xFF8AADF4),
        CatppuccinAccent.Lavender to Color(0xFFB7BDF8),
    ),
    text = Color(0xFFCAD3F5),
    subtext1 = Color(0xFFB8C0E0),
    subtext0 = Color(0xFFA5ADCB),
    overlay2 = Color(0xFF939AB7),
    overlay1 = Color(0xFF8087A2),
    overlay0 = Color(0xFF6E738D),
    surface2 = Color(0xFF5B6078),
    surface1 = Color(0xFF494D64),
    surface0 = Color(0xFF363A4F),
    base = Color(0xFF24273A),
    mantle = Color(0xFF1E2030),
    crust = Color(0xFF181926),
)

internal val CatppuccinMochaPalette = CatppuccinPalette(
    accents = mapOf(
        CatppuccinAccent.Rosewater to Color(0xFFF5E0DC),
        CatppuccinAccent.Flamingo to Color(0xFFF2CDCD),
        CatppuccinAccent.Pink to Color(0xFFF5C2E7),
        CatppuccinAccent.Mauve to Color(0xFFCBA6F7),
        CatppuccinAccent.Red to Color(0xFFF38BA8),
        CatppuccinAccent.Maroon to Color(0xFFEBA0AC),
        CatppuccinAccent.Peach to Color(0xFFFAB387),
        CatppuccinAccent.Yellow to Color(0xFFF9E2AF),
        CatppuccinAccent.Green to Color(0xFFA6E3A1),
        CatppuccinAccent.Teal to Color(0xFF94E2D5),
        CatppuccinAccent.Sky to Color(0xFF89DCEB),
        CatppuccinAccent.Sapphire to Color(0xFF74C7EC),
        CatppuccinAccent.Blue to Color(0xFF89B4FA),
        CatppuccinAccent.Lavender to Color(0xFFB4BEFE),
    ),
    text = Color(0xFFCDD6F4),
    subtext1 = Color(0xFFBAC2DE),
    subtext0 = Color(0xFFA6ADC8),
    overlay2 = Color(0xFF9399B2),
    overlay1 = Color(0xFF7F849C),
    overlay0 = Color(0xFF6C7086),
    surface2 = Color(0xFF585B70),
    surface1 = Color(0xFF45475A),
    surface0 = Color(0xFF313244),
    base = Color(0xFF1E1E2E),
    mantle = Color(0xFF181825),
    crust = Color(0xFF11111B),
)
