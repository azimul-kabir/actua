package com.azimulkabir.actua.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import com.azimulkabir.actua.data.reports.SpendingTrend
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** One bar of the Spending trend chart, in pixels, with its left edge at [left] and its base at [bottom]. */
internal data class ChartBar(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
}

/** The colors the chart is drawn in, taken from the widget's look. */
internal class ChartColors(
    /** The current month's bar, in the widget's accent. */
    val highlight: Int,
    /** The other months' bars. */
    val bar: Int,
    /** Month and amount labels. */
    val label: Int,
    /** The spending bar beside an income bar, for this month, in a tone that tells it from the accent. */
    val second: Int = label,
    /** The spending bar for the other months. */
    val secondPast: Int = label,
)

/** The chart's colors for a widget style: its accent for this month, a softer tone of it for the others. */
internal fun chartColorsFor(colors: WidgetStyleColors): ChartColors {
    val backdrop = colors.background or (0xFF shl 24)
    // The spending bar beside an income bar is a text tone that sits as far from the accent as it can while
    // staying a clear shape on the background, so the two read as different series in every style, light and
    // dark. The dimmed secondary tone is there for dark styles, where every full text tone is as light as the accent.
    val candidates = listOf(
        colors.onBackground, colors.secondary,
        blendArgb(colors.secondary, backdrop, 0.6f), blendArgb(colors.secondary, backdrop, 0.45f),
    ).filter { contrast(it, backdrop) >= MIN_SHAPE_CONTRAST }
    var second = candidates.maxByOrNull { contrast(it, colors.primary) } ?: colors.secondary
    if (contrast(second, colors.primary) < MIN_SERIES_CONTRAST) {
        // A soft secondary text color sits close to the accent, and its dimmed tones fall below the shape contrast, so
        // the candidates above can all be too near the accent. Also try the main text color dimmed toward the backdrop.
        val dimmed = listOf(0.8f, 0.7f, 0.6f, 0.5f)
            .map { blendArgb(colors.onBackground, backdrop, it) }
            .filter { contrast(it, backdrop) >= MIN_SHAPE_CONTRAST }
        (candidates + dimmed).maxByOrNull { contrast(it, colors.primary) }?.let { second = it }
    }
    val past = blendArgb(second, backdrop, PAST_BAR_STRENGTH).takeIf { contrast(it, backdrop) >= MIN_SHAPE_CONTRAST } ?: second
    return ChartColors(
        highlight = colors.primary,
        bar = blendArgb(colors.primary, backdrop, PAST_BAR_STRENGTH),
        label = colors.secondary,
        second = second,
        secondPast = past,
    )
}

/** The contrast a bar needs against the background to stay a clear shape (WCAG's 3:1 for graphics). */
private const val MIN_SHAPE_CONTRAST = 3.0

/** The contrast the spending bars need against the income bars (the accent) to read as another series. */
private const val MIN_SERIES_CONTRAST = 1.3

internal fun luminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val v = ((argb shr shift) and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

internal fun contrast(a: Int, b: Int): Double {
    val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (light + 0.05) / (dark + 0.05)
}

/** How much of the accent the past months' bars keep: enough to stay a clear shape on the background. */
internal const val PAST_BAR_STRENGTH = 0.7f

internal object SpendingChart {
    /**
     * The bars for [spentCents] (oldest first) in a chart [width] x [height] pixels. The tallest month fills
     * the height and the others are in proportion; a month with nothing spent, or net refunds, is a short
     * stub of [minBar] so it still shows as a bar. Bars sit in equal slots and are at most [maxBarWidth] wide.
     */
    fun bars(spentCents: List<Long>, width: Float, height: Float, minBar: Float, maxBarWidth: Float): List<ChartBar> {
        if (spentCents.isEmpty()) return emptyList()
        val slot = width / spentCents.size
        val barWidth = min(slot * 0.62f, maxBarWidth)
        val tallest = max(spentCents.maxOf { it }, 1L)
        return spentCents.mapIndexed { index, cents ->
            val barHeight = if (cents <= 0) minBar else max(minBar, height * cents / tallest)
            val left = slot * index + (slot - barWidth) / 2
            ChartBar(left, height - barHeight, left + barWidth, height)
        }
    }

    /**
     * Draws [trend] as a bitmap [widthPx] x [heightPx], with a month label under each bar and, when
     * [amountLabel] is set, this month's amount above its bar. Text sizes follow the user's font size.
     */
    fun render(
        trend: SpendingTrend,
        widthPx: Int,
        heightPx: Int,
        colors: ChartColors,
        metrics: android.util.DisplayMetrics,
        locale: Locale,
        amountLabel: String?,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
        fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.label
            textSize = sp(10f)
            textAlign = Paint.Align.CENTER
        }
        val amountPaint = Paint(text).apply {
            color = colors.highlight
            typeface = Typeface.DEFAULT_BOLD
            textSize = sp(11f)
        }
        val labelBand = text.textSize + dp(4f)
        val amountBand = if (amountLabel != null) amountPaint.textSize + dp(4f) else 0f
        val chartTop = amountBand
        val chartHeight = (heightPx - labelBand - chartTop).coerceAtLeast(dp(8f))

        val bars = bars(trend.months.map { it.spentCents }, widthPx.toFloat(), chartHeight, dp(2f), dp(30f))
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = dp(4f)
        val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)
        trend.months.zip(bars).forEach { (month, bar) ->
            fill.color = if (month.current) colors.highlight else colors.bar
            val rect = RectF(bar.left, chartTop + bar.top, bar.right, chartTop + bar.bottom)
            canvas.drawRoundRect(rect, radius, radius, fill)
            // A slot too narrow for "Nov" shows its first letter.
            val slotWidth = widthPx.toFloat() / trend.months.size
            val label = month.month.format(monthFormat).let { if (text.measureText(it) > slotWidth) it.take(1) else it }
            canvas.drawText(label, bar.centerX, heightPx - dp(3f), text)
        }
        if (amountLabel != null) {
            val current = bars.last()
            val half = amountPaint.measureText(amountLabel) / 2
            val x = current.centerX.coerceIn(half, widthPx - half)
            canvas.drawText(amountLabel, x, chartTop + current.top - dp(3f), amountPaint)
        }
        return bitmap
    }
}
