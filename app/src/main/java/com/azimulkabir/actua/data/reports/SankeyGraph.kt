package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyDiagramLink
import com.azimulkabir.actua.model.SankeyDiagramNode
import com.azimulkabir.actua.model.SankeyLayer
import com.azimulkabir.actua.model.SankeyNodeRole

/**
 * Actua's port of the layered graph in Actual's `sankey-spreadsheet.ts`: a graph of nodes in six
 * layers (payee, income category, account, budget, category group, category) joined by flows in
 * integer cents. The functions below follow upstream's `createTransactionsGraph`,
 * `createBudgetGraph`, `groupOtherCategories`, `sortGraph`, `addPercentageLabels` and
 * `filterGraphByLayers`. Upstream's hidden padding nodes only keep a charting library's columns
 * aligned, so they are replaced by [assignColumns].
 */
internal object SankeyKeys {
    const val TO_BUDGET = "to_budget"
    const val BUDGETED = "budgeted"
    const val LAST_MONTH_OVERSPENT = "last_month_overspent"
    const val FOR_NEXT_MONTH = "for_next_month"
    const val FROM_PREV_MONTH = "from_previous_month"
    const val AVAILABLE_INCOME = "available_income"
    const val ALL_ACCOUNTS = "all_income"
    const val OTHER_SUFFIX = "__OTHER_BUCKET"
    const val NEGATIVE_SUFFIX = "__NEGATIVE"
    const val NO_PAYEE_SUFFIX = "__NO_PAYEE"
    const val OTHER_NAME = "Other"
}

internal class GraphNode(
    val key: String,
    val layer: SankeyLayer,
    var name: String,
    var role: SankeyNodeRole = SankeyNodeRole.NORMAL,
    var isNegative: Boolean = false,
) {
    val to = LinkedHashMap<String, Long>()

    // What a tap on the node drills into; merged when nodes fold into "Other".
    val categoryIds = LinkedHashSet<String>()
    val accountIds = LinkedHashSet<String>()
    val payeeIds = LinkedHashSet<String>()
    var direction: DrillDownDirection? = null
    val folded = LinkedHashMap<String, Long>()

    fun tag(categoryId: String? = null, accountId: String? = null, payeeId: String? = null, direction: DrillDownDirection? = null) {
        categoryId?.let(categoryIds::add)
        accountId?.let(accountIds::add)
        payeeId?.let(payeeIds::add)
        if (direction != null) this.direction = if (this.direction == null || this.direction == direction) direction else DrillDownDirection.ANY
    }

    fun copyOf(): GraphNode = GraphNode(key, layer, name, role, isNegative).also {
        it.to.putAll(to); it.categoryIds.addAll(categoryIds); it.accountIds.addAll(accountIds)
        it.payeeIds.addAll(payeeIds); it.direction = direction; it.folded.putAll(folded)
    }
}

internal class SankeyGraph {
    val nodes = LinkedHashMap<String, GraphNode>()

    fun addNode(
        key: String, layer: SankeyLayer, name: String,
        role: SankeyNodeRole = SankeyNodeRole.NORMAL, isNegative: Boolean = false,
    ): GraphNode = nodes.getOrPut(key) { GraphNode(key, layer, name, role, isNegative) }

    fun link(from: String, to: String, cents: Long) {
        val node = nodes[from] ?: return
        node.to[to] = (node.to[to] ?: 0L) + cents
    }

    fun copyOf(): SankeyGraph = SankeyGraph().also { c -> nodes.forEach { (k, v) -> c.nodes[k] = v.copyOf() } }

    fun nodesInLayer(layer: SankeyLayer): List<String> = nodes.filterValues { it.layer == layer }.keys.toList()
}

/** One category's net flow for one account and payee, as upstream's `fetchCategoryData` returns it. */
internal data class SankeyEntry(
    val categoryGroup: String,
    val categoryGroupId: String,
    val category: String,
    val categoryId: String,
    /** Absolute net amount in cents; [isNegative] carries the sign. */
    val value: Long,
    val isIncome: Boolean,
    val isNegative: Boolean,
    val accountName: String,
    val accountId: String,
    val payeeName: String,
    val payeeId: String,
)

