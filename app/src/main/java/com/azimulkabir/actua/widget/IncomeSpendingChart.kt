package com.azimulkabir.actua.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import com.azimulkabir.actua.data.reports.IncomeSpending
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** The income bar and the spending bar of one month of the Income and spending chart. */
internal data class ChartPair(val income: ChartBar, val spending: ChartBar)

internal object IncomeSpendingChart {
    /**
     * The paired bars for [incomeCents] and [spentCents] (oldest first, same length) in a chart [width] x
     * [height] pixels. Both series share one scale, so the largest amount of either fills the height; an
     * amount of nothing, or less, is a stub of [minBar] so it still shows. A pair sits in an equal slot and
     * is at most [maxBarWidth] wide per bar, with [gap] between the two bars.
     */
    fun pairs(
        incomeCents: List<Long>, spentCents: List<Long>,
        width: Float, height: Float, minBar: Float, maxBarWidth: Float, gap: Float,
    ): List<ChartPair> {
        require(incomeCents.size == spentCents.size) { "One income and one spending amount per month" }
        if (incomeCents.isEmpty()) return emptyList()
        val slot = width / incomeCents.size
        val barWidth = min((slot * 0.78f - gap) / 2, maxBarWidth)
        val tallest = max(max(incomeCents.maxOf { it }, spentCents.maxOf { it }), 1L)
        fun barHeight(cents: Long) = if (cents <= 0) minBar else max(minBar, height * cents / tallest)
        return incomeCents.indices.map { index ->
            val left = slot * index + (slot - (2 * barWidth + gap)) / 2
            val incomeHeight = barHeight(incomeCents[index])
            val spendingHeight = barHeight(spentCents[index])
            ChartPair(
                ChartBar(left, height - incomeHeight, left + barWidth, height),
                ChartBar(left + barWidth + gap, height - spendingHeight, left + 2 * barWidth + gap, height),
            )
        }
    }

    /** Draws [series] as a bitmap [widthPx] x [heightPx], with a month label under each pair. Text follows the user's font size. */
    fun render(
        series: IncomeSpending,
        widthPx: Int,
        heightPx: Int,
        colors: ChartColors,
        metrics: android.util.DisplayMetrics,
        locale: Locale,
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
        val labelBand = text.textSize + dp(4f)
        val chartHeight = (heightPx - labelBand).coerceAtLeast(dp(8f))
        val pairs = pairs(
            series.months.map { it.incomeCents }, series.months.map { it.spentCents },
            widthPx.toFloat(), chartHeight, dp(2f), dp(16f), dp(2f),
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = dp(3f)
        val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)
        val slotWidth = widthPx.toFloat() / series.months.size
        series.months.zip(pairs).forEach { (month, pair) ->
            fun bar(bar: ChartBar, color: Int) {
                fill.color = color
                canvas.drawRoundRect(RectF(bar.left, bar.top, bar.right, bar.bottom), radius, radius, fill)
            }
            bar(pair.income, if (month.current) colors.highlight else colors.bar)
            bar(pair.spending, if (month.current) colors.second else colors.secondPast)
            // A slot too narrow for "Nov" shows its first letter.
            val label = month.month.format(monthFormat).let { if (text.measureText(it) > slotWidth) it.take(1) else it }
            canvas.drawText(label, (pair.income.left + pair.spending.right) / 2, heightPx - dp(3f), text)
        }
        return bitmap
    }
}
