package com.azimulkabir.actua.ui.reports.charts

import com.azimulkabir.actua.model.ReportCategory
import kotlin.math.absoluteValue

/** Most slices a donut draws before the smallest are grouped into "Other". */
const val MAX_DONUT_SLICES = 12

/**
 * Keeps the largest [maxSlices] - 1 categories and folds the rest into one "Other" category, so
 * totals and percentages are unchanged. Kept categories keep their order; "Other" goes last.
 * Its drill-down merges the folded category ids when the queries differ only by category, and
 * otherwise falls back to the union of their transaction ids.
 */
fun groupSmallCategories(
    categories: List<ReportCategory>,
    maxSlices: Int = MAX_DONUT_SLICES,
    otherLabel: String = "Other",
): List<ReportCategory> {
    if (categories.size <= maxSlices) return categories
    val keepCount = maxSlices - 1
    val kept = categories.withIndex()
        .sortedByDescending { it.value.spentCents.absoluteValue }
        .take(keepCount).sortedBy { it.index }.map { it.value }
    val folded = categories.filterNot { c -> kept.any { it === c } }
    val queries = folded.mapNotNull { it.drillDownQuery }
    val mergedQuery = queries.takeIf { it.size == folded.size && it.all { q -> q.categoryIds != null } }
        ?.takeIf { qs -> qs.map { it.copy(categoryIds = null, categoryName = null) }.distinct().size == 1 }
        ?.let { qs -> qs.first().copy(categoryIds = qs.flatMap { it.categoryIds.orEmpty() }.toSet(), categoryName = otherLabel) }
    val other = ReportCategory(
        name = otherLabel,
        spentCents = folded.sumOf { it.spentCents },
        transactionIds = folded.flatMap { it.transactionIds },
        incomeCents = folded.mapNotNull { it.incomeCents }.takeIf { it.isNotEmpty() }?.sum(),
        expenseCents = folded.mapNotNull { it.expenseCents }.takeIf { it.isNotEmpty() }?.sum(),
        drillDownQuery = mergedQuery,
    )
    return kept + other
}

/**
 * Full angular extent of each slice (no gaps), proportional to [magnitudes], clockwise from
 * [startDeg] in canvas convention (0 = 3 o'clock, so -90 starts at 12 o'clock).
 */
fun computeDonutArcs(magnitudes: List<Long>, startDeg: Float = -90f): List<DonutSlice> {
    val total = magnitudes.sum().coerceAtLeast(1L).toDouble()
    var start = startDeg.toDouble()
    return magnitudes.mapIndexed { i, m ->
        val sweep = m * 360.0 / total
        DonutSlice(i, start.toFloat(), sweep.toFloat()).also { start += sweep }
    }
}

/**
 * Slices for tap detection: any slice thinner than [minSweepDeg] is widened around its centre so
 * it stays tappable, and the thinnest come first so they win where widened areas overlap.
 */
fun donutHitSlices(arcs: List<DonutSlice>, minSweepDeg: Float = 10f): List<DonutSlice> =
    arcs.map { arc ->
        if (arc.sweepAngleDeg >= minSweepDeg) arc else {
            val mid = arc.startAngleDeg + arc.sweepAngleDeg / 2f
            DonutSlice(arc.index, mid - minSweepDeg / 2f, minSweepDeg)
        }
    }.sortedBy { it.sweepAngleDeg }