/** One side of a transfer, as `fetchTransferData` reads it. */
internal data class SankeyTransferRow(
    val id: String,
    val amount: Long,
    val accountId: String,
    val accountName: String,
    val transferId: String,
)

internal data class SankeyTransferPair(
    val fromAccountId: String,
    val fromAccountName: String,
    val toAccountId: String,
    val toAccountName: String,
    val amount: Long,
)

/** The budget figures `createBudgetSpreadsheet` aggregates for a budgeted card. */
internal data class SankeyBudgetTotals(
    val toBudget: Long,
    val forNextMonth: Long,
    val fromPreviousMonth: Long,
    val lastMonthOverspent: Long,
    val previousMonthLabel: String,
    val nextMonthLabel: String,
)

private val SOURCE_COLOR_LAYERS = setOf(SankeyLayer.PAYEE, SankeyLayer.INCOME_CATEGORY, SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY_GROUP)

// ---- Graph construction ------------------------------------------------------------------

/** Upstream's `createTransactionsGraph`: payee, income category, account, category group, category. */
internal fun createTransactionsGraph(
    entries: List<SankeyEntry>,
    transfers: List<SankeyTransferPair> = emptyList(),
    groupAccounts: Boolean = false,
): SankeyGraph {
    val graph = SankeyGraph()

    fun accountNode(accountId: String, accountName: String): GraphNode =
        if (accountId == SankeyKeys.ALL_ACCOUNTS) graph.addNode(accountId, SankeyLayer.ACCOUNT, "Income", SankeyNodeRole.POOL)
        else graph.addNode(accountId, SankeyLayer.ACCOUNT, accountName).also { it.tag(accountId = accountId) }

    fun payeeNode(key: String, name: String, entry: SankeyEntry, payeeId: String?): GraphNode =
        graph.addNode(key, SankeyLayer.PAYEE, name).also {
            it.tag(categoryId = entry.categoryId, payeeId = payeeId, direction = DrillDownDirection.INCOME)
        }

    for (e in entries) {
        if (e.accountId.isBlank() || e.accountName.isBlank() || e.categoryId.isBlank()) continue
        if (e.isIncome) {
            if (e.isNegative) {
                // Account > negative income
                accountNode(e.accountId, e.accountName)
                val key = e.categoryId + SankeyKeys.NEGATIVE_SUFFIX
                graph.addNode(key, SankeyLayer.CATEGORY_GROUP, e.payeeName.ifBlank { e.category }, SankeyNodeRole.NEGATIVE, isNegative = true)
                    .tag(categoryId = e.categoryId, direction = DrillDownDirection.EXPENSE)
                graph.link(e.accountId, key, e.value)
            } else {
                // Payee > income category > account
                graph.addNode(e.categoryId, SankeyLayer.INCOME_CATEGORY, e.category)
                    .tag(categoryId = e.categoryId, direction = DrillDownDirection.INCOME)
                accountNode(e.accountId, e.accountName)
                graph.link(e.categoryId, e.accountId, e.value)
                if (e.payeeId.isNotBlank()) {
                    payeeNode(e.payeeId, e.payeeName.ifBlank { "(Unknown payee)" }, e, e.payeeId)
                    graph.link(e.payeeId, e.categoryId, e.value)
                }
            }
        } else if (e.isNegative) {
            // Account > category group > category
            accountNode(e.accountId, e.accountName)
            graph.addNode(e.categoryGroupId, SankeyLayer.CATEGORY_GROUP, e.categoryGroup)
                .tag(categoryId = e.categoryId, direction = DrillDownDirection.EXPENSE)
            graph.addNode(e.categoryId, SankeyLayer.CATEGORY, e.category)
                .tag(categoryId = e.categoryId, direction = DrillDownDirection.EXPENSE)
            graph.link(e.accountId, e.categoryGroupId, e.value)
            graph.link(e.categoryGroupId, e.categoryId, e.value)
        } else {
            // Payee > refunded category > account
            val categoryKey = e.categoryId + SankeyKeys.NEGATIVE_SUFFIX
            graph.addNode(categoryKey, SankeyLayer.INCOME_CATEGORY, e.category)
                .tag(categoryId = e.categoryId, direction = DrillDownDirection.INCOME)
            accountNode(e.accountId, e.accountName)
            graph.link(categoryKey, e.accountId, e.value)
            if (e.payeeId.isNotBlank()) {
                payeeNode(e.payeeId, e.payeeName.ifBlank { "(Unknown payee)" }, e, e.payeeId)
                graph.link(e.payeeId, categoryKey, e.value)
            } else {
                val noPayeeKey = e.categoryId + SankeyKeys.NO_PAYEE_SUFFIX
                payeeNode(noPayeeKey, "(No payee)", e, null)
                graph.link(noPayeeKey, categoryKey, e.value)
            }
        }
    }

    // Transfers between accounts are drawn only when accounts stay separate.
    if (transfers.isNotEmpty() && !groupAccounts) {
        for (pair in transfers) {
            accountNode(pair.fromAccountId, pair.fromAccountName)
            accountNode(pair.toAccountId, pair.toAccountName)
            graph.link(pair.fromAccountId, pair.toAccountId, pair.amount)
        }
    }
    return graph
}

