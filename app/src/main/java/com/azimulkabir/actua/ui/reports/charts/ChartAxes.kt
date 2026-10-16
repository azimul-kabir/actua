package com.azimulkabir.actua.ui.reports.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.NumberDisplay
import com.azimulkabir.actua.ui.components.currencyInputPrefix
import com.azimulkabir.actua.ui.components.formatWholeNumber
import com.azimulkabir.actua.ui.components.minorUnitsPerWhole
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Calculates 3 to 5 "nice" gridline values spanning [min] to [max].
 * Handles negative values, zero-only data, single data points, and very large amounts.
 */
fun calculateNiceGridlines(min: Long, max: Long, targetSteps: Int = 4): List<Long> {
    if (min == max) return listOf(min)
    if (min > max) return calculateNiceGridlines(max, min, targetSteps)

    val range = (max - min).toDouble()
    val roughStep = range / targetSteps.coerceIn(2, 6)
    val exponent = kotlin.math.floor(kotlin.math.log10(roughStep)).toInt()

    val candidateMultipliers = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
    val candidateSteps = buildList {
        for (exp in (exponent - 1)..(exponent + 2)) {
            val p = 10.0.pow(exp)
            for (m in candidateMultipliers) {
                val stepVal = (m * p).toLong()
                if (stepVal > 0L) add(stepVal)
            }
        }
    }.distinct()

    var bestStep = candidateSteps.firstOrNull() ?: 1L
    var bestCountDiff = Int.MAX_VALUE
    var bestStepsRange = Long.MAX_VALUE

    for (step in candidateSteps) {
        val start = floorToStep(min, step)
        val end = ceilToStep(max, step)
        if (end < start) continue
        val numSteps = (end - start) / step
        val lineCount = (numSteps + 1).toInt()

        val inTarget = lineCount in 3..5
        val diff = kotlin.math.abs(lineCount - 4)

        if (inTarget) {
            if (diff < bestCountDiff || (diff == bestCountDiff && (end - start) < bestStepsRange)) {
                bestStep = step
                bestCountDiff = diff
                bestStepsRange = end - start
            }
        }
    }

    if (bestCountDiff == Int.MAX_VALUE) {
        for (step in candidateSteps) {
            val start = floorToStep(min, step)
            val end = ceilToStep(max, step)
            val numSteps = (end - start) / step
            val lineCount = (numSteps + 1).toInt()
            if (lineCount < 2) continue
            val diff = kotlin.math.abs(lineCount - 4)
            if (diff < bestCountDiff) {
                bestStep = step
                bestCountDiff = diff
            }
        }
    }

    val start = floorToStep(min, bestStep)
    val end = ceilToStep(max, bestStep)
    val result = mutableListOf<Long>()
    var curr = start
    while (curr <= end) {
        result.add(curr)
        if (curr > Long.MAX_VALUE - bestStep) break
        curr += bestStep
    }
    return result
}

fun floorToStep(value: Long, step: Long): Long = Math.floorDiv(value, step) * step
fun ceilToStep(value: Long, step: Long): Long = -Math.floorDiv(-value, step) * step

/**
 * Formats a monetary amount in whole units without minor decimals, respecting the budget's
 * digit grouping, currency symbol, symbol position, and spacing.
 */
fun formatCompactMoney(
    cents: Long,
    hideDecimals: Boolean = true,
    currencySymbol: String = currencyInputPrefix(),
    symbolAfterAmount: Boolean = CurrencyDisplay.symbolAfterAmount,
    spaceBetweenAmountAndSymbol: Boolean = CurrencyDisplay.spaceBetweenAmountAndSymbol,
    numberFormat: String = NumberDisplay.format,
    decimalPlaces: Int = CurrencyDisplay.decimalPlaces,
): String {
    val magnitude = cents.absoluteValue
    val scale = minorUnitsPerWhole(decimalPlaces)
    val roundsToWhole = hideDecimals && decimalPlaces > 0
    val roundsUp = roundsToWhole && magnitude % scale * 2 >= scale
    val wholeUnits = magnitude / scale + (if (roundsUp) 1L else 0L)

    val sign = when {
        cents < 0 && !(roundsToWhole && wholeUnits == 0L) -> "−"
        else -> ""
    }

    val whole = formatWholeNumber(wholeUnits, numberFormat)
    val decimalSeparator = when (numberFormat) {
        "1.234,56", "1 234,56" -> ","
        else -> "."
    }
    val decimals = if (hideDecimals || decimalPlaces == 0) "" else
        "$decimalSeparator${(magnitude % scale).toString().padStart(decimalPlaces, '0')}"

    val amountString = "$whole$decimals"
    if (currencySymbol.isEmpty()) return "$sign$amountString"

    val space = if (spaceBetweenAmountAndSymbol) "\u202F" else ""
    val withSymbol = if (symbolAfterAmount) "$amountString$space$currencySymbol" else "$currencySymbol$space$amountString"
    return "$sign$withSymbol"
}

