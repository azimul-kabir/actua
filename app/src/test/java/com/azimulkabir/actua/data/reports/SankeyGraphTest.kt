package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyLayer
import com.azimulkabir.actua.model.SankeyNodeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layered Sankey graph (actua#1788), checked against hand-worked results of upstream's
 * `createTransactionsGraph`, `groupOtherCategories`, `sortGraph`, `aggregateTransferPairs` and
 * `filterGraphByLayers` in `sankey-spreadsheet.ts`.
 */
class SankeyGraphTest {
    private fun entry(
        category: String, group: String, cents: Long, account: String = "chk", payee: String = "",
        income: Boolean = false, negative: Boolean = !income,
    ) = SankeyEntry(
        categoryGroup = group, categoryGroupId = group.lowercase(), category = category, categoryId = category.lowercase(),
        value = cents, isIncome = income, isNegative = negative,
        accountName = account.uppercase(), accountId = account, payeeName = payee.uppercase(), payeeId = payee,
    )

    private val entries = listOf(
        entry("Salary", "Income", 500_000, payee = "acme", income = true, negative = false),
        entry("Bonus", "Income", 100_000, payee = "acme", income = true, negative = false),
        entry("Rent", "Bills", 300_000),
        entry("Power", "Bills", 40_000),
        entry("Food", "Living", 100_000),
        entry("Food", "Living", 5_000, payee = "shop", negative = false), // a refund
    )

    private fun diagram(
        graph: SankeyGraph, options: SankeyOptions = SankeyOptions(), order: SankeyBudgetOrder = SankeyBudgetOrder(),
    ) = buildSankeyDiagram(graph, options, order, 20260401, 20260430, budgeted = false)

    private fun SankeyDiagram.flows() = links.associate { "${nodes[it.source].name}>${nodes[it.target].name}" to it.cents }
    private fun SankeyDiagram.column(c: Int) = nodes.filter { it.column == c }.map { it.name }

    @Test fun `the default range draws payees, income categories, accounts, groups and categories`() {
        val d = diagram(createTransactionsGraph(entries))
        assertEquals(
            listOf(SankeyLayer.PAYEE, SankeyLayer.INCOME_CATEGORY, SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY_GROUP, SankeyLayer.CATEGORY),
            d.columnLayers,
        )
        assertEquals(
            mapOf(
                "ACME>Salary" to 500_000L, "ACME>Bonus" to 100_000L, "SHOP>Food" to 5_000L,
                "Salary>CHK" to 500_000L, "Bonus>CHK" to 100_000L, "Food>CHK" to 5_000L,
                "CHK>Bills" to 340_000L, "CHK>Living" to 100_000L,
                "Bills>Rent" to 300_000L, "Bills>Power" to 40_000L, "Living>Food" to 100_000L,
            ),
            d.flows(),
        )
    }

    @Test fun `a refund flows in from its payee through the category and the nodes list by value, categories under their group`() {
        val d = diagram(createTransactionsGraph(entries))
        assertEquals(listOf("ACME", "SHOP"), d.column(0))
        assertEquals(listOf("Salary", "Bonus", "Food"), d.column(1))
        assertEquals(listOf("Bills", "Living"), d.column(3))
        // per-group puts each group's categories right after it, so within the column they read Rent, Power, Food.
        assertEquals(listOf("Rent", "Power", "Food"), d.column(4))
    }

    @Test fun `layerFrom and layerTo keep only the layers between them`() {
        val graph = createTransactionsGraph(entries)
        val accounts = diagram(graph, SankeyOptions(layerFrom = SankeyLayer.ACCOUNT, layerTo = SankeyLayer.CATEGORY_GROUP))
        assertEquals(listOf(SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY_GROUP), accounts.columnLayers)
        assertEquals(mapOf("CHK>Bills" to 340_000L, "CHK>Living" to 100_000L), accounts.flows())
        // The account node still reports its share of the whole account layer.
        assertEquals(1.0, accounts.nodes.first { it.name == "CHK" }.share, 0.0)

        val income = diagram(graph, SankeyOptions(layerFrom = SankeyLayer.PAYEE, layerTo = SankeyLayer.INCOME_CATEGORY))
        assertEquals(listOf("ACME", "SHOP"), income.column(0))
        assertEquals(setOf("ACME>Salary", "ACME>Bonus", "SHOP>Food"), income.flows().keys)

        val none = diagram(graph, SankeyOptions(layerFrom = SankeyLayer.CATEGORY, layerTo = SankeyLayer.PAYEE))
        assertTrue(none.isEmpty)
    }

    @Test fun `groupAccounts folds every account into one Income node`() {
        val grouped = listOf(
            entry("Salary", "Income", 500_000, account = SankeyKeys.ALL_ACCOUNTS, payee = "acme", income = true, negative = false),
            entry("Rent", "Bills", 300_000, account = SankeyKeys.ALL_ACCOUNTS),
        )
        val d = diagram(createTransactionsGraph(grouped, groupAccounts = true), SankeyOptions(groupAccounts = true))
        assertEquals(listOf("Income"), d.nodes.filter { it.layer == SankeyLayer.ACCOUNT }.map { it.name })
        assertEquals(SankeyNodeRole.POOL, d.nodes.first { it.layer == SankeyLayer.ACCOUNT }.role)
    }

    @Test fun `negative income flows out of its account into a red node at the group layer`() {
        val d = diagram(
            createTransactionsGraph(listOf(
                entry("Salary", "Income", 500_000, payee = "acme", income = true, negative = false),
                entry("Salary", "Income", 20_000, account = "sav", payee = "acme", income = true, negative = true),
                entry("Rent", "Bills", 300_000),
            )),
        )
        val negative = d.nodes.first { it.role == SankeyNodeRole.NEGATIVE }
        assertEquals("ACME", negative.name)
        assertEquals(SankeyLayer.CATEGORY_GROUP, negative.layer)
        assertEquals(20_000L, negative.valueCents)
        // Listed first in its column, ahead of Bills.
        assertEquals(listOf("ACME", "Bills"), d.column(3))
    }

    @Test fun `transfers pair by transfer id, add up per direction and cancel cycles`() {
        fun row(id: String, amount: Long, account: String, to: String) =
            SankeyTransferRow(id, amount, account, account.uppercase(), to)
        val pairs = aggregateTransferPairs(listOf(
            row("a1", -100_00, "a", "b1"), row("b1", 100_00, "b", "a1"),
            row("b2", -40_00, "b", "a2"), row("a2", 40_00, "a", "b2"),
            row("c1", -25_00, "a", "d1"), row("d1", 25_00, "c", "c1"),
            row("e1", -10_00, "a", "missing"), // its other side is not in the rows (an off-budget account), so it is skipped
        ))
        // a>b is 100.00 less the 40.00 that came back from b; a>c stays.
        assertEquals(
            listOf(SankeyTransferPair("a", "A", "b", "B", 60_00), SankeyTransferPair("a", "A", "c", "C", 25_00)),
            pairs,
        )
    }

    @Test fun `a three account cycle of transfers cancels by its smallest link`() {
        fun pair(idA: String, idB: String, a: String, b: String, cents: Long) = listOf(
            SankeyTransferRow(idA, -cents, a, a.uppercase(), idB), SankeyTransferRow(idB, cents, b, b.uppercase(), idA),
        )
        val pairs = aggregateTransferPairs(
            pair("1", "2", "a", "b", 100_00) + pair("3", "4", "b", "c", 70_00) + pair("5", "6", "c", "a", 30_00),
        )
        assertEquals(
            listOf(Triple("a", "b", 70_00L), Triple("b", "c", 40_00L)),
            pairs.map { Triple(it.fromAccountId, it.toAccountId, it.amount) },
        )
    }

    @Test fun `transfers chain accounts into extra columns only while accounts stay separate`() {
        val transfers = listOf(SankeyTransferPair("chk", "CHK", "sav", "SAV", 50_000))
        val withTransfers = diagram(createTransactionsGraph(entries, transfers))
        assertEquals(
            listOf(SankeyLayer.PAYEE, SankeyLayer.INCOME_CATEGORY, SankeyLayer.ACCOUNT, SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY_GROUP, SankeyLayer.CATEGORY),
            withTransfers.columnLayers,
        )
        assertEquals(listOf("CHK"), withTransfers.column(2))
        assertEquals(listOf("SAV"), withTransfers.column(3))
        assertEquals(50_000L, withTransfers.flows()["CHK>SAV"])
        // Groups sit after the last account column.
        assertEquals(listOf("Bills", "Living"), withTransfers.column(4))

        val grouped = diagram(createTransactionsGraph(entries, transfers, groupAccounts = true), SankeyOptions(groupAccounts = true))
        assertTrue(grouped.flows().keys.none { it.endsWith(">SAV") })
    }

    @Test fun `the smallest nodes fold into their group's Other, which counts toward the limit`() {
        val many = listOf(
            entry("Rent", "Bills", 300_000), entry("Power", "Bills", 40_000), entry("Water", "Bills", 20_000), entry("Phone", "Bills", 10_000),
            entry("Food", "Living", 100_000),
        )
        val d = diagram(createTransactionsGraph(many), SankeyOptions(topN = 3))
        // Five categories exceed 3, so Phone, Water and Power (smallest first) fold into Bills' "Other"
        // until two categories and the "Other" are left.
        assertEquals(listOf("Rent", "Other", "Food"), d.column(d.columnCount - 1))
        val other = d.nodes.first { it.name == "Other" && it.layer == SankeyLayer.CATEGORY }
        assertEquals(70_000L, other.valueCents)
        assertEquals(listOf("Power", "Water", "Phone"), other.foldedNames)
        assertEquals(setOf("power", "water", "phone"), other.drillDownQuery!!.categoryIds)
        assertEquals(DrillDownDirection.EXPENSE, other.drillDownQuery!!.direction)
        // Cents add up.
        assertEquals(470_000L, d.nodes.filter { it.layer == SankeyLayer.CATEGORY }.sumOf { it.valueCents })
    }

    @Test fun `global sort uses one Other per layer and lists it last`() {
        val many = listOf(
            entry("Rent", "Bills", 300_000), entry("Power", "Bills", 40_000), entry("Water", "Bills", 20_000),
            entry("Food", "Living", 100_000), entry("Gym", "Living", 5_000),
        )
        val d = diagram(createTransactionsGraph(many), SankeyOptions(topN = 3, sort = SankeySort.GLOBAL))
        val categories = d.nodes.filter { it.layer == SankeyLayer.CATEGORY }
        assertEquals(listOf("Rent", "Food", "Other"), categories.map { it.name })
        assertEquals(65_000L, categories.last().valueCents)
    }

    @Test fun `budget order follows the budget's groups and categories`() {
        val d = diagram(
            createTransactionsGraph(entries),
            SankeyOptions(sort = SankeySort.BUDGET_ORDER),
            SankeyBudgetOrder(listOf("living" to listOf("food"), "bills" to listOf("power", "rent"))),
        )
        assertEquals(listOf("Living", "Bills"), d.column(3))
        assertEquals(listOf("Food", "Power", "Rent"), d.column(4))
    }

    @Test fun `payees fold into their income category's Other`() {
        val payees = listOf(
            entry("Salary", "Income", 100, payee = "p1", income = true, negative = false),
            entry("Salary", "Income", 200, payee = "p2", income = true, negative = false),
            entry("Salary", "Income", 300, payee = "p3", income = true, negative = false),
        )
        val d = diagram(createTransactionsGraph(payees), SankeyOptions(topN = 2))
        assertEquals(listOf("P3", "Other"), d.column(0))
        assertEquals(300L, d.nodes.first { it.name == "Other" }.valueCents)
    }

    @Test fun `each node's share is its part of the whole layer`() {
        val d = diagram(createTransactionsGraph(entries))
        val bills = d.nodes.first { it.name == "Bills" }
        assertEquals(340_000.0 / 440_000.0, bills.share, 1e-9)
        assertEquals(1.0, d.nodes.first { it.name == "CHK" }.share, 0.0)
    }

    @Test fun `drill-down covers the node's categories and range`() {
        val d = diagram(createTransactionsGraph(entries))
        val bills = d.nodes.first { it.name == "Bills" }.drillDownQuery!!
        assertEquals(setOf("rent", "power"), bills.categoryIds)
        assertEquals(20260401, bills.startDate)
        assertEquals(DrillDownDirection.EXPENSE, bills.direction)
        val acme = d.nodes.first { it.name == "ACME" }.drillDownQuery!!
        assertEquals("acme", acme.payeeId)
        assertEquals(DrillDownDirection.INCOME, acme.direction)
        val chk = d.nodes.first { it.name == "CHK" }.drillDownQuery!!
        assertEquals(setOf("chk"), chk.accountIds)
        assertNull(d.nodes.first { it.name == "CHK" }.drillDownQuery!!.categoryIds)
    }

    @Test fun `a budgeted graph runs income into available funds, budgeted and the groups`() {
        val budget = listOf(
            SankeyBudgetEntry("Income", "gi", "Salary", "salary", isIncome = true, value = 500_000),
            SankeyBudgetEntry("Bills", "bills", "Rent", "rent", isIncome = false, value = 300_000),
            SankeyBudgetEntry("Bills", "bills", "Power", "power", isIncome = false, value = 40_000),
            SankeyBudgetEntry("Living", "living", "Fund", "fund", isIncome = false, value = -20_000),
        )
        val totals = SankeyBudgetTotals(
            toBudget = 100_000, forNextMonth = 30_000, fromPreviousMonth = 50_000, lastMonthOverspent = -10_000,
            previousMonthLabel = "Mar 2026", nextMonthLabel = "May 2026",
        )
        val options = SankeyOptions(budgeted = true, layerFrom = SankeyLayer.INCOME_CATEGORY)
        val d = buildSankeyDiagram(createBudgetGraph(budget, totals), options, SankeyBudgetOrder(), 0, 0, budgeted = true)
        assertEquals(
            mapOf(
                "Salary>Available funds" to 500_000L, "From Mar 2026>Available funds" to 50_000L,
                "From Fund>Budgeted" to 20_000L,
                "Available funds>Budgeted" to 320_000L, // 300 000 + 40 000 assigned, less the 20 000 taken from Fund
                "Available funds>To budget" to 100_000L, "Available funds>For May 2026" to 30_000L,
                "Available funds>Overspent" to 10_000L,
                "Budgeted>Bills" to 340_000L, "Bills>Rent" to 300_000L, "Bills>Power" to 40_000L,
            ),
            d.flows(),
        )
        assertEquals(SankeyNodeRole.TO_BUDGET, d.nodes.first { it.name == "To budget" }.role)
        assertEquals(SankeyNodeRole.NEGATIVE, d.nodes.first { it.name == "From Fund" }.role)
        assertTrue(d.nodes.all { it.drillDownQuery == null })
        // "From <category>" sits in the account column, "To budget" in the group column, carry-overs and overspent last.
        assertEquals(listOf("Salary", "From Mar 2026"), d.column(0))
        assertEquals(listOf("From Fund", "Available funds"), d.column(1))
        assertEquals(listOf("Budgeted", "Overspent", "For May 2026"), d.column(2))
        assertEquals(listOf("Bills", "To budget"), d.column(3))
    }

    @Test fun `an overbudgeted month draws Overbudgeted into Budgeted`() {
        val totals = SankeyBudgetTotals(-5_000, 0, 0, 0, "Mar 2026", "May 2026")
        val d = buildSankeyDiagram(
            createBudgetGraph(listOf(SankeyBudgetEntry("Bills", "bills", "Rent", "rent", false, 10_000)), totals),
            SankeyOptions(budgeted = true, layerFrom = SankeyLayer.INCOME_CATEGORY), SankeyBudgetOrder(), 0, 0, true,
        )
        assertEquals(5_000L, d.flows()["Overbudgeted>Budgeted"])
        assertEquals(5_000L, d.flows()["Available funds>Budgeted"]) // 10 000 assigned less the 5 000 overbudgeted
    }
}