/** One budget category's value for a budgeted card (`CategoryEntry` in upstream's budget spreadsheet). */
internal data class SankeyBudgetEntry(
    val categoryGroup: String,
    val categoryGroupId: String,
    val category: String,
    val categoryId: String,
    val isIncome: Boolean,
    /** Received (income) or budgeted (expense), signed, in cents. */
    val value: Long,
)

/** Upstream's `createBudgetGraph`: income into "Available funds", then "Budgeted" and the groups. */
internal fun createBudgetGraph(entries: List<SankeyBudgetEntry>, totals: SankeyBudgetTotals): SankeyGraph {
    val graph = SankeyGraph()
    graph.addNode(SankeyKeys.BUDGETED, SankeyLayer.BUDGET, "Budgeted", SankeyNodeRole.POOL)
    graph.addNode(SankeyKeys.AVAILABLE_INCOME, SankeyLayer.ACCOUNT, "Available funds", SankeyNodeRole.POOL)

    for (e in entries) {
        if (e.isIncome) {
            graph.addNode(e.categoryId, SankeyLayer.INCOME_CATEGORY, e.category)
            graph.link(e.categoryId, SankeyKeys.AVAILABLE_INCOME, e.value)
        } else if (e.value >= 0) {
            graph.addNode(e.categoryGroupId, SankeyLayer.CATEGORY_GROUP, e.categoryGroup)
            graph.addNode(e.categoryId, SankeyLayer.CATEGORY, e.category)
            graph.link(e.categoryGroupId, e.categoryId, e.value)
            graph.link(SankeyKeys.BUDGETED, e.categoryGroupId, e.value)
            graph.link(SankeyKeys.AVAILABLE_INCOME, SankeyKeys.BUDGETED, e.value)
        } else {
            graph.addNode(e.categoryId, SankeyLayer.ACCOUNT, "From ${e.category}", SankeyNodeRole.NEGATIVE, isNegative = true)
            graph.link(e.categoryId, SankeyKeys.BUDGETED, kotlin.math.abs(e.value))
            graph.link(SankeyKeys.AVAILABLE_INCOME, SankeyKeys.BUDGETED, e.value)
        }
    }

    if (totals.toBudget > 0) {
        graph.addNode(SankeyKeys.TO_BUDGET, SankeyLayer.CATEGORY_GROUP, "To budget", SankeyNodeRole.TO_BUDGET)
        graph.link(SankeyKeys.AVAILABLE_INCOME, SankeyKeys.TO_BUDGET, totals.toBudget)
    } else {
        graph.addNode(SankeyKeys.TO_BUDGET, SankeyLayer.ACCOUNT, "Overbudgeted", SankeyNodeRole.NEGATIVE, isNegative = true)
        graph.link(SankeyKeys.TO_BUDGET, SankeyKeys.BUDGETED, kotlin.math.abs(totals.toBudget))
        graph.link(SankeyKeys.AVAILABLE_INCOME, SankeyKeys.BUDGETED, -kotlin.math.abs(totals.toBudget))
    }

    graph.addNode(SankeyKeys.FROM_PREV_MONTH, SankeyLayer.INCOME_CATEGORY, "From ${totals.previousMonthLabel}", SankeyNodeRole.CARRYOVER)
    graph.link(SankeyKeys.FROM_PREV_MONTH, SankeyKeys.AVAILABLE_INCOME, totals.fromPreviousMonth)
    graph.addNode(SankeyKeys.FOR_NEXT_MONTH, SankeyLayer.BUDGET, "For ${totals.nextMonthLabel}", SankeyNodeRole.CARRYOVER)
    graph.link(SankeyKeys.AVAILABLE_INCOME, SankeyKeys.FOR_NEXT_MONTH, totals.forNextMonth)
    graph.addNode(SankeyKeys.LAST_MONTH_OVERSPENT, SankeyLayer.BUDGET, "Overspent", SankeyNodeRole.OVERSPENT)
    graph.link(SankeyKeys.AVAILABLE_INCOME, SankeyKeys.LAST_MONTH_OVERSPENT, kotlin.math.abs(totals.lastMonthOverspent))
    return graph
}

