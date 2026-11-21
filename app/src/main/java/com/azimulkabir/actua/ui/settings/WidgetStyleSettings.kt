package com.azimulkabir.actua.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.widget.WidgetCornerRadius
import com.azimulkabir.actua.widget.WidgetLook
import com.azimulkabir.actua.widget.WidgetLookOverride
import kotlin.math.roundToInt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.widget.WidgetStyle
import com.azimulkabir.actua.widget.WidgetStyles
import com.azimulkabir.actua.widget.WidgetUpdater

@StringRes
internal fun widgetStyleLabelRes(style: WidgetStyle): Int = when (style) {
    WidgetStyle.Default -> R.string.settings_widget_style_default
    WidgetStyle.Translucent -> R.string.settings_widget_style_translucent
    WidgetStyle.SolidDark -> R.string.settings_widget_style_solid_dark
    WidgetStyle.SolidLight -> R.string.settings_widget_style_solid_light
    WidgetStyle.AccentTinted -> R.string.settings_widget_style_accent
    WidgetStyle.GradientAccent -> R.string.settings_widget_style_gradient
    WidgetStyle.Outline -> R.string.settings_widget_style_outline
    WidgetStyle.Midnight -> R.string.settings_widget_style_midnight
}

@StringRes
internal fun widgetRadiusLabelRes(radius: WidgetCornerRadius): Int = when (radius) {
    WidgetCornerRadius.System -> R.string.settings_widget_radius_system
    WidgetCornerRadius.Small -> R.string.settings_widget_radius_small
    WidgetCornerRadius.Medium -> R.string.settings_widget_radius_medium
    WidgetCornerRadius.Large -> R.string.settings_widget_radius_large
}

/** The styles this device can draw: the ones that tint the background need Android 12. */
internal fun availableWidgetStyles(): List<WidgetStyle> =
    WidgetStyle.entries.filter { !it.needsAndroid12 || WidgetStyles.accentTintedAvailable() }

/**
 * The Widget style page (Settings → Display → Supporter extras): a preview of every style with the
 * chosen one marked. A tap restyles all home-screen widgets straight away. A non-supporter, who can reach
 * this page again from a restored screen after support lapsed, sees every style but Default marked
 * "Supporter", and a tap on one opens the Support page.
 */
@Composable
internal fun WidgetStyleSettings(isSupporter: Boolean, onOpenSupport: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { DisplayPreferences(context) }
    var chosen by remember { mutableStateOf(preferences.widgetStyle) }
    var radius by remember { mutableStateOf(preferences.widgetCornerRadius) }
    var opacity by remember { mutableStateOf(preferences.widgetOpacity) }
    // A widget is only drawn in a chosen style for a supporter, so that is also what a non-supporter sees marked.
    val selected = if (isSupporter) chosen else WidgetStyle.Default
    val styles = availableWidgetStyles()
    Column(
        // The tiles are one set of radio choices, so a screen reader announces them as a group.
        modifier.selectableGroup().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        styles.chunked(2).forEach { rowStyles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rowStyles.forEach { style ->
                    val locked = !isSupporter && style != WidgetStyle.Default
                    WidgetStyleTile(
                        style = style,
                        selected = style == selected,
                        locked = locked,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (locked) {
                                onOpenSupport()
                            } else {
                                chosen = style
                                preferences.widgetStyle = style
                                WidgetUpdater.requestAll(context)
                            }
                        },
                    )
                }
                if (rowStyles.size == 1) Box(Modifier.weight(1f))
            }
        }
        Text(
            stringResource(R.string.settings_widget_style_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        if (WidgetStyles.accentTintedAvailable()) {
            ActuaGroupLabel(stringResource(R.string.settings_widget_all_widgets))
            WidgetShapeControls(
                style = selected,
                radius = if (isSupporter) radius else WidgetCornerRadius.System,
                opacity = if (isSupporter) opacity else WidgetLook.FULL_OPACITY,
                locked = !isSupporter,
                onLocked = onOpenSupport,
                onRadiusChange = {
                    radius = it
                    preferences.widgetCornerRadius = it
                    WidgetUpdater.requestAll(context)
                },
                onOpacityChange = { opacity = it },
                onOpacityChangeFinished = {
                    preferences.widgetOpacity = opacity
                    WidgetUpdater.requestAll(context)
                },
            )
        }
    }
}

