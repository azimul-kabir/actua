package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.BlendMode
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.content.ContextCompat
import com.azimulkabir.actua.ui.theme.CatppuccinStatusColors
import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.SupporterPerks

/** Applies the chosen [WidgetStyle] to a widget's [RemoteViews]. Views a layout doesn't have are skipped by RemoteViews. */
internal object WidgetStyles {
    internal val onBackgroundIds = intArrayOf(
        R.id.widget_title, R.id.widget_item_name, R.id.widget_schedule_title,
    )
    internal val secondaryIds = intArrayOf(
        R.id.widget_empty, R.id.widget_month, R.id.widget_ready_label, R.id.widget_budgeted_label,
        R.id.widget_balance_label, R.id.widget_schedule_due, R.id.widget_updated,
        R.id.widget_single_name, R.id.widget_single_message, R.id.widget_trend_period, R.id.widget_trend_message,
        R.id.widget_is_period, R.id.widget_is_spent, R.id.widget_is_message,
        R.id.widget_report_comparison,
    )
    internal val primaryIds = intArrayOf(
        R.id.widget_ready_value, R.id.widget_budgeted_value, R.id.widget_balance_value,
        R.id.widget_item_amount, R.id.widget_item_amount_pill, R.id.widget_schedule_amount, R.id.widget_single_amount,
        R.id.widget_trend_total, R.id.widget_is_left, R.id.widget_is_income,
    )
    internal val buttonIds = intArrayOf(R.id.widget_expense, R.id.widget_income, R.id.widget_transfer)

    /** Tonal surfaces that are not buttons: the amount pills and the budget snapshot's tiles. */
    internal val tileIds = intArrayOf(
        R.id.widget_item_amount_pill, R.id.widget_schedule_amount, R.id.widget_tile_ready, R.id.widget_tile_budgeted, R.id.widget_tile_balance,
    )
    internal val progressIds = intArrayOf(R.id.widget_item_progress, R.id.widget_single_progress)

    /** Whether [WidgetStyle.AccentTinted] can be offered: tinting the rounded background needs Android 12. */
    fun accentTintedAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * The layout to inflate for a widget: [plain] for the default style, which adds no overrides and so
     * keeps following the device theme through the layouts' own color resources, or its [styled] twin
     * for any other style. A launcher reapplies an update onto the view it already shows when the layout
     * id is unchanged, so anything an earlier look set and the new update leaves alone would linger.
     * Different ids for the default and styled looks make every switch between them a fresh inflate.
     */
    fun layout(context: Context, plain: Int, styled: Int, widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): Int =
        if (activeLook(context, widgetId).style == WidgetStyle.Default) plain else styled

    /** Changes whenever something that alters how a widget looks does, for deciding when previews are stale. */
    fun lookSignature(context: Context): String {
        val preferences = DisplayPreferences(context)
        return "${activeLook(context, AppWidgetManager.INVALID_APPWIDGET_ID)}:${preferences.accent}:${preferences.customAccentColor}:${preferences.catppuccinTheme()}:${preferences.useDynamicColor}"
    }

    /** The colors a chart drawn into a bitmap uses, so it matches the widget's style and the device theme at this update. */
    fun chartColors(context: Context, widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): ChartColors {
        val style = activeLook(context, widgetId).style
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        styleColors(context, style, night)?.let { return chartColorsFor(it) }
        // The default style takes its colors from the color resources, which follow the device theme.
        fun color(id: Int) = ContextCompat.getColor(context, id)
        return chartColorsFor(
            WidgetStyleColors(
                background = color(R.color.widget_background), onBackground = color(R.color.widget_on_background),
                secondary = color(R.color.widget_secondary_text), primary = color(R.color.widget_primary),
                button = color(R.color.widget_button), buttonText = color(R.color.widget_primary),
                progressTrack = color(R.color.widget_button), tile = color(R.color.widget_button),
                warning = color(R.color.widget_status_warning), danger = color(R.color.widget_status_danger),
            ),
        )
    }

