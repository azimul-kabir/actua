package com.azimulkabir.actua.ui.settings

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.theme.CatppuccinAccent
import com.azimulkabir.actua.ui.theme.CatppuccinFlavor
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.theme.AccentPalette
import com.azimulkabir.actua.ui.theme.Spacing

@StringRes
internal fun accentLabelRes(accent: AccentPalette): Int = when (accent) {
    AccentPalette.Violet -> R.string.settings_accent_violet
    AccentPalette.Teal -> R.string.settings_accent_teal
    AccentPalette.Amber -> R.string.settings_accent_amber
    AccentPalette.Rose -> R.string.settings_accent_rose
    AccentPalette.Forest -> R.string.settings_accent_forest
    AccentPalette.Ocean -> R.string.settings_accent_ocean
    AccentPalette.Mono -> R.string.settings_accent_mono
}

/** What the Accent color page offers: the device's Material You colors, or one of the palettes. */
private sealed interface AccentChoice {
    data object MaterialYou : AccentChoice
    data class Palette(val accent: AccentPalette) : AccentChoice
}

/**
 * The Accent color page (Settings → Display, and Settings → Supporter extras). Material You (Android 12+) and
 * Violet, Actua's own colors, are free for everyone; the other palettes are Supporter extras, and
 * a non-supporter who taps one is sent to the Support page. Choosing a palette turns Material You
 * off, and a pick applies right away because the app theme follows these settings.
 */
@Composable
internal fun AccentSettings(
    isSupporter: Boolean,
    useDynamicColor: Boolean,
    onUseDynamicColorChange: (Boolean) -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { DisplayPreferences(context) }
    var accent by remember { mutableStateOf(preferences.accent) }
    var catppuccinOn by remember { mutableStateOf(preferences.catppuccinEnabled) }
    val materialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val choices = buildList<AccentChoice> {
        if (materialYouAvailable) add(AccentChoice.MaterialYou)
        AccentPalette.entries.forEach { add(AccentChoice.Palette(it)) }
    }
    Column(
        modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        choices.chunked(2).forEach { rowChoices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rowChoices.forEach { choice ->
                    val scheme = when (choice) {
                        AccentChoice.MaterialYou ->
                            if (materialYouAvailable) materialYouScheme(context, dark) else MaterialTheme.colorScheme
                        is AccentChoice.Palette -> choice.accent.colorScheme(dark)
                    }
                    val locked = choice is AccentChoice.Palette && choice.accent != AccentPalette.Violet && !isSupporter
                    val selected = when (choice) {
                        AccentChoice.MaterialYou -> useDynamicColor
                        is AccentChoice.Palette -> !useDynamicColor && !catppuccinOn && choice.accent == accent
                    }
                    AccentTile(
                        label = stringResource(
                            when (choice) {
                                AccentChoice.MaterialYou -> R.string.settings_accent_material_you
                                is AccentChoice.Palette -> accentLabelRes(choice.accent)
                            },
                        ),
                        scheme = scheme,
                        selected = selected,
                        locked = locked,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when {
                                locked -> onOpenSupport()
                                choice == AccentChoice.MaterialYou -> {
                                    catppuccinOn = false
                                    preferences.catppuccinEnabled = false
                                    onUseDynamicColorChange(true)
                                }
                                choice is AccentChoice.Palette -> {
                                    accent = choice.accent
                                    preferences.accent = choice.accent
                                    // Choosing one of Actua's palettes turns the Catppuccin theme and Material You off.
                                    catppuccinOn = false
                                    preferences.catppuccinEnabled = false
                                    if (useDynamicColor) onUseDynamicColorChange(false)
                                }
                            }
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.settings_accent_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        CatppuccinSection(
            isSupporter = isSupporter,
            enabled = catppuccinOn,
            onEnabledChange = { on ->
                catppuccinOn = on
                preferences.catppuccinEnabled = on
                // Choosing Catppuccin turns Material You off, as choosing an accent does.
                if (on && useDynamicColor) onUseDynamicColorChange(false)
            },
            preferences = preferences,
            onOpenSupport = onOpenSupport,
        )
    }
}

/**
 * The Catppuccin group of the Accent color page: a switch, the dark flavor and one of the 14 accents, with a credit
 * line. A non-supporter sees the group marked "Supporter" and is sent to the Support page when they tap it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatppuccinSection(
    isSupporter: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    preferences: DisplayPreferences,
    onOpenSupport: () -> Unit,
) {
    var darkFlavor by remember { mutableStateOf(preferences.catppuccinDarkFlavor) }
    var accent by remember { mutableStateOf(preferences.catppuccinAccent) }
    val uriHandler = LocalUriHandler.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ActuaGroupLabel(stringResource(R.string.settings_catppuccin))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = enabled && isSupporter, role = Role.Switch,
                            onValueChange = { if (isSupporter) onEnabledChange(it) else onOpenSupport() },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_catppuccin_use), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_catppuccin_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    if (isSupporter) {
                        Switch(checked = enabled, onCheckedChange = null)
                    } else {
                        Text(
                            stringResource(R.string.settings_supporter_extras_marker),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isSupporter) {
                    Text(stringResource(R.string.settings_catppuccin_dark_flavor), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CatppuccinFlavor.dark.forEach { flavor ->
                            val colors = flavor.palette
                            FlavorTile(
                                label = flavor.displayName,
                                swatches = listOf(colors.base, colors.surface1, colors.accents.getValue(accent)),
                                selected = flavor == darkFlavor,
                                modifier = Modifier.weight(1f),
                            ) {
                                darkFlavor = flavor
                                preferences.catppuccinDarkFlavor = flavor
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.settings_catppuccin_accent) + ": " + accent.displayName,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val palette = (if (dark) darkFlavor else CatppuccinFlavor.Latte).palette
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CatppuccinAccent.entries.forEach { option ->
                            AccentDot(
                                color = palette.accents.getValue(option),
                                label = option.displayName,
                                selected = option == accent,
                            ) {
                                accent = option
                                preferences.catppuccinAccent = option
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_catppuccin_credit),
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { uriHandler.openUri("https://github.com/catppuccin/catppuccin") }
                        .padding(vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun FlavorTile(label: String, swatches: List<Color>, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                swatches.forEach { Box(Modifier.size(18.dp).clip(CircleShape).background(it).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun AccentDot(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.Black.takeIf { color.luminance() > 0.4f } ?: Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Only called when [AccentChoice.MaterialYou] is offered, which needs Android 12. */
@RequiresApi(Build.VERSION_CODES.S)
private fun materialYouScheme(context: Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

@Composable
private fun AccentTile(
    label: String,
    scheme: ColorScheme,
    selected: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Swatch(scheme.primary, Modifier.size(40.dp))
                Swatch(scheme.secondary, Modifier.size(28.dp))
                Swatch(scheme.tertiary, Modifier.size(28.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            if (locked) {
                Text(
                    stringResource(R.string.settings_supporter_extras_marker),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(color))
}