/**
 * Corner radius and background opacity, for all widgets or one. The opacity slider stops at the lowest value that keeps
 * [style]'s text readable on any wallpaper. Both apply to a styled look only, so with Default they are shown disabled.
 * [locked] shows them marked "Supporter", and a tap calls [onLocked].
 */
@Composable
internal fun WidgetShapeControls(
    style: WidgetStyle,
    radius: WidgetCornerRadius,
    opacity: Int,
    locked: Boolean,
    onLocked: () -> Unit,
    onRadiusChange: (WidgetCornerRadius) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onOpacityChangeFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val enabled = !locked && style != WidgetStyle.Default
    val night = isSystemInDarkTheme()
    val floor = remember(style, night) { if (style == WidgetStyle.Default) WidgetLook.LOWEST_OPACITY else WidgetStyles.minimumOpacity(context, style) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_widget_radius), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (locked) SupporterMarker()
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WidgetCornerRadius.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == radius,
                    onClick = { if (locked) onLocked() else onRadiusChange(option) },
                    enabled = locked || enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, WidgetCornerRadius.entries.size),
                    label = { Text(stringResource(widgetRadiusLabelRes(option)), maxLines = 1) },
                )
            }
        }
        val shown = opacity.coerceIn(floor, WidgetLook.FULL_OPACITY)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_widget_opacity), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.settings_widget_opacity_value, shown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val opacityLabel = stringResource(R.string.settings_widget_opacity)
        val steps = ((WidgetLook.FULL_OPACITY - floor) / WidgetLook.OPACITY_STEP - 1).coerceAtLeast(0)
        Box(Modifier.fillMaxWidth().then(if (locked) Modifier.clickable(role = Role.Button, onClick = onLocked) else Modifier)) {
            Slider(
                value = shown.toFloat(),
                onValueChange = { onOpacityChange(it.roundToInt()) },
                onValueChangeFinished = onOpacityChangeFinished,
                valueRange = floor.toFloat()..WidgetLook.FULL_OPACITY.toFloat(),
                steps = steps,
                enabled = enabled && floor < WidgetLook.FULL_OPACITY,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = opacityLabel },
            )
        }
        Text(
            stringResource(if (style == WidgetStyle.Default) R.string.settings_widget_shape_default_note else R.string.settings_widget_opacity_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SupporterMarker() {
    Text(
        stringResource(R.string.settings_supporter_extras_marker),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * One widget's own look, on its setup screen: a live preview, its style (or the one for all widgets), its corner radius
 * and opacity. Android 12+ only. A non-supporter sees it marked "Supporter" and can't change it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetLookSection(isSupporter: Boolean, look: WidgetLookOverride, onLookChange: (WidgetLookOverride) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { DisplayPreferences(context) }
    val global = remember { WidgetLook(preferences.widgetStyle, preferences.widgetCornerRadius, preferences.widgetOpacity) }
    val style = look.style ?: global.style
    val radius = look.radius ?: global.radius
    val opacity = look.opacity ?: global.opacity
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActuaGroupLabel(stringResource(R.string.widget_look_title), modifier = Modifier.weight(1f))
            if (!isSupporter) SupporterMarker()
        }
        WidgetPreview(if (isSupporter) style else WidgetStyle.Default, radius, opacity, height = 96.dp)
        val same = stringResource(R.string.widget_look_same)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = look.style == null,
                onClick = { if (isSupporter) onLookChange(look.copy(style = null)) },
                enabled = isSupporter,
                label = { Text(same) },
            )
            availableWidgetStyles().forEach { option ->
                FilterChip(
                    selected = look.style == option,
                    onClick = { if (isSupporter) onLookChange(look.copy(style = option)) },
                    enabled = isSupporter,
                    label = { Text(stringResource(widgetStyleLabelRes(option))) },
                )
            }
        }
        WidgetShapeControls(
            style = style,
            radius = radius,
            opacity = opacity,
            locked = !isSupporter,
            onLocked = {},
            onRadiusChange = { onLookChange(look.copy(radius = it)) },
            onOpacityChange = { onLookChange(look.copy(opacity = it)) },
        )
        if (!look.isEmpty && isSupporter) {
            TextButton(onClick = { onLookChange(WidgetLookOverride()) }) { Text(stringResource(R.string.widget_look_reset)) }
        }
    }
}