    /** The look [widgetId] is drawn in: its own on top of the one for all widgets, for a supporter only. */
    internal fun activeLook(context: Context, widgetId: Int): WidgetLook {
        val preferences = DisplayPreferences(context)
        return effectiveWidgetLook(
            perksAvailable = supporterPerksAvailable(),
            isSupporter = SupporterPerks(context).isSupporter,
            global = WidgetLook(preferences.widgetStyle, preferences.widgetCornerRadius, preferences.widgetOpacity),
            override = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) null else WidgetLookPreferences(context).override(widgetId),
            tintAvailable = accentTintedAvailable(),
        )
    }

    /**
     * The colors [style] has for the device theme [night], drawn from the app's accent where the style follows it. Gradient
     * accent always takes the light scheme's saturated primary.
     */
    internal fun styleColors(context: Context, style: WidgetStyle, night: Boolean): WidgetStyleColors? {
        if (!style.followsAccent) return null
        val preferences = DisplayPreferences(context)
        val scheme = accentScheme(context, preferences, night = night && style != WidgetStyle.GradientAccent)
        val status = if (style == WidgetStyle.AccentTinted) accentStatus(context, preferences, night) else null
        return style.colors(night, scheme, status)
    }

    /** The lowest opacity [style] can be drawn at and stay readable, in both device themes. */
    internal fun minimumOpacity(context: Context, style: WidgetStyle): Int =
        minimumOpacity(styleColors(context, style, night = false), styleColors(context, style, night = true))

    /**
     * Sets the style's colors on [views], which came from [layout]. Between two styled looks the same
     * layout is reapplied, so every color, background and tint is set explicitly (an accent tint is
     * cleared when another style is chosen). Does nothing for the default style.
     *
     * On Android 14+ every color is sent as a light and a dark value and the launcher picks the one for
     * the current theme, so a styled widget follows a device theme change by itself. Older releases take
     * one value, chosen for the theme at this update, so after a theme change the look stays consistent
     * (background and text were chosen together) until the widget's next refresh.
     *
     * [progressStatus] is how spent the widget's category is, for its progress bar's amber and red states; it
     * has to be passed here because this sets the bar's color, so a status applied before would be overwritten.
     */
    fun apply(
        context: Context,
        views: RemoteViews,
        progressStatus: ProgressStatus = ProgressStatus.Normal,
        widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ) {
        val look = activeLook(context, widgetId)
        val style = look.style
        if (style == WidgetStyle.Default) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val light = styleColors(context, style, night = false) ?: return
            val dark = styleColors(context, style, night = true) ?: return
            applyThemeAware(views, look, light, dark, progressStatus)
            return
        }
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val colors = styleColors(context, style, night) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyTinted(views, look, colors, progressStatus)
        } else {
            applyFixed(views, style, night, colors, progressStatus)
        }
    }

    /**
     * The root background for [look]: a white shape the style's color tints by multiplying ([backgroundTint]), the
     * Gradient accent's white-to-grey one, or the Outline style's own scrim and border.
     */
    private fun backgroundFor(look: WidgetLook): Int = when (look.style) {
        WidgetStyle.GradientAccent -> look.radius.gradient
        WidgetStyle.Outline -> look.radius.outline
        else -> look.radius.shape
    }

    /**
     * What [backgroundFor] is multiplied by: the style's background with its alpha scaled to the look's opacity, which is
     * never below what keeps the text readable. The Outline drawable has its own colors, so only its alpha is set.
     */
    internal fun backgroundTint(look: WidgetLook, colors: WidgetStyleColors): Int {
        val opacity = look.opacity.coerceAtLeast(minimumOpacity(colors))
        val base = if (look.style == WidgetStyle.Outline) 0xFFFFFFFF.toInt() else colors.background
        val alpha = (((base ushr 24) and 0xFF) * opacity / 100f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (base and 0xFFFFFF)
    }

    /** Android 14+: each color is a (light, dark) pair resolved by the launcher. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyThemeAware(views: RemoteViews, look: WidgetLook, light: WidgetStyleColors, dark: WidgetStyleColors, status: ProgressStatus) {
        fun text(ids: IntArray, light: Int, dark: Int) = ids.forEach { views.setColorInt(it, "setTextColor", light, dark) }
        fun tint(id: Int, method: String, light: Int, dark: Int) =
            views.setColorStateList(id, method, ColorStateList.valueOf(light), ColorStateList.valueOf(dark))
        text(onBackgroundIds, light.onBackground, dark.onBackground)
        text(secondaryIds, light.secondary, dark.secondary)
        text(primaryIds, light.primary, dark.primary)
        text(buttonIds, light.buttonText, dark.buttonText)
        views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(look))
        views.setBlendMode(R.id.widget_root, "setBackgroundTintBlendMode", BlendMode.MULTIPLY)
        tint(R.id.widget_root, "setBackgroundTintList", backgroundTint(look, light), backgroundTint(look, dark))
        buttonIds.forEach {
            views.setInt(it, "setBackgroundResource", R.drawable.widget_button_background)
            tint(it, "setBackgroundTintList", light.button, dark.button)
        }
        tileIds.forEach {
            views.setInt(it, "setBackgroundResource", R.drawable.widget_button_background)
            tint(it, "setBackgroundTintList", light.tile, dark.tile)
        }
        progressIds.forEach {
            tint(it, "setProgressTintList", light.progressFill(status), dark.progressFill(status))
            tint(it, "setProgressBackgroundTintList", light.progressTrack, dark.progressTrack)
        }
    }

    /** Android 12 and 13: one value per color, chosen for the theme at this update, on a tinted background. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun applyTinted(views: RemoteViews, look: WidgetLook, colors: WidgetStyleColors, status: ProgressStatus) {
        onBackgroundIds.forEach { views.setTextColor(it, colors.onBackground) }
        secondaryIds.forEach { views.setTextColor(it, colors.secondary) }
        primaryIds.forEach { views.setTextColor(it, colors.primary) }
        buttonIds.forEach { views.setTextColor(it, colors.buttonText) }
        views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(look))
        views.setBlendMode(R.id.widget_root, "setBackgroundTintBlendMode", BlendMode.MULTIPLY)
        tint(views, R.id.widget_root, "setBackgroundTintList", backgroundTint(look, colors))
        buttonIds.forEach {
            views.setInt(it, "setBackgroundResource", R.drawable.widget_button_background)
            tint(views, it, "setBackgroundTintList", colors.button)
        }
        tileIds.forEach {
            views.setInt(it, "setBackgroundResource", R.drawable.widget_button_background)
            tint(views, it, "setBackgroundTintList", colors.tile)
        }
        progressIds.forEach {
            tint(views, it, "setProgressTintList", colors.progressFill(status))
            tint(views, it, "setProgressBackgroundTintList", colors.progressTrack)
        }
    }

    /** Before Android 12: one value per color, chosen for the theme at this update, on fixed drawables. */
    private fun applyFixed(views: RemoteViews, style: WidgetStyle, night: Boolean, colors: WidgetStyleColors, status: ProgressStatus) {
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
            tint(views, it, "setProgressTintList", colors.progressFill(status))
            tint(views, it, "setProgressBackgroundTintList", colors.progressTrack)
        }
    }

    /**
     * Colors a category row's progress bar by how spent it is: the usual color, amber from 85% spent and
     * red once overspent. Only for the default style on Android 12+, where a tint can be passed. A styled
     * widget gets the same states from [apply], in tones chosen for its style.
     */
    fun applyProgressStatus(
        context: Context,
        views: RemoteViews,
        progressId: Int,
        status: ProgressStatus,
        widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activeLook(context, widgetId).style != WidgetStyle.Default) return
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
        // The styles that need a tint never reach here: before Android 12 they are drawn as Default.
        WidgetStyle.Default, WidgetStyle.AccentTinted, WidgetStyle.GradientAccent, WidgetStyle.Outline, WidgetStyle.Midnight ->
            R.drawable.widget_background to R.drawable.widget_button_background
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

    /**
     * The scheme an [WidgetStyle.AccentTinted] widget is drawn from, for the device theme [night]: Material You
     * when it is on (Android 12+), otherwise the chosen palette. The Widget style page previews with this too,
     * so the tile and the widget cannot disagree.
     */
    /**
     * The Catppuccin theme's warning and overspent colors for [night], when a Catppuccin theme is what
     * [accentScheme] draws from (so not while Material You is on), else null.
     */
    internal fun accentStatus(context: Context, preferences: DisplayPreferences, night: Boolean): CatppuccinStatusColors? =
        if (preferences.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) null
        else preferences.catppuccinTheme()?.statusColors(night)

    internal fun accentScheme(context: Context, preferences: DisplayPreferences, night: Boolean): ColorScheme =
        if (preferences.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            preferences.appColorScheme(night)
        }
}