// ---- Transfers -----------------------------------------------------------------------------

/**
 * Upstream's `aggregateTransferPairs`: pairs the two sides of each transfer by `transfer_id`, adds
 * the amounts per (source, target) account and cancels any cycle of transfers so money going
 * round (A to B and back) is not drawn.
 */
internal fun aggregateTransferPairs(rows: List<SankeyTransferRow>): List<SankeyTransferPair> {
    val byId = HashMap<String, SankeyTransferRow>()
    val nameByAccount = HashMap<String, String>()
    val graph = LinkedHashMap<String, LinkedHashMap<String, Long>>()
    rows.forEach { byId[it.id] = it; nameByAccount[it.accountId] = it.accountName }

    rows.forEach { from ->
        val to = byId[from.transferId] ?: return@forEach
        if (from.accountId > to.accountId) return@forEach
        val source = if (from.amount < 0) from.accountId else to.accountId
        val target = if (source == from.accountId) to.accountId else from.accountId
        if (source == target || from.amount == 0L) return@forEach
        val children = graph.getOrPut(source) { LinkedHashMap() }
        children[target] = (children[target] ?: 0L) + kotlin.math.abs(from.amount)
    }
    cancelTransferCycles(graph)

    return graph.entries.flatMap { (fromId, children) ->
        children.entries.map { (toId, amount) ->
            SankeyTransferPair(fromId, nameByAccount[fromId].orEmpty(), toId, nameByAccount[toId].orEmpty(), amount)
        }
    }.sortedWith(compareBy({ it.fromAccountId }, { it.toAccountId }))
}

private fun cancelTransferCycles(graph: LinkedHashMap<String, LinkedHashMap<String, Long>>) {
    while (true) {
        val cycle = findDirectedCycle(graph.keys) { graph[it]?.keys ?: emptySet() } ?: return
        val amount = cycle.minOf { (from, to) -> graph[from]?.get(to) ?: 0L }
        cycle.forEach { (from, to) ->
            val children = graph[from] ?: return@forEach
            val remaining = (children[to] ?: 0L) - amount
            if (remaining > 0) children[to] = remaining else children.remove(to)
            if (children.isEmpty()) graph.remove(from)
        }
    }
}

