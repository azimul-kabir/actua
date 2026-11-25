package com.azimulkabir.actua.model

/**
 * The layers of a Sankey card, in the order upstream draws them (`GRAPH_LAYER_ORDER` in
 * `sankey-spreadsheet.ts`). [meta] is the value saved in the card's `layerFrom` / `layerTo`.
 */
enum class SankeyLayer(val meta: String, val title: String) {
    PAYEE("payee", "Payees"),
    INCOME_CATEGORY("income_category", "Income categories"),
    ACCOUNT("account", "Accounts"),
    BUDGET("budget", "Budget"),
    CATEGORY_GROUP("category_group", "Category groups"),
    CATEGORY("category", "Categories");

    companion object {
        fun fromMeta(value: String?): SankeyLayer? = entries.firstOrNull { it.meta == value }
    }
}

/** How the chart colours a node whose colour carries meaning, as upstream's `addColors` does. */
enum class SankeyNodeRole {
    NORMAL,

    /** The single "Income" node of `groupAccounts`, and the budgeted "Available funds" and "Budgeted" nodes. */
    POOL,

    /** A net-negative flow (negative income, "Overbudgeted", "From <category>"). */
    NEGATIVE,

    /** "To budget": money not yet assigned. */
    TO_BUDGET,

    /** "Overspent" from the previous month. */
    OVERSPENT,

    /** The carry-over nodes "From <month>" and "For <month>". */
    CARRYOVER,
}

/**
 * One node of a drawn Sankey. [column] is its x position, [valueCents] the larger of what flows in
 * and out of it in the drawn layer range, and [share] its fraction of its whole layer
 * (upstream's `percentageLabel`, taken before the layer range is applied).
 */
data class SankeyDiagramNode(
    val key: String,
    val name: String,
    val layer: SankeyLayer,
    val role: SankeyNodeRole,
    val column: Int,
    val valueCents: Long,
    val share: Double,
    val drillDownQuery: DrillDownQuery? = null,
    /** Names folded into an "Other" node, largest first, for its description. */
    val foldedNames: List<String> = emptyList(),
)

/**
 * A flow between two nodes of [SankeyDiagram.nodes], in integer cents. [colorFromSource] follows
 * upstream's rule: flows out of payees, income categories, accounts and category groups take the
 * source colour; flows into categories and budget nodes take the target colour.
 */
data class SankeyDiagramLink(
    val source: Int,
    val target: Int,
    val cents: Long,
    val colorFromSource: Boolean,
)

/**
 * A layered Sankey as upstream's `buildSankeyData` returns it, with the layer range applied and
 * the nodes in drawing order (top to bottom within each column).
 */
data class SankeyDiagram(
    val nodes: List<SankeyDiagramNode>,
    val links: List<SankeyDiagramLink>,
    /** The layer drawn in each column, left to right. Account chains from transfers share one layer. */
    val columnLayers: List<SankeyLayer>,
    val budgeted: Boolean = false,
    val showPercentages: Boolean = false,
) {
    val columnCount: Int get() = columnLayers.size

    /** What flows into the first column: the sum of its nodes. */
    val inflowCents: Long get() = nodes.filter { it.column == 0 }.sumOf { it.valueCents }

    /** What reaches the last column: the sum of its nodes. */
    val outflowCents: Long get() = nodes.filter { it.column == columnCount - 1 }.sumOf { it.valueCents }

    val isEmpty: Boolean get() = nodes.isEmpty() || links.isEmpty()
}
