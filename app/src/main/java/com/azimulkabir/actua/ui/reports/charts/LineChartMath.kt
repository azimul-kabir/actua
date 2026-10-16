package com.azimulkabir.actua.ui.reports.charts

/**
 * Index of the first projected point, or null when every point is posted.
 * The solid line ends at the point before it; the dashed forecast line starts there and runs on.
 */
fun forecastBoundaryIndex(isForecast: List<Boolean>): Int? =
    isForecast.indexOfFirst { it }.takeIf { it >= 0 }

/**
 * Fractional index at which [primary] first reaches [secondary], linearly interpolated between
 * the two neighbouring points, or null if it never does. A series that starts at or above the
 * other crosses at 0.
 */
fun findCrossoverPosition(primary: List<Long>, secondary: List<Long>): Float? {
    val n = minOf(primary.size, secondary.size)
    if (n == 0) return null
    if (primary[0] >= secondary[0]) return 0f
    for (i in 1 until n) {
        if (primary[i] >= secondary[i]) {
            val before = (secondary[i - 1] - primary[i - 1]).toDouble()
            val after = (primary[i] - secondary[i]).toDouble()
            return (i - 1 + before / (before + after)).toFloat()
        }
    }
    return null
}

/** Nearest point index for a touch at [x] across a plot [width] holding [count] evenly spaced points. */
fun nearestPointIndex(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return Math.round(x / width * (count - 1)).coerceIn(0, count - 1)
}

/**
 * Period to select after moving [delta] (+1 next, -1 previous) from [current], for TalkBack's
 * Next and Previous period actions. With nothing selected, Next picks the first period and
 * Previous the last; the result always stays within the chart.
 */
fun stepPeriodIndex(current: Int?, delta: Int, count: Int): Int {
    if (count <= 0) return 0
    return when {
        current == null -> if (delta >= 0) 0 else count - 1
        else -> (current + delta).coerceIn(0, count - 1)
    }
}

/**
 * One line a screen reader can speak for a series: how many periods, where it starts and ends, and
 * its high and low. [format] renders a value the way the chart's tooltip does.
 */
fun seriesSummary(label: String, periods: List<String>, values: List<Long>, format: (Long) -> String): String {
    val n = minOf(periods.size, values.size)
    if (n == 0) return "$label: no data."
    val high = (0 until n).maxBy { values[it] }
    val low = (0 until n).minBy { values[it] }
    return "$label: $n periods, from ${periods[0].take(10)} ${format(values[0])} to ${periods[n - 1].take(10)} ${format(values[n - 1])}, " +
        "highest ${format(values[high])} in ${periods[high].take(10)}, lowest ${format(values[low])} in ${periods[low].take(10)}."
}
