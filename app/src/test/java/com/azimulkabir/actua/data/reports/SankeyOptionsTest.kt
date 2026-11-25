package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sankey `topNcategories`, `categorySort` and `showPercentages` (actua#1358). */
class SankeyOptionsTest {
    private fun node(name: String, cents: Long, vararg ids: String) = ReportCategory(
        name, cents, drillDownQuery = DrillDownQuery(
            startDate = 20260401, endDate = 20260430, categoryIds = ids.toSet(),
            direction = DrillDownDirection.EXPENSE, categoryName = name,
        ),
    )

    private val nodes = listOf(node("Rent", 300_000, "rent"), node("Food", 50_001, "food"), node("Bike", 20_000, "bike"), node("Gym", 1_999, "gym"))

    @Test fun `options are read from the widget meta and default to off`() {
        assertEquals(SankeyOptions(), SankeyOptions.from(null))
        assertEquals(SankeyOptions(), SankeyOptions.from(JSONObject("""{"mode":"spent"}""")))
        val read = SankeyOptions.from(JSONObject("""{"topNcategories":5,"categorySort":"budget-order","showPercentages":true}"""))
        assertEquals(SankeyOptions(topN = 5, budgetOrder = true, showPercentages = true), read)
        assertEquals(true, SankeyOptions.from(JSONObject("""{"categorySort":"global"}""")).otherLast)
        assertNull(SankeyOptions.from(JSONObject("""{"topNcategories":0}""")).topN)
    }

    @Test fun `a column at or under the limit is left alone`() {
        assertEquals(nodes, shapeSankeyColumn(nodes, SankeyOptions(topN = 4), emptyList()))
        assertEquals(nodes, shapeSankeyColumn(nodes, SankeyOptions(), emptyList()))
    }

    @Test fun `the smallest nodes fold into one Other that counts toward the limit`() {
        val shaped = shapeSankeyColumn(nodes, SankeyOptions(topN = 3), emptyList())
        assertEquals(listOf("Rent" to 300_000L, "Food" to 50_001L, "Other" to 21_999L), shaped.map { it.name to it.spentCents })
        assertEquals(nodes.sumOf { it.spentCents }, shaped.sumOf { it.spentCents })
    }

    @Test fun `Other merges its categories' drill-down`() {
        val other = shapeSankeyColumn(nodes, SankeyOptions(topN = 3), emptyList()).last()
        assertEquals(setOf("bike", "gym"), other.drillDownQuery!!.categoryIds)
        assertEquals("Other", other.drillDownQuery!!.categoryName)
        assertEquals(DrillDownDirection.EXPENSE, other.drillDownQuery!!.direction)
    }

    @Test fun `a limit of one folds everything and ties fold the earlier node first`() {
        val all = shapeSankeyColumn(nodes, SankeyOptions(topN = 1), emptyList())
        assertEquals(listOf("Other" to 372_000L), all.map { it.name to it.spentCents })
        val tied = listOf(node("A", 10, "a"), node("B", 10, "b"), node("C", 99, "c"))
        assertEquals(listOf("C", "Other"), shapeSankeyColumn(tied, SankeyOptions(topN = 2), emptyList()).map { it.name })
    }

    @Test fun `Other sorts by value, or last for the global sort`() {
        val many = listOf(node("A", 100, "a"), node("B", 90, "b"), node("C", 80, "c"), node("D", 70, "d"))
        // topN 2 keeps A and folds B, C and D into 240.
        assertEquals(listOf("Other", "A"), shapeSankeyColumn(many, SankeyOptions(topN = 2), emptyList()).map { it.name })
        assertEquals(listOf("A", "Other"), shapeSankeyColumn(many, SankeyOptions(topN = 2, otherLast = true), emptyList()).map { it.name })
    }

    @Test fun `budget order follows the budget and leaves Other last`() {
        val order = listOf("Bike", "Rent", "Food", "Gym")
        assertEquals(listOf("Bike", "Rent", "Food", "Gym"), shapeSankeyColumn(nodes, SankeyOptions(budgetOrder = true), order).map { it.name })
        assertEquals(listOf("Rent", "Food", "Other"), shapeSankeyColumn(nodes, SankeyOptions(topN = 3, budgetOrder = true), order).map { it.name })
        assertEquals(listOf("Rent", "Unknown"), shapeSankeyColumn(listOf(node("Unknown", 5, "u"), node("Rent", 1, "rent")), SankeyOptions(budgetOrder = true), order).map { it.name })
    }

    @Test fun `the card applies the options to its income and expense columns`() {
        val groups = listOf(
            ActualCategoryGroup("gi", "Income", true, false, 1.0, listOf(
                ActualCategory("salary", "Salary", "gi", true, false, 1.0),
                ActualCategory("bonus", "Bonus", "gi", true, false, 2.0),
            )),
            ActualCategoryGroup("g1", "Bills", false, false, 2.0, listOf(ActualCategory("rent", "Rent", "g1", false, false, 1.0))),
            ActualCategoryGroup("g2", "Fun", false, false, 3.0, listOf(ActualCategory("food", "Food", "g2", false, false, 1.0))),
            ActualCategoryGroup("g3", "Travel", false, false, 4.0, listOf(ActualCategory("bike", "Bike", "g3", false, false, 1.0))),
        )
        val context = RuleContext(
            categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
            categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
            categoryGroupNames = groups.associate { it.id to it.name },
        )
        val incomeIds = setOf("salary", "bonus")
        fun tx(id: String, amount: Long, cat: String) = ActualTransaction(
            id, "a", 20260410, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null)
        val rows = listOf(
            tx("1", 800_000, "salary"), tx("2", 200_000, "bonus"),
            tx("3", -300_000, "rent"), tx("4", -50_001, "food"), tx("5", -20_000, "bike"),
        )
        val frame = """"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}"""
        fun widget(extra: String) = CoreReportEngine.compute(
            DashboardWidgetRow("w", "sankey-card", "{$frame$extra}"), rows, context, incomeIds,
        )

        val plain = widget("")
        assertEquals(listOf("Bills", "Fun", "Travel"), plain.categories.map { it.name })
        assertEquals(false, plain.sankeyShowPercentages)

        val folded = widget(""","topNcategories":2""")
        assertEquals(listOf("Bills" to 300_000L, "Other" to 70_001L), folded.categories.map { it.name to it.spentCents })
        assertEquals(listOf("Salary" to 800_000L, "Bonus" to 200_000L), folded.incomeCategories.map { it.name to it.spentCents })
        assertEquals(plain.comparisonCents, folded.comparisonCents)
        val one = widget(""","topNcategories":1""")
        assertEquals(listOf("Other" to 1_000_000L), one.incomeCategories.map { it.name to it.spentCents })
        assertEquals(plain.valueCents, one.valueCents)

        val budgetOrder = widget(""","categorySort":"budget-order","showPercentages":true""")
        assertEquals(listOf("Bills", "Fun", "Travel"), budgetOrder.categories.map { it.name })
        assertEquals(listOf("Salary", "Bonus"), budgetOrder.incomeCategories.map { it.name })
        assertEquals(true, budgetOrder.sankeyShowPercentages)
    }
}
