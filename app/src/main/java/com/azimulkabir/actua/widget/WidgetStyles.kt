package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.preferences.DisplayPreferences

/** Applies the chosen [WidgetStyle] to a widget's [RemoteViews]. Views a layout doesn't have are skipped by RemoteViews. */
internal object WidgetStyles {
    private val onBackgroundIds = intArrayOf(
        R.id.widget_title, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4,
        R.id.widget_schedule_title,
    )
    private val secondaryIds = intArrayOf(
        R.id.widget_empty, R.id.widget_month, R.id.widget_ready_label, R.id.widget_budgeted_label,
        R.id.widget_balance_label, R.id.widget_schedule_due,
    )
    private val primaryIds = intArrayOf(
        R.id.widget_ready_value, R.id.widget_budgeted_value, R.id.widget_balance_value,
        R.id.widget_amount_1, R.id.widget_amount_2, R.id.widget_amount_3, R.id.widget_amount_4,
        R.id.widget_schedule_amount,
    )
    private const val STATE = "widget_style_state"
    private val buttonIds = intArrayOf(R.id.widget_expense, R.id.widget_income, R.id.widget_transfer)
    private val progressIds = intArrayOf(
        R.id.widget_progress_1, R.id.widget_progress_2, R.id.widget_progress_3, R.id.widget_progress_4,
    )

    /** Whether [WidgetStyle.AccentTinted] can be offered: tinting the rounded background needs Android 12. */
    fun accentTintedAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Pushes [views] to widget [widgetId] in the chosen style. The default style adds nothing, so it
     * keeps following the device theme through the layouts' own color resources. A styled widget gets
     * every color, background and tint set explicitly, because the launcher reapplies an update with
     * the same layout onto the views it already shows and anything left unset would stay (an accent
     * tint under a translucent background, say). Going back to Default would leave exactly such
     * leftovers, so a blank layout is pushed first to make the launcher inflate the widget afresh.
     */
    fun update(context: Context, manager: AppWidgetManager, widgetId: Int, views: RemoteViews) {
        val styled = apply(context, views)
        val state = context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        val key = "styled_$widgetId"
        if (!styled && state.getBoolean(key, false)) {
            manager.updateAppWidget(widgetId, RemoteViews(context.packageName, R.layout.widget_style_reset))
        }
        manager.updateAppWidget(widgetId, views)
        state.edit().apply { if (styled) putBoolean(key, true) else remove(key) }.apply()
    }

    /** Sets the style's colors on [views]; returns false, leaving [views] untouched, for the default style. */
    fun apply(context: Context, views: RemoteViews): Boolean {
        if (!supporterPerksAvailable()) return false
        val preferences = DisplayPreferences(context)
        val style = preferences.widgetStyle.takeUnless { it == WidgetStyle.AccentTinted && !accentTintedAvailable() }
            ?: WidgetStyle.Default
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val accent = if (style == WidgetStyle.AccentTinted) accentScheme(context, preferences, night) else null
        // Every color, tint and drawable below is fixed at this update, so they always agree with each
        // other (a theme change leaves a consistent look until the widget's next refresh).
        val colors = style.colors(night, accent) ?: return false

        onBackgroundIds.forEach { views.setTextColor(it, colors.onBackground) }
        secondaryIds.forEach { views.setTextColor(it, colors.secondary) }
        primaryIds.forEach { views.setTextColor(it, colors.primary) }
        buttonIds.forEach { views.setTextColor(it, colors.buttonText) }
        val (background, button) = drawables(style, night)
        views.setInt(R.id.widget_root, "setBackgroundResource", background)
        buttonIds.forEach { views.setInt(it, "setBackgroundResource", button) }
        if (style == WidgetStyle.AccentTinted) {
            tint(views, R.id.widget_root, "setBackgroundTintList", colors.background)
            buttonIds.forEach { tint(views, it, "setBackgroundTintList", colors.button) }
        }
        progressIds.forEach {
            tint(views, it, "setProgressTintList", colors.primary)
            tint(views, it, "setProgressBackgroundTintList", colors.progressTrack)
        }
        return true
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