/** A directed cycle as a list of edges, found depth first over sorted nodes (upstream's `findDirectedCycle`). */
private fun findDirectedCycle(nodes: Iterable<String>, children: (String) -> Iterable<String>): List<Pair<String, String>>? {
    val state = HashMap<String, Int>() // 1 visiting, 2 visited
    class Frame(val node: String, val kids: List<String>, var next: Int = 0)
    for (start in nodes.toList().sorted()) {
        if (state.containsKey(start)) continue
        val path = mutableListOf(start)
        val pathIndex = hashMapOf(start to 0)
        val stack = mutableListOf(Frame(start, children(start).toList().sorted()))
        state[start] = 1
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            val child = frame.kids.getOrNull(frame.next)
            if (child == null) {
                state[frame.node] = 2
                stack.removeAt(stack.size - 1)
                pathIndex.remove(frame.node)
                path.removeAt(path.size - 1)
                continue
            }
            frame.next++
            val childState = state[child]
            if (childState == 2) continue
            if (childState == 1) {
                val cycleStart = pathIndex[child] ?: continue
                val cycle = mutableListOf<Pair<String, String>>()
                for (i in cycleStart until path.size - 1) cycle += path[i] to path[i + 1]
                cycle += frame.node to child
                return cycle
            }
            state[child] = 1
            pathIndex[child] = path.size
            path += child
            stack += Frame(child, children(child).toList().sorted())
        }
    }
    return null
}

// ---- Values, "Other" folding and sorting ------------------------------------------------------

/**
 * Upstream's `getNodeValue` for every node: a node nothing flows into counts what leaves it;
 * every other node counts what arrives, or what leaves when nothing positive arrives.
 */
internal fun nodeValues(graph: SankeyGraph): Map<String, Long> {
    val incoming = HashMap<String, Long>()
    val hasParent = HashSet<String>()
    graph.nodes.forEach { (source, node) ->
        node.to.forEach { (target, cents) ->
            if (target == source) return@forEach
            incoming[target] = (incoming[target] ?: 0L) + cents
            hasParent += target
        }
    }
    return graph.nodes.mapValues { (key, node) ->
        val out = node.to.values.sum()
        if (key !in hasParent) out else (incoming[key] ?: 0L).takeIf { it > 0 } ?: out
    }
}

/**
 * Upstream's `groupOtherCategories`: in each layer, while the ordinary nodes plus "Other" nodes
 * exceed [topN], the smallest ordinary node (the first of equals) moves into an "Other" node.
 * A category folds into its group's "Other" and a payee into its income category's, unless
 * [globalOther] (the `global` sort) wants one "Other" per layer.
 */
internal fun groupOtherCategories(graph: SankeyGraph, topN: Int, globalOther: Boolean) {
    val structural = setOf(
        SankeyKeys.TO_BUDGET, SankeyKeys.BUDGETED, SankeyKeys.LAST_MONTH_OVERSPENT, SankeyKeys.FOR_NEXT_MONTH,
        SankeyKeys.FROM_PREV_MONTH, SankeyKeys.AVAILABLE_INCOME, SankeyKeys.ALL_ACCOUNTS,
    )
    fun isGroupable(key: String): Boolean {
        val node = graph.nodes[key] ?: return false
        return !key.endsWith(SankeyKeys.OTHER_SUFFIX) && key !in structural && !node.isNegative
    }
    for (layer in SankeyLayer.entries) {
        while (true) {
            val inLayer = graph.nodesInLayer(layer)
            val ordinary = inLayer.filter(::isGroupable)
            val others = inLayer.count { it.endsWith(SankeyKeys.OTHER_SUFFIX) }
            if (ordinary.size + others <= topN) break
            val values = nodeValues(graph)
            var minValue = Long.MAX_VALUE
            var toFold: String? = null
            for (key in ordinary) {
                val v = values[key] ?: 0L
                if (v < minValue) { minValue = v; toFold = key }
            }
            moveToOther(graph, toFold ?: break, globalOther)
        }
    }
}

