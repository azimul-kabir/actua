package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.model.SankeyLayer
import org.json.JSONObject

/**
 * The Sankey card options Actua applies, read from the saved widget's `meta` (`SankeyWidget` in
 * `loot-core/.../dashboard.ts`).
 */
internal data class SankeyOptions(
    /** `topNcategories`: most nodes a layer keeps before the smallest fold into "Other". Upstream defaults to 15. */
    val topN: Int = DEFAULT_TOP_N,
    /** `categorySort`: `per-group`, `global` (one "Other" per layer, listed last) or `budget-order`. */
    val sort: SankeySort = SankeySort.PER_GROUP,
    /** `showPercentages`: label nodes with their share of the layer instead of an amount. */
    val showPercentages: Boolean = false,
    /** `groupAccounts`: net each category (and income payee) across accounts and draw one "Income" account. */
    val groupAccounts: Boolean = false,
    /** `showTransfers`: draw transfers between accounts as flows from account to account. */
    val showTransfers: Boolean = false,
    /** `layerFrom`: the first layer drawn. */
    val layerFrom: SankeyLayer = SankeyLayer.PAYEE,
    /** `layerTo`: the last layer drawn. */
    val layerTo: SankeyLayer = SankeyLayer.CATEGORY,
    /** `mode = budgeted`. */
    val budgeted: Boolean = false,
) {
    companion object {
        const val DEFAULT_TOP_N = 15

        /** Upstream's `getDefaultLayerRange`: budgeted cards start at income categories, spent cards at payees. */
        fun defaultFrom(budgeted: Boolean) = if (budgeted) SankeyLayer.INCOME_CATEGORY else SankeyLayer.PAYEE

        fun from(meta: JSONObject?): SankeyOptions {
            val budgeted = meta?.optString("mode") == "budgeted"
            if (meta == null) return SankeyOptions(budgeted = budgeted, layerFrom = defaultFrom(budgeted))
            // Upstream uses a saved range only when both ends are real layers.
            val from = SankeyLayer.fromMeta(meta.optString("layerFrom"))
            val to = SankeyLayer.fromMeta(meta.optString("layerTo"))
            val range = if (from != null && to != null) from to to else defaultFrom(budgeted) to SankeyLayer.CATEGORY
            val topN = if (meta.has("topNcategories") && !meta.isNull("topNcategories")) {
                meta.optInt("topNcategories", 0).takeIf { it > 0 } ?: DEFAULT_TOP_N
            } else DEFAULT_TOP_N
            return SankeyOptions(
                topN = topN,
                sort = when (meta.optString("categorySort")) {
                    "global" -> SankeySort.GLOBAL
                    "budget-order" -> SankeySort.BUDGET_ORDER
                    else -> SankeySort.PER_GROUP
                },
                showPercentages = meta.optBoolean("showPercentages", false),
                groupAccounts = meta.optBoolean("groupAccounts", false),
                showTransfers = meta.optBoolean("showTransfers", false),
                layerFrom = range.first,
                layerTo = range.second,
                budgeted = budgeted,
            )
        }
    }
}
