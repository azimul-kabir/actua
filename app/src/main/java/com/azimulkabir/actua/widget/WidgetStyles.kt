package com.azimulkabir.actua.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.content.ContextCompat
import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.DisplayPreferences

/** Applies the chosen [WidgetStyle] to a widget's [RemoteViews]. Views a layout doesn't have are skipped by RemoteViews. */
internal object WidgetStyles {
    private val onBackgroundIds = intArrayOf(
        R.id.widget_title, R.id.widget_item_name, R.id.widget_schedule_title,
    )
    private val secondaryIds = intArrayOf(
        R.id.widget_empty, R.id.widget_month, R.id.widget_ready_label, R.id.widget_budgeted_label,
        R.id.widget_balance_label, R.id.widget_schedule_due, R.id.widget_updated,
        R.id.widget_single_name, R.id.widget_single_message, R.id.widget_trend_period, R.id.widget_trend_message,
        R.id.widget_is_period, R.id.widget_is_spent, R.id.widget_is_message,
    )
    private val primaryIds = intArrayOf(
        R.id.widget_ready_value, R.id.widget_budgeted_value, R.id.widget_balance_value,
        R.id.widget_item_amount, R.id.widget_schedule_amount, R.id.widget_single_amount,
        R.id.widget_trend_total, R.id.widget_is_left, R.id.widget_is_income,
    )
    private val buttonIds = intArrayOf(R.id.widget_expense, R.id.widget_income, R.id.widget_transfer)

    /** Tonal surfaces that are not buttons: the amount pills and the budget snapshot's tiles. */
    private val tileIds = intArrayOf(
        R.id.widget_item_amount, R.id.widget_schedule_amount, R.id.widget_tile_ready, R.id.widget_tile_budgeted, R.id.widget_tile_balance,
    )
    private val progressIds = intArrayOf(R.id.widget_item_progress, R.id.widget_single_progress)

    /** Whether [WidgetStyle.AccentTinted] can be offered: tinting the rounded background needs Android 12. */
    fun accentTintedAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * The layout to inflate for a widget: [plain] for the default style, which adds no overrides and so
     * keeps following the device theme through the layouts' own color resources, or its [styled] twin
     * for any other style. A launcher reapplies an update onto the view it already shows when the layout
     * id is unchanged, so anything an earlier look set and the new update leaves alone would linger.
     * Different ids for the default and styled looks make every switch between them a fresh inflate.
     */
    fun layout(context: Context, plain: Int, styled: Int): Int = if (activeStyle(context) == WidgetStyle.Default) plain else styled

    /** Changes whenever something that alters how a widget looks does, for deciding when previews are stale. */
    fun lookSignature(context: Context): String {
        val preferences = DisplayPreferences(context)
        return "${activeStyle(context)}:${preferences.accent}:${preferences.useDynamicColor}"
    }

    /** The colors a chart drawn into a bitmap uses, so it matches the widget's style and the device theme at this update. */
    fun chartColors(context: Context): ChartColors {
        val style = activeStyle(context)
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val accent = if (style == WidgetStyle.AccentTinted) accentScheme(context, DisplayPreferences(context), night) else null
        style.colors(night, accent)?.let { return chartColorsFor(it) }
        // The default style takes its colors from the color resources, which follow the device theme.
        fun color(id: Int) = ContextCompat.getColor(context, id)
        return chartColorsFor(
            WidgetStyleColors(
                background = color(R.color.widget_background), onBackground = color(R.color.widget_on_background),
                secondary = color(R.color.widget_secondary_text), primary = color(R.color.widget_primary),
                button = color(R.color.widget_button), buttonText = color(R.color.widget_primary),
                progressTrack = color(R.color.widget_button), tile = color(R.color.widget_button),
            ),
        )
    }

    private fun activeStyle(context: Context): WidgetStyle {
        if (!supporterPerksAvailable()) return WidgetStyle.Default
        val style = DisplayPreferences(context).widgetStyle
        return if (style == WidgetStyle.AccentTinted && !accentTintedAvailable()) WidgetStyle.Default else style
    }

    /**
     * Sets the style's colors on [views], which came from [layout]. Between two styled looks the same
     * layout is reapplied, so every color, background and tint is set explicitly (an accent tint is
     * cleared when another style is chosen). Does nothing for the default style.
     *
     * On Android 14+ every color is sent as a light and a dark value and the launcher picks the one for
     * the current theme, so a styled widget follows a device theme change by itself. Older releases take
     * one value, chosen for the theme at this update, so after a theme change the look stays consistent
     * (background and text were chosen together) until the widget's next refresh.
     */
    fun apply(context: Context, views: RemoteViews) {
        val style = activeStyle(context)
        if (style == WidgetStyle.Default) return
        val preferences = DisplayPreferences(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val accentLight = if (style == WidgetStyle.AccentTinted) accentScheme(context, preferences, night = false) else null
            val accentDark = if (style == WidgetStyle.AccentTinted) accentScheme(context, preferences, night = true) else null
            val light = style.colors(night = false, accent = accentLight) ?: return
            val dark = style.colors(night = true, accent = accentDark) ?: return
            applyThemeAware(views, light, dark)
            return
        }
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val accent = if (style == WidgetStyle.AccentTinted) accentScheme(context, preferences, night) else null
        val colors = style.colors(night, accent) ?: return
        applyFixed(views, style, night, colors)
    }