private fun moveToOther(graph: SankeyGraph, key: String, globalOther: Boolean) {
    val node = graph.nodes[key] ?: return
    val toKeys = node.to.keys.toList()
    val fromNodes = graph.nodes.values.filter { it.to.containsKey(key) }

    var otherKey = node.layer.meta + SankeyKeys.OTHER_SUFFIX
    if (!globalOther) {
        if (node.layer == SankeyLayer.CATEGORY) {
            graph.nodes.values.firstOrNull { it.layer == SankeyLayer.CATEGORY_GROUP && it.to.containsKey(key) }
                ?.let { otherKey = it.key + SankeyKeys.OTHER_SUFFIX }
        } else if (node.layer == SankeyLayer.PAYEE) {
            node.to.keys.firstOrNull { graph.nodes[it]?.layer == SankeyLayer.INCOME_CATEGORY }
                ?.let { otherKey = it + SankeyKeys.OTHER_SUFFIX }
        }
    }
    val other = graph.addNode(otherKey, node.layer, SankeyKeys.OTHER_NAME)
    other.categoryIds += node.categoryIds
    other.accountIds += node.accountIds
    other.payeeIds += node.payeeIds
    if (node.direction != null) other.tag(direction = node.direction)
    other.folded[node.name] = (other.folded[node.name] ?: 0L) + (nodeValues(graph)[key] ?: 0L)

    fromNodes.forEach { from ->
        val cents = from.to[key] ?: return@forEach
        if (from.key != otherKey) graph.link(from.key, otherKey, cents)
    }
    toKeys.forEach { target ->
        val cents = node.to[target] ?: return@forEach
        if (otherKey != target) graph.link(otherKey, target, cents)
    }
    fromNodes.forEach { it.to.remove(key) }
    graph.nodes.remove(key)
}

internal enum class SankeySort { PER_GROUP, GLOBAL, BUDGET_ORDER }

/** The budget's category groups and their categories, in order, for `categorySort = budget-order`. */
internal data class SankeyBudgetOrder(val groups: List<Pair<String, List<String>>> = emptyList())

/**
 * Upstream's `sortGraph`: the order the nodes are listed in; within a column it is top to bottom.
 * `per-group` lists each group's categories under it (and each income category's payees above
 * it), `global` lists everything by value with "Other" last, `budget-order` follows the budget.
 */
internal fun sortGraph(graph: SankeyGraph, sort: SankeySort, budgetOrder: SankeyBudgetOrder): List<String> {
    val values = nodeValues(graph)
    fun value(key: String) = values[key] ?: 0L
    val entries = mutableListOf<String>()

    fun relatedAround(anchorLayer: SankeyLayer, relatedLayer: SankeyLayer, after: Boolean) {
        for (anchorKey in graph.nodesInLayer(anchorLayer)) {
            val anchor = graph.nodes[anchorKey] ?: continue
            val related = if (after) anchor.to.keys.filter { graph.nodes[it]?.layer == relatedLayer }
                else graph.nodes.values.filter { it.layer == relatedLayer && it.to.containsKey(anchorKey) }.map { it.key }
            val otherKey = anchorKey + SankeyKeys.OTHER_SUFFIX
            val relatedOther = otherKey.takeIf { graph.nodes[it]?.layer == relatedLayer }
            val ordered = related.filter { it != otherKey }.filter { it in entries }
                .sortedByDescending { value(it) } // stable, like upstream's sort
            val toMove = (related.filter { it != otherKey } + listOfNotNull(relatedOther)).toSet()
            val without = entries.filter { it !in toMove }.toMutableList()
            val anchorIndex = without.indexOf(anchorKey)
            if (anchorIndex == -1) continue
            val insertAt = if (after) anchorIndex + 1 else anchorIndex
            without.addAll(insertAt, ordered + listOfNotNull(relatedOther?.takeIf { it in entries }))
            entries.clear(); entries.addAll(without)
        }
    }

    when (sort) {
        SankeySort.GLOBAL -> {
            entries += graph.nodes.keys.sortedByDescending { value(it) }
            entries.filter { it.endsWith(SankeyKeys.OTHER_SUFFIX) }.forEach { entries.remove(it); entries.add(it) }
        }
        SankeySort.PER_GROUP -> {
            entries += graph.nodes.keys.sortedByDescending { value(it) }
            relatedAround(SankeyLayer.CATEGORY_GROUP, SankeyLayer.CATEGORY, after = true)
            relatedAround(SankeyLayer.INCOME_CATEGORY, SankeyLayer.PAYEE, after = false)
        }
        SankeySort.BUDGET_ORDER -> {
            val used = LinkedHashSet<String>()
            budgetOrder.groups.forEach { (groupId, categoryIds) ->
                if (graph.nodes.containsKey(groupId)) used += groupId
                categoryIds.filter { graph.nodes.containsKey(it) }.forEach { used += it }
                (groupId + SankeyKeys.OTHER_SUFFIX).takeIf { graph.nodes.containsKey(it) }?.let { used += it }
            }
            entries += used + graph.nodes.keys.filter { it !in used }
        }
    }

    // Each negative node moves to the start in turn, so they end up in reverse order, as upstream's do.
    entries.filter { graph.nodes[it]?.isNegative == true }.forEach { entries.remove(it); entries.add(0, it) }
    graph.nodes[SankeyKeys.TO_BUDGET]?.let {
        entries.remove(SankeyKeys.TO_BUDGET)
        if (it.isNegative) entries.add(0, SankeyKeys.TO_BUDGET) else entries.add(SankeyKeys.TO_BUDGET)
    }
    listOf(SankeyKeys.LAST_MONTH_OVERSPENT, SankeyKeys.FOR_NEXT_MONTH, SankeyKeys.FROM_PREV_MONTH).forEach {
        if (entries.remove(it)) entries.add(it)
    }
    return entries
}

