package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.SankeyLayer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sankey card options read from the saved widget (actua#1358, actua#1788). */
class SankeyOptionsTest {
    @Test fun `options are read from the widget meta and default to upstream's`() {
        val plain = SankeyOptions()
        assertEquals(plain, SankeyOptions.from(null))
        assertEquals(plain, SankeyOptions.from(JSONObject("""{"mode":"spent"}""")))
        assertEquals(15, plain.topN)
        assertEquals(SankeyLayer.PAYEE, plain.layerFrom)
        assertEquals(SankeyLayer.CATEGORY, plain.layerTo)

        val read = SankeyOptions.from(JSONObject(
            """{"topNcategories":5,"categorySort":"budget-order","showPercentages":true,"groupAccounts":true,"showTransfers":true,""" +
                """"layerFrom":"account","layerTo":"category_group"}""",
        ))
        assertEquals(
            SankeyOptions(
                topN = 5, sort = SankeySort.BUDGET_ORDER, showPercentages = true, groupAccounts = true, showTransfers = true,
                layerFrom = SankeyLayer.ACCOUNT, layerTo = SankeyLayer.CATEGORY_GROUP,
            ),
            read,
        )
        assertEquals(SankeySort.GLOBAL, SankeyOptions.from(JSONObject("""{"categorySort":"global"}""")).sort)
        assertEquals(SankeySort.PER_GROUP, SankeyOptions.from(JSONObject("""{"categorySort":"per-group"}""")).sort)
        // A limit that is not positive means the default.
        assertEquals(15, SankeyOptions.from(JSONObject("""{"topNcategories":0}""")).topN)
    }

    @Test fun `a layer range needs both ends, and budgeted cards start at income categories`() {
        // Upstream uses a saved range only when layerFrom and layerTo are both layers.
        val oneEnd = SankeyOptions.from(JSONObject("""{"layerFrom":"account"}"""))
        assertEquals(SankeyLayer.PAYEE to SankeyLayer.CATEGORY, oneEnd.layerFrom to oneEnd.layerTo)
        val bogus = SankeyOptions.from(JSONObject("""{"layerFrom":"nope","layerTo":"category"}"""))
        assertEquals(SankeyLayer.PAYEE, bogus.layerFrom)
        val budgeted = SankeyOptions.from(JSONObject("""{"mode":"budgeted"}"""))
        assertEquals(true, budgeted.budgeted)
        assertEquals(SankeyLayer.INCOME_CATEGORY to SankeyLayer.CATEGORY, budgeted.layerFrom to budgeted.layerTo)
        val saved = SankeyOptions.from(JSONObject("""{"mode":"budgeted","layerFrom":"budget","layerTo":"category_group"}"""))
        assertEquals(SankeyLayer.BUDGET to SankeyLayer.CATEGORY_GROUP, saved.layerFrom to saved.layerTo)
    }

    private val groups = listOf(
        ActualCategoryGroup("gi", "Income", true, false, 1.0, listOf(
            ActualCategory("salary", "Salary", "gi", true, false, 1.0),
            ActualCategory("bonus", "Bonus", "gi", true, false, 2.0),
        )),
        ActualCategoryGroup("g1", "Bills", false, false, 2.0, listOf(
            ActualCategory("rent", "Rent", "g1", false, false, 1.0),
            ActualCategory("power", "Power", "g1", false, false, 2.0),
        )),
        ActualCategoryGroup("g2", "Fun", false, false, 3.0, listOf(ActualCategory("food", "Food", "g2", false, false, 1.0))),
    )
    private val context = RuleContext(
        accountNames = mapOf("a" to "Checking", "b" to "Savings"),
        categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
        categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
        categoryGroupNames = groups.associate { it.id to it.name },
        payeeNames = mapOf("acme" to "Acme", "shop" to "Shop"),
    )
    private val incomeIds = setOf("salary", "bonus")

    private fun tx(id: String, account: String, amount: Long, cat: String?, payee: String? = null, transferTo: String? = null) = ActualTransaction(
        id, account, 20260410, amount, payee, null, cat, null, null, false, false, transferTo, false, null, false, null, null,
        null, null)

    private val frame = """"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}"""
    private fun widget(rows: List<ActualTransaction>, extra: String = ""): ReportWidget = CoreReportEngine.compute(
        DashboardWidgetRow("w", "sankey-card", "{$frame$extra}"), rows, context, incomeIds,
    )

    private fun ReportWidget.flows() = sankey!!.links.associate { "${sankey!!.nodes[it.source].name}>${sankey!!.nodes[it.target].name}" to it.cents }

    private val rows = listOf(
        tx("1", "a", 500_000, "salary", "acme"), tx("2", "a", -300_000, "rent"), tx("3", "b", 100_000, "rent", "shop"),
        tx("4", "a", -40_000, "power"), tx("5", "a", -20_000, "food"),
    )

    @Test fun `payees are the first layer by default and each account is drawn`() {
        val w = widget(rows)
        assertEquals(
            listOf(
                SankeyLayer.PAYEE, SankeyLayer.INCOME_CATEGORY, SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY_GROUP, SankeyLayer.CATEGORY,
            ),
            w.sankey!!.columnLayers.distinct(),
        )
        // Rent nets per account and payee: 300 000 out of Checking, and 100 000 in on Savings from Shop.
        assertEquals(
            mapOf(
                "Acme>Salary" to 500_000L, "Salary>Checking" to 500_000L,
                "Shop>Rent" to 100_000L, "Rent>Savings" to 100_000L,
                "Checking>Bills" to 340_000L, "Checking>Fun" to 20_000L,
                "Bills>Rent" to 300_000L, "Bills>Power" to 40_000L, "Fun>Food" to 20_000L,
            ),
            w.flows(),
        )
    }

    @Test fun `layerFrom and layerTo choose the drawn layers`() {
        val accounts = widget(rows, ""","layerFrom":"account","layerTo":"category_group"""")
        assertEquals(listOf(SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY_GROUP), accounts.sankey!!.columnLayers)
        assertEquals(mapOf("Checking>Bills" to 340_000L, "Checking>Fun" to 20_000L), accounts.flows())

        val categories = widget(rows, ""","layerFrom":"category_group","layerTo":"category"""")
        assertEquals(setOf("Bills>Rent", "Bills>Power", "Fun>Food"), categories.flows().keys)
        assertEquals(setOf("rent"), categories.sankey!!.nodes.first { it.name == "Rent" }.drillDownQuery!!.categoryIds)
    }

    @Test fun `groupAccounts nets a category across accounts into one Income account`() {
        val split = listOf(
            tx("1", "a", 500_000, "salary", "acme"), tx("2", "a", -300_000, "rent"), tx("3", "b", 100_000, "rent"),
        )
        val separate = widget(split)
        assertEquals(setOf("Checking", "Savings"), separate.sankey!!.nodes.filter { it.layer == SankeyLayer.ACCOUNT }.map { it.name }.toSet())
        assertEquals(300_000L, separate.flows()["Bills>Rent"])

        val grouped = widget(split, ""","groupAccounts":true""")
        assertEquals(listOf("Income"), grouped.sankey!!.nodes.filter { it.layer == SankeyLayer.ACCOUNT }.map { it.name })
        // Rent nets across accounts, -300 000 + 100 000, so the refund on Savings no longer shows by itself.
        assertEquals(200_000L, grouped.flows()["Bills>Rent"])
        assertEquals(200_000L, grouped.flows()["Income>Bills"])
    }

    @Test fun `showTransfers draws transfers between accounts and groupAccounts hides them`() {
        val transfer = listOf(
            tx("t1", "a", -50_000, null, transferTo = "t2"), tx("t2", "b", 50_000, null, transferTo = "t1"),
        )
        val off = widget(rows + transfer)
        assertTrue(off.flows().keys.none { it == "Checking>Savings" })

        val on = widget(rows + transfer, ""","showTransfers":true""")
        assertEquals(50_000L, on.flows()["Checking>Savings"])
        assertEquals(2, on.sankey!!.columnLayers.count { it == SankeyLayer.ACCOUNT })

        val grouped = widget(rows + transfer, ""","showTransfers":true,"groupAccounts":true""")
        assertTrue(grouped.flows().keys.none { it.endsWith(">Savings") })
    }

    @Test fun `layers over the default limit of 15 fold into Other`() {
        val many = (1..18).map { ActualCategory("c$it", "Cat $it", "g", false, false, it.toDouble()) }
        val manyGroup = listOf(ActualCategoryGroup("g", "Many", false, false, 1.0, many))
        val ctx = RuleContext(
            accountNames = mapOf("a" to "Checking"),
            categoryNames = many.associate { it.id to it.name },
            categoryGroupIds = many.associate { it.id to "g" },
            categoryGroupNames = manyGroup.associate { it.id to it.name },
        )
        val spending = many.mapIndexed { i, c -> tx("t$i", "a", -(i + 1) * 1_000L, c.id) }
        val w = CoreReportEngine.compute(DashboardWidgetRow("w", "sankey-card", "{$frame}"), spending, ctx, emptySet())
        val categories = w.sankey!!.nodes.filter { it.layer == SankeyLayer.CATEGORY }
        // 14 categories and one "Other" in the group; the cents still add up.
        assertEquals(15, categories.size)
        assertEquals(spending.sumOf { -it.amountCents }, categories.sumOf { it.valueCents })
        assertEquals(4, categories.single { it.name == "Other" }.foldedNames.size)

        val all = CoreReportEngine.compute(
            DashboardWidgetRow("w", "sankey-card", """{$frame,"topNcategories":100000}"""), spending, ctx, emptySet(),
        )
        assertEquals(18, all.sankey!!.nodes.count { it.layer == SankeyLayer.CATEGORY })
    }

    @Test fun `showPercentages is carried to the diagram`() {
        assertEquals(false, widget(rows).sankey!!.showPercentages)
        assertEquals(true, widget(rows, ""","showPercentages":true""").sankey!!.showPercentages)
    }

    @Test fun `budget order lists groups and categories as the budget does`() {
        val w = widget(rows, ""","categorySort":"budget-order","layerFrom":"category_group","layerTo":"category"""")
        assertEquals(listOf("Bills", "Fun"), w.sankey!!.nodes.filter { it.layer == SankeyLayer.CATEGORY_GROUP }.map { it.name })
        assertEquals(listOf("Rent", "Power", "Food"), w.sankey!!.nodes.filter { it.layer == SankeyLayer.CATEGORY }.map { it.name })
    }
}
