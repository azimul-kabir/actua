package com.azimulkabir.actua.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
}

/**
 * The Widget style page (Settings → Display → Supporter extras): a preview of every style with the
 * chosen one marked. A tap restyles all home-screen widgets straight away.
 */
@Composable
internal fun WidgetStyleSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { DisplayPreferences(context) }
    var selected by remember { mutableStateOf(preferences.widgetStyle) }
    val styles = WidgetStyle.entries.filter { it != WidgetStyle.AccentTinted || WidgetStyles.accentTintedAvailable() }
    Column(
        // The tiles are one set of radio choices, so a screen reader announces them as a group.
        modifier.selectableGroup().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        styles.chunked(2).forEach { rowStyles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rowStyles.forEach { style ->
                    WidgetStyleTile(
                        style = style,
                        selected = style == selected,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selected = style
                            preferences.widgetStyle = style
                            WidgetUpdater.requestAll(context)
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
    }
}

@Composable
private fun WidgetStyleTile(style: WidgetStyle, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        }
    }
}

/** A small card in the style's colors, over a light-and-dark backdrop so translucency shows. */
@Composable
private fun WidgetPreview(style: WidgetStyle) {
    val context = LocalContext.current
    // Widgets follow the device theme, not the app's own appearance setting, so the accent they are tinted
    // with comes from the same place the widget takes it, not from the app's current theme.
    val night = isSystemInDarkTheme()
    val accent = if (style.followsAccent) {
        remember(night) { WidgetStyles.accentScheme(context, DisplayPreferences(context), night) }
    } else null
    val colors = style.colors(night, accent)
    val background = colors?.let { Color(it.background) } ?: colorResource(R.color.widget_background)
    val onBackground = colors?.let { Color(it.onBackground) } ?: colorResource(R.color.widget_on_background)
    val primary = colors?.let { Color(it.primary) } ?: colorResource(R.color.widget_primary)
    Box(
        Modifier
            // Decoration: a tile is announced by its style name and whether it is selected, not its sample text.
            .clearAndSetSemantics {}
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFEDEBF0), Color(0xFF3A3640)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm)
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.budget_budget), style = MaterialTheme.typography.labelMedium, color = onBackground, maxLines = 1)
            // The same synthetic figure the launcher's widget picker previews use, never real budget data.
            Text(stringResource(R.string.widget_sample_amount_4), style = MaterialTheme.typography.titleSmall, color = primary, maxLines = 1)
        }
    }
}
