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
 * The Accent color page (Settings → Display → Supporter extras). Material You (Android 12+) and
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
                        is AccentChoice.Palette -> !useDynamicColor && choice.accent == accent
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
                                choice == AccentChoice.MaterialYou -> onUseDynamicColorChange(true)
                                choice is AccentChoice.Palette -> {
                                    accent = choice.accent
                                    preferences.accent = choice.accent
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
