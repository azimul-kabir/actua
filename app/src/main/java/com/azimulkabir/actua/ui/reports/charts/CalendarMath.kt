package com.azimulkabir.actua.ui.reports.charts

import kotlin.math.absoluteValue

/**
 * Month step for a horizontal drag of [totalDragPx] (negative = finger moved left). A swipe left
 * goes to the next month (+1), right to the previous (-1), and a short drag changes nothing (0).
 */
fun calendarSwipeStep(totalDragPx: Float, thresholdPx: Float): Int = when {
    totalDragPx.absoluteValue < thresholdPx -> 0
    totalDragPx < 0f -> 1
    else -> -1
}

/** [index] moved by [step] but kept inside 0..[lastIndex]. */
fun clampMonthIndex(index: Int, step: Int, lastIndex: Int): Int = (index + step).coerceIn(0, lastIndex.coerceAtLeast(0))

/** Bar height as a fraction of the month's largest day; zero stays zero, any amount stays visible. */
fun calendarBarFraction(cents: Long, monthMaxCents: Long, minVisible: Float = 0.08f): Float {
    if (cents <= 0L || monthMaxCents <= 0L) return 0f
    return (cents.toFloat() / monthMaxCents).coerceIn(minVisible, 1f)
}