/** Upstream's `cleanUpNodes`: drops zero flows, then nodes with no flow in or out. */
internal fun cleanUpNodes(graph: SankeyGraph) {
    graph.nodes.values.forEach { node -> node.to.entries.removeAll { it.value == 0L } }
    val hasIncoming = graph.nodes.values.flatMap { it.to.keys }.toHashSet()
    graph.nodes.entries.removeAll { (key, node) -> node.to.isEmpty() && key !in hasIncoming }
}

// ---- Layers and columns -----------------------------------------------------------------------

/** Upstream's `filterGraphByLayers`: keeps the layers from [from] to [to], inclusive. */
internal fun filterGraphByLayers(graph: SankeyGraph, from: SankeyLayer, to: SankeyLayer) {
    graph.nodes.entries.removeAll { (_, node) -> node.layer.ordinal < from.ordinal || node.layer.ordinal > to.ordinal }
    graph.nodes.values.forEach { node -> node.to.entries.removeAll { !graph.nodes.containsKey(it.key) } }
}

/**
 * Columns for the nodes of a layer-filtered graph: one per layer that has nodes, left to right in
 * layer order. Transfers chain accounts, so the account layer gets one column per link in its
 * longest chain of transfers. Returns each node's column and the layer of every column.
 */
internal fun assignColumns(graph: SankeyGraph): Pair<Map<String, Int>, List<SankeyLayer>> {
    val layers = SankeyLayer.entries.filter { layer -> graph.nodes.values.any { it.layer == layer } }
    val accountDepth = HashMap<String, Int>()
    val resolving = HashSet<String>()
    fun depth(key: String): Int {
        accountDepth[key]?.let { return it }
        if (!resolving.add(key)) return 0
        val parents = graph.nodes.values.filter { it.layer == SankeyLayer.ACCOUNT && it.to.containsKey(key) && it.key != key }
        val d = if (parents.isEmpty()) 0 else 1 + parents.maxOf { depth(it.key) }
        resolving.remove(key)
        accountDepth[key] = d
        return d
    }
    val depths = graph.nodes.values.filter { it.layer == SankeyLayer.ACCOUNT }.associate { it.key to depth(it.key) }
    val accountWidth = 1 + (depths.values.maxOrNull() ?: 0)

    val base = HashMap<SankeyLayer, Int>()
    val columnLayers = mutableListOf<SankeyLayer>()
    for (layer in layers) {
        base[layer] = columnLayers.size
        repeat(if (layer == SankeyLayer.ACCOUNT) accountWidth else 1) { columnLayers += layer }
    }
    val columns = graph.nodes.values.associate { it.key to (base.getValue(it.layer) + (depths[it.key] ?: 0)) }
    return columns to columnLayers
}

/**
 * Upstream's `buildSankeyData` for a base graph: folds "Other" nodes, sorts, works out each node's
 * share of its layer, applies the layer range and lays the survivors out in columns. [start] and
 * [end] are the YYYYMMDD range a node's drill-down covers; [budgeted] graphs have no drill-down.
 */