/**
 * The widget style that is drawn: the [stored] choice only for a supporter on the Play build, and not
 * Accent-tinted where it cannot be tinted. The cached supporter status is what gates it, so a stored style
 * of someone who is not (or no longer) a supporter is never drawn, whether or not its reset has run.
 */
internal fun effectiveWidgetStyle(
    perksAvailable: Boolean,
    isSupporter: Boolean,
    stored: WidgetStyle,
    accentTintedAvailable: Boolean,
): WidgetStyle = when {
    !perksAvailable || !isSupporter -> WidgetStyle.Default
    stored == WidgetStyle.AccentTinted && !accentTintedAvailable -> WidgetStyle.Default
    else -> stored
}

/** How far a category row's budget is used, for [WidgetStyles.applyProgressStatus]. */
internal enum class ProgressStatus {
    Normal, Warning, Overspent;

    companion object {
        /**
         * [percentSpent] is spent over budgeted, 0..100; [overspent] means the category's balance is below zero.
         * Amber warns that the budget is nearly used up; a category spent exactly to its budget is on plan, so
         * it keeps the normal color.
         */
        fun of(percentSpent: Int, overspent: Boolean): ProgressStatus = when {
            overspent -> Overspent
            percentSpent in 85..99 -> Warning
            else -> Normal
        }
    }
}