    /** Android 14+: each color is a (light, dark) pair resolved by the launcher, on top of the default drawables. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyThemeAware(views: RemoteViews, light: WidgetStyleColors, dark: WidgetStyleColors) {
        fun text(ids: IntArray, light: Int, dark: Int) = ids.forEach { views.setColorInt(it, "setTextColor", light, dark) }
        fun tint(id: Int, method: String, light: Int, dark: Int) =
            views.setColorStateList(id, method, ColorStateList.valueOf(light), ColorStateList.valueOf(dark))
        text(onBackgroundIds, light.onBackground, dark.onBackground)
        text(secondaryIds, light.secondary, dark.secondary)
        text(primaryIds, light.primary, dark.primary)
        text(buttonIds, light.buttonText, dark.buttonText)
        views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background)
        tint(R.id.widget_root, "setBackgroundTintList", light.background, dark.background)
        buttonIds.forEach {
            views.setInt(it, "setBackgroundResource", R.drawable.widget_button_background)
            tint(it, "setBackgroundTintList", light.button, dark.button)
        }
        tileIds.forEach {
            views.setInt(it, "setBackgroundResource", R.drawable.widget_button_background)
            tint(it, "setBackgroundTintList", light.tile, dark.tile)
        }
        progressIds.forEach {
            tint(it, "setProgressTintList", light.primary, dark.primary)
            tint(it, "setProgressBackgroundTintList", light.progressTrack, dark.progressTrack)
        }
    }

    /** Before Android 14: one value per color, chosen for the theme at this update. */
    private fun applyFixed(views: RemoteViews, style: WidgetStyle, night: Boolean, colors: WidgetStyleColors) {
        onBackgroundIds.forEach { views.setTextColor(it, colors.onBackground) }
        secondaryIds.forEach { views.setTextColor(it, colors.secondary) }
        primaryIds.forEach { views.setTextColor(it, colors.primary) }
        buttonIds.forEach { views.setTextColor(it, colors.buttonText) }
        val (background, button) = drawables(style, night)
        views.setInt(R.id.widget_root, "setBackgroundResource", background)
        buttonIds.forEach { views.setInt(it, "setBackgroundResource", button) }
        tileIds.forEach { views.setInt(it, "setBackgroundResource", button) }
        if (style == WidgetStyle.AccentTinted) {
            tint(views, R.id.widget_root, "setBackgroundTintList", colors.background)
            buttonIds.forEach { tint(views, it, "setBackgroundTintList", colors.button) }
            tileIds.forEach { tint(views, it, "setBackgroundTintList", colors.tile) }
        } else {
            clearTint(views, R.id.widget_root, "setBackgroundTintList")
            buttonIds.forEach { clearTint(views, it, "setBackgroundTintList") }
            tileIds.forEach { clearTint(views, it, "setBackgroundTintList") }
        }
        progressIds.forEach {
            tint(views, it, "setProgressTintList", colors.primary)
            tint(views, it, "setProgressBackgroundTintList", colors.progressTrack)
        }
    }

    /**
     * Colors a category row's progress bar by how spent it is: the usual color, amber from 85% spent and
     * red once overspent. Only for the default style on Android 12+, where a tint can be passed; a
     * styled widget keeps its own one-color bars.
     */
    fun applyProgressStatus(context: Context, views: RemoteViews, progressId: Int, status: ProgressStatus) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activeStyle(context) != WidgetStyle.Default) return
        val color = when (status) {
            ProgressStatus.Normal -> R.color.widget_primary
            ProgressStatus.Warning -> R.color.widget_status_warning
            ProgressStatus.Overspent -> R.color.widget_status_danger
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // A color resource is resolved by the launcher, so it follows a theme change by itself.
            views.setColorStateList(progressId, "setProgressTintList", color)
        } else {
            tint(views, progressId, "setProgressTintList", ContextCompat.getColor(context, color))
        }
    }

    private fun drawables(style: WidgetStyle, night: Boolean): Pair<Int, Int> = when (style) {
        WidgetStyle.Translucent ->
            if (night) R.drawable.widget_background_translucent_dark to R.drawable.widget_button_translucent_dark
            else R.drawable.widget_background_translucent_light to R.drawable.widget_button_translucent_light
        WidgetStyle.SolidDark -> R.drawable.widget_background_solid_dark to R.drawable.widget_button_solid_dark
        WidgetStyle.SolidLight -> R.drawable.widget_background_solid_light to R.drawable.widget_button_solid_light
        WidgetStyle.Default, WidgetStyle.AccentTinted -> R.drawable.widget_background to R.drawable.widget_button_background
    }

    /** RemoteViews can only pass a ColorStateList on Android 12+; older releases keep the layout's own tint. */
    private fun clearTint(views: RemoteViews, viewId: Int, method: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setColorStateList(viewId, method, null)
    }

    private fun tint(views: RemoteViews, viewId: Int, method: String, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(viewId, method, ColorStateList.valueOf(color))
        }
    }

    private fun accentScheme(context: Context, preferences: DisplayPreferences, night: Boolean): ColorScheme =
        if (preferences.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            preferences.accent.colorScheme(night)
        }
}

/** How far a category row's budget is used, for [WidgetStyles.applyProgressStatus]. */
internal enum class ProgressStatus {
    Normal, Warning, Overspent;

    companion object {
        /** [percentSpent] is spent over budgeted, 0..100; [overspent] means the category's balance is below zero. */
        fun of(percentSpent: Int, overspent: Boolean): ProgressStatus = when {
            overspent -> Overspent
            percentSpent >= 85 -> Warning
            else -> Normal
        }
    }
}