@Composable
private fun WidgetStyleTile(style: WidgetStyle, selected: Boolean, locked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.lg, horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            WidgetPreview(style)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(stringResource(widgetStyleLabelRes(style)), style = MaterialTheme.typography.bodyLarge)
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

/**
 * A small card in the style's colors, over a light-and-dark backdrop so translucency shows, with [radius] and [opacity]
 * as the widget would be drawn (the opacity never below what keeps the text readable).
 */
@Composable
internal fun WidgetPreview(
    style: WidgetStyle,
    radius: WidgetCornerRadius = WidgetCornerRadius.System,
    opacity: Int = WidgetLook.FULL_OPACITY,
    height: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val context = LocalContext.current
    // Widgets follow the device theme, not the app's own appearance setting, so the accent they are tinted
    // with comes from the same place the widget takes it, not from the app's current theme.
    val night = isSystemInDarkTheme()
    val colors = remember(style, night) { WidgetStyles.styleColors(context, style, night) }
    val look = WidgetLook(style, radius, opacity)
    val tint = colors?.let { WidgetStyles.backgroundTint(look, it) }
    val alpha = tint?.let { ((it ushr 24) and 0xFF) / 255f } ?: 1f
    // The tint carries the opacity (and, except for Outline, the style's own alpha), so the brush colors are opaque.
    fun brushColor(argb: Int) = if (style == WidgetStyle.Outline) Color(argb) else Color(argb).copy(alpha = 1f)
    val background = colors?.let { brushColor(it.background) } ?: colorResource(R.color.widget_background)
    val backgroundEnd = colors?.let { brushColor(it.backgroundEnd) } ?: background
    val corner = when (radius) {
        WidgetCornerRadius.System -> 12.dp
        WidgetCornerRadius.Small -> 4.dp
        WidgetCornerRadius.Medium -> 8.dp
        WidgetCornerRadius.Large -> 16.dp
    }
    val outline = if (style == WidgetStyle.Outline) colorResource(R.color.widget_outline_stroke) else null
    val onBackground = colors?.let { Color(it.onBackground) } ?: colorResource(R.color.widget_on_background)
    val primary = colors?.let { Color(it.primary) } ?: colorResource(R.color.widget_primary)
    Box(
        Modifier
            // Decoration: a tile is announced by its style name and whether it is selected, not its sample text.
            .clearAndSetSemantics {}
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFEDEBF0), Color(0xFF3A3640)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm)
                .clip(RoundedCornerShape(corner))
                .background(Brush.linearGradient(listOf(background, backgroundEnd)), alpha = alpha)
                .then(if (outline != null) Modifier.border(2.dp, outline, RoundedCornerShape(corner)) else Modifier)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.budget_budget), style = MaterialTheme.typography.labelMedium, color = onBackground, maxLines = 1)
            // The same synthetic figure the launcher's widget picker previews use, never real budget data.
            Text(stringResource(R.string.widget_sample_amount_4), style = MaterialTheme.typography.titleSmall, color = primary, maxLines = 1)
        }
    }
}