/**
 * Thins month labels so they do not overlap within [availableWidthPx].
 * Returns the indices of the labels to be displayed.
 */
fun thinMonthLabels(
    labels: List<String>,
    availableWidthPx: Float,
    labelWidthPx: Float,
    minSpacingPx: Float = 8f,
): List<Int> {
    val n = labels.size
    if (n == 0) return emptyList()
    if (n == 1) return listOf(0)
    if (availableWidthPx <= 0f) return emptyList()

    val totalRequiredWidth = n * labelWidthPx + (n - 1) * minSpacingPx
    if (totalRequiredWidth <= availableWidthPx) {
        return labels.indices.toList()
    }

    val minGap = (labelWidthPx + minSpacingPx) * n / availableWidthPx
    val stepCandidates = listOf(1, 2, 3, 4, 6, 12, 24, 36, 48)
    val step = stepCandidates.firstOrNull { it >= minGap } ?: ceil(minGap).toInt().coerceAtLeast(1)

    val candidateIndices = (0 until n step step).toList()
    val candidateRequiredWidth = candidateIndices.size * labelWidthPx + (candidateIndices.size - 1) * minSpacingPx
    if (candidateRequiredWidth > availableWidthPx && candidateIndices.size > 1) {
        return listOf(0)
    }
    return candidateIndices
}

/**
 * Pre-measured right-axis value labels cached to prevent re-measuring on every frame.
 */
class ChartValueAxisMeasurements(
    val labels: List<Pair<Long, TextLayoutResult>>,
    val maxLabelWidth: Float,
    val maxLabelHeight: Float,
)

/**
 * Measures right-axis value labels once with [TextMeasurer] and caches the layout results.
 */
@Composable
fun rememberChartValueAxis(
    gridlines: List<Long>,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    formatLabel: (Long) -> String = { formatCompactMoney(it) },
): ChartValueAxisMeasurements {
    return remember(gridlines, textStyle, textMeasurer) {
        val measured = gridlines.map { value ->
            value to textMeasurer.measure(
                text = formatLabel(value),
                style = textStyle,
            )
        }
        val maxWidth = measured.maxOfOrNull { it.second.size.width.toFloat() } ?: 0f
        val maxHeight = measured.maxOfOrNull { it.second.size.height.toFloat() } ?: 0f
        ChartValueAxisMeasurements(measured, maxWidth, maxHeight)
    }
}

/**
 * Draws horizontal gridlines and right-aligned value labels measured with [TextMeasurer].
 */
fun DrawScope.drawChartGridAndValues(
    axis: ChartValueAxisMeasurements,
    chartAreaWidth: Float,
    chartAreaHeight: Float,
    minVal: Long,
    maxVal: Long,
    gridColor: Color,
    textColor: Color,
    gridStrokeWidth: Float = 1.dp.toPx(),
    pathEffect: PathEffect? = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
    labelPaddingPx: Float = 8.dp.toPx(),
) {
    val span = (maxVal - minVal).coerceAtLeast(1L).toFloat()

    axis.labels.forEach { (value, layoutResult) ->
        val fraction = (value - minVal).toFloat() / span
        val y = chartAreaHeight - (fraction * chartAreaHeight)

        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartAreaWidth, y),
            strokeWidth = gridStrokeWidth,
            pathEffect = pathEffect,
        )

        drawText(
            textLayoutResult = layoutResult,
            color = textColor,
            topLeft = Offset(
                x = chartAreaWidth + labelPaddingPx,
                y = y - layoutResult.size.height / 2f,
            ),
        )
    }
}