internal fun buildSankeyDiagram(
    base: SankeyGraph,
    options: SankeyOptions,
    budgetOrder: SankeyBudgetOrder,
    start: Int,
    end: Int,
    budgeted: Boolean,
): SankeyDiagram {
    val graph = base.copyOf()
    groupOtherCategories(graph, options.topN, options.sort == SankeySort.GLOBAL)
    val order = sortGraph(graph, options.sort, budgetOrder)
    cleanUpNodes(graph)

    // Each node's share of its layer, taken before the layer range is applied.
    val values = nodeValues(graph)
    val layerSums = HashMap<SankeyLayer, Long>()
    graph.nodes.forEach { (key, node) -> layerSums[node.layer] = (layerSums[node.layer] ?: 0L) + (values[key] ?: 0L) }
    val shares = graph.nodes.mapValues { (key, node) ->
        val total = layerSums[node.layer] ?: 0L
        if (total > 0) (values[key] ?: 0L).toDouble() / total else 0.0
    }

    filterGraphByLayers(graph, options.layerFrom, options.layerTo)
    if (graph.nodes.isEmpty()) return SankeyDiagram(emptyList(), emptyList(), emptyList(), budgeted, options.showPercentages)

    // Drop the flows nothing positive passes along and any node left with none.
    graph.nodes.values.forEach { node -> node.to.entries.removeAll { it.value <= 0L } }
    val touched = HashSet<String>()
    graph.nodes.values.forEach { node -> if (node.to.isNotEmpty()) { touched += node.key; touched += node.to.keys } }
    graph.nodes.entries.removeAll { it.key !in touched }
    if (graph.nodes.isEmpty()) return SankeyDiagram(emptyList(), emptyList(), emptyList(), budgeted, options.showPercentages)

    val (columns, columnLayers) = assignColumns(graph)
    val ordered = order.filter { graph.nodes.containsKey(it) }
    val index = ordered.withIndex().associate { (i, key) -> key to i }

    val inSum = HashMap<String, Long>()
    graph.nodes.values.forEach { n -> n.to.forEach { (t, c) -> inSum[t] = (inSum[t] ?: 0L) + c } }
    val nodes = ordered.map { key ->
        val node = graph.nodes.getValue(key)
        SankeyDiagramNode(
            key = key, name = node.name, layer = node.layer, role = node.role, column = columns.getValue(key),
            valueCents = maxOf(node.to.values.sum(), inSum[key] ?: 0L), share = shares[key] ?: 0.0,
            drillDownQuery = if (budgeted) null else node.drillDown(start, end),
            foldedNames = node.folded.entries.sortedByDescending { it.value }.map { it.key },
        )
    }
    val specialTargets = setOf(SankeyKeys.LAST_MONTH_OVERSPENT, SankeyKeys.TO_BUDGET, SankeyKeys.FOR_NEXT_MONTH)
    val links = ordered.flatMap { sourceKey ->
        val source = graph.nodes.getValue(sourceKey)
        source.to.map { (targetKey, cents) ->
            val fromSource = when {
                source.isNegative -> true
                targetKey in specialTargets || targetKey.endsWith(SankeyKeys.NEGATIVE_SUFFIX) -> false
                else -> source.layer in SOURCE_COLOR_LAYERS
            }
            SankeyDiagramLink(index.getValue(sourceKey), index.getValue(targetKey), cents, fromSource)
        }
    }
    return SankeyDiagram(nodes, links, columnLayers, budgeted, options.showPercentages)
}

private fun GraphNode.drillDown(start: Int, end: Int): DrillDownQuery? {
    if (categoryIds.isEmpty() && accountIds.isEmpty() && payeeIds.isEmpty()) return null
    return DrillDownQuery(
        startDate = start, endDate = end,
        accountIds = accountIds.takeIf { it.isNotEmpty() },
        categoryIds = categoryIds.takeIf { it.isNotEmpty() },
        payeeId = payeeIds.singleOrNull(),
        direction = direction ?: DrillDownDirection.ANY,
        accountName = if (layer == SankeyLayer.ACCOUNT) name else null,
        categoryName = name,
    )
}
