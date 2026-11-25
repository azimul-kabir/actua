package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.model.ReportCategory
import org.json.JSONObject
import kotlin.math.absoluteValue

/**
 * The Sankey card options Actua applies to its income and expense columns, read from the saved
 * widget's `meta` (`SankeyWidget` in `loot-core/.../dashboard.ts`). `layerFrom` and `layerTo`
 * only choose between Actua's group and category columns; the payee, account and budget layers need
 * a layered chart and are not drawn.
 */
internal data class SankeyOptions(
    /** `topNcategories`: most nodes a column keeps before the smallest fold into "Other"; null shows all. */
    val topN: Int? = null,
    /** `categorySort = budget-order`: follow the budget's group and category order instead of value. */
    val budgetOrder: Boolean = false,
    /** `categorySort = global`: always list "Other" last. */
    val otherLast: Boolean = false,
    /** `showPercentages`: label nodes with their share of the column instead of an amount. */
    val showPercentages: Boolean = false,
    /** `groupAccounts`: net each category (and income payee) across accounts instead of per account. */
    val groupAccounts: Boolean = false,
    /** `layerTo = category`: the expense column lists categories instead of category groups. */
    val categoryLayer: Boolean = false,
) {
    companion object {
        const val OTHER = "Other"

        fun from(meta: JSONObject?): SankeyOptions {
            if (meta == null) return SankeyOptions()
            val sort = meta.optString("categorySort")
            return SankeyOptions(
                topN = if (meta.has("topNcategories") && !meta.isNull("topNcategories")) meta.optInt("topNcategories", 0).takeIf { it > 0 } else null,
                budgetOrder = sort == "budget-order",
                otherLast = sort == "global",
                showPercentages = meta.optBoolean("showPercentages", false),
                groupAccounts = meta.optBoolean("groupAccounts", false),
                categoryLayer = meta.optString("layerTo") == "category",
            )
        }
    }
}

/**
 * Upstream's `groupOtherCategories` for one column: while the column holds more than [topN] nodes,
 * the smallest (the first of equals) folds into a single "Other" node, which counts toward the
 * limit, so a column keeps `topN - 1` nodes plus "Other". Returns the kept nodes and "Other" (null
 * when nothing folded). Folded totals add up exactly in cents, and the drill-down is merged when
 * the nodes' queries differ only by category.
 */
internal fun splitOffOther(nodes: List<ReportCategory>, topN: Int?): Pair<List<ReportCategory>, ReportCategory?> {
    if (topN == null || nodes.size <= topN) return nodes to null
    val keepCount = (topN - 1).coerceAtLeast(0)
    val foldedIndexes = nodes.indices.sortedWith(compareBy({ nodes[it].spentCents.absoluteValue }, { it }))
        .take(nodes.size - keepCount).toSet()
    val folded = nodes.filterIndexed { i, _ -> i in foldedIndexes }
    val kept = nodes.filterIndexed { i, _ -> i !in foldedIndexes }
    val queries = folded.mapNotNull { it.drillDownQuery }
    val merged = queries.takeIf { it.size == folded.size && it.all { q -> q.categoryIds != null } }
        ?.takeIf { qs -> qs.map { it.copy(categoryIds = null, categoryName = null) }.distinct().size == 1 }
        ?.let { qs -> qs.first().copy(categoryIds = qs.flatMap { it.categoryIds.orEmpty() }.toSet(), categoryName = SankeyOptions.OTHER) }
    return kept to ReportCategory(
        name = SankeyOptions.OTHER,
        spentCents = folded.sumOf { it.spentCents },
        transactionIds = folded.flatMap { it.transactionIds },
        drillDownQuery = merged,
    )
}

/**
 * One Sankey column as upstream orders it (`groupOtherCategories` then `sortGraph`): the top-N fold,
 * then by value, largest first (`per-group`, where "Other" sorts among the rest by its value, and
 * `global`, where it is listed last), or in the budget's order for `budget-order`. [budgetOrder]
 * lists the column's names in that order; unknown names and "Other" come last.
 */
internal fun shapeSankeyColumn(nodes: List<ReportCategory>, options: SankeyOptions, budgetOrder: List<String>): List<ReportCategory> {
    val (kept, other) = splitOffOther(nodes, options.topN)
    if (options.budgetOrder) {
        val position = budgetOrder.withIndex().associate { (i, name) -> name to i }
        return kept.withIndex().sortedWith(compareBy({ position[it.value.name] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value } + listOfNotNull(other)
    }
    if (other == null) return kept.sortedByDescending { it.spentCents }
    return if (options.otherLast) kept.sortedByDescending { it.spentCents } + other
    else (kept + other).sortedByDescending { it.spentCents }
}
