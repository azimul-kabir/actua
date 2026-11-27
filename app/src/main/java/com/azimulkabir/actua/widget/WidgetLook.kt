package com.azimulkabir.actua.widget

import android.content.Context
import androidx.annotation.DrawableRes
import com.azimulkabir.actua.R

/**
 * A widget's corner radius (a supporter extra, Android 12+). RemoteViews can't set a radius, so each step is its own
 * shape drawable: [shape] is a white fill the style's color tints, [gradient] the Gradient accent's white-to-grey fill,
 * and [outline] the stroke drawn over the Outline style's scrim.
 */
enum class WidgetCornerRadius(@DrawableRes val shape: Int, @DrawableRes val gradient: Int, @DrawableRes val outline: Int) {
    /** The launcher's own widget radius. */
    System(R.drawable.widget_shape_system, R.drawable.widget_gradient_system, R.drawable.widget_outline_system),
    Small(R.drawable.widget_shape_small, R.drawable.widget_gradient_small, R.drawable.widget_outline_small),
    Medium(R.drawable.widget_shape_medium, R.drawable.widget_gradient_medium, R.drawable.widget_outline_medium),
    Large(R.drawable.widget_shape_large, R.drawable.widget_gradient_large, R.drawable.widget_outline_large),
    ;

    companion object {
        fun fromName(name: String?): WidgetCornerRadius? = entries.firstOrNull { it.name == name }
    }
}

/** How a widget is drawn: its style, corner radius and background opacity (percent). */
data class WidgetLook(val style: WidgetStyle, val radius: WidgetCornerRadius, val opacity: Int) {
    companion object {
        const val FULL_OPACITY = 100

        /** The most see-through a supporter can ask for. A style may stop sooner, see [minimumOpacity]. */
        const val LOWEST_OPACITY = 40
        const val OPACITY_STEP = 5
        val Default = WidgetLook(WidgetStyle.Default, WidgetCornerRadius.System, FULL_OPACITY)
    }
}

/** One widget's own look. A null field follows the look chosen for all widgets. */
data class WidgetLookOverride(val style: WidgetStyle? = null, val radius: WidgetCornerRadius? = null, val opacity: Int? = null) {
    val isEmpty: Boolean get() = style == null && radius == null && opacity == null
}

/**
 * The look that is drawn. Only a supporter on a build with supporter extras gets anything but [WidgetLook.Default],
 * whether or not the reset has run, the same way [effectiveWidgetStyle] gates the style. [override] is the widget's own
 * look on top of the [global] one. Before Android 12 ([tintAvailable] false) a widget can't be tinted, so the styles
 * that need it fall back to Default and the radius and opacity stay as they were.
 */
internal fun effectiveWidgetLook(
    perksAvailable: Boolean,
    isSupporter: Boolean,
    global: WidgetLook,
    override: WidgetLookOverride?,
    tintAvailable: Boolean,
): WidgetLook {
    if (!perksAvailable || !isSupporter) return WidgetLook.Default
    val style = override?.style ?: global.style
    if (!tintAvailable) return WidgetLook(if (style.needsAndroid12) WidgetStyle.Default else style, WidgetCornerRadius.System, WidgetLook.FULL_OPACITY)
    val opacity = (override?.opacity ?: global.opacity).coerceIn(WidgetLook.LOWEST_OPACITY, WidgetLook.FULL_OPACITY)
    return WidgetLook(style, override?.radius ?: global.radius, opacity)
}

/** Wallpapers a see-through widget has to read on: white, mid-grey and black, the extremes and the hardest middle. */
private val Wallpapers = intArrayOf(0xFFFFFFFF.toInt(), 0xFF777777.toInt(), 0xFF000000.toInt())

/** [top] at its own alpha times [opacity] (percent) over the opaque [bottom]. */
internal fun overWithOpacity(top: Int, opacity: Int, bottom: Int): Int {
    val alpha = ((top ushr 24) and 0xFF) / 255f * opacity / 100f
    return blendArgb(top or (0xFF shl 24), bottom, alpha)
}

/** Whether every text color of [colors] reads at 4.5:1 with the background at [opacity] over every wallpaper. */
internal fun readableAt(colors: WidgetStyleColors, opacity: Int): Boolean = Wallpapers.all { wallpaper ->
    listOf(colors.background, colors.backgroundEnd).all { surface ->
        val background = overWithOpacity(surface, opacity, wallpaper)
        val tile = blendArgb(colors.tile or (0xFF shl 24), background, ((colors.tile ushr 24) and 0xFF) / 255f)
        val button = blendArgb(colors.button or (0xFF shl 24), background, ((colors.button ushr 24) and 0xFF) / 255f)
        listOf(colors.onBackground, colors.secondary, colors.primary).all { contrast(it, background) >= 4.5 } &&
            listOf(colors.primary, colors.secondary).all { contrast(it, tile) >= 4.5 } &&
            contrast(colors.buttonText, button) >= 4.5
    }
}

/**
 * The lowest opacity, in [WidgetLook.OPACITY_STEP]s from [WidgetLook.LOWEST_OPACITY], at which [colors] stays readable on
 * any wallpaper. Text is never faded, so this is where each style stops: a dark style over a white wallpaper turns grey
 * long before 40%.
 */
internal fun minimumOpacity(vararg colors: WidgetStyleColors?): Int {
    val known = colors.filterNotNull()
    return (WidgetLook.LOWEST_OPACITY..WidgetLook.FULL_OPACITY step WidgetLook.OPACITY_STEP)
        .firstOrNull { opacity -> known.all { readableAt(it, opacity) } } ?: WidgetLook.FULL_OPACITY
}

/** Each widget's own look, kept on the device only, by widget id. Removed with the widget. */
class WidgetLookPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun override(widgetId: Int): WidgetLookOverride = WidgetLookOverride(
        style = values.getString("style_$widgetId", null)?.let { name -> WidgetStyle.entries.firstOrNull { it.name == name } },
        radius = WidgetCornerRadius.fromName(values.getString("radius_$widgetId", null)),
        opacity = if (values.contains("opacity_$widgetId")) values.getInt("opacity_$widgetId", WidgetLook.FULL_OPACITY) else null,
    )

    fun save(widgetId: Int, look: WidgetLookOverride) {
        values.edit().apply {
            if (look.style == null) remove("style_$widgetId") else putString("style_$widgetId", look.style.name)
            if (look.radius == null) remove("radius_$widgetId") else putString("radius_$widgetId", look.radius.name)
            if (look.opacity == null) remove("opacity_$widgetId") else putInt("opacity_$widgetId", look.opacity)
        }.apply()
    }

    fun remove(widgetIds: IntArray) {
        values.edit().apply { widgetIds.forEach { remove("style_$it"); remove("radius_$it"); remove("opacity_$it") } }.apply()
    }

    /** Forgets every widget's own look, for the supporter-status reset. */
    fun clear() {
        values.edit().clear().apply()
    }

    private companion object { const val NAME = "widget_look_preferences" }
}
