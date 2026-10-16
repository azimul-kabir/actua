package com.azimulkabir.actua.ui.reports

import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDataTableTest {
    private val money: (Long) -> String = { "$" + it / 100 }

    @Test fun cashFlowRowsCarryIncomeExpenseNetAndOpenTheMonth() {
        val query = DrillDownQuery(month = "2026-05")
        val widget = ReportWidget(
            "w", ReportWidgetKind.CASH_FLOW, "Cash Flow",
            points = listOf(ReportPoint("2026-05", 500_00, 200_00, drillDownQuery = query)),
        )
        val table = reportDataTable(widget, money)!!
        assertEquals(listOf("Period", "Income", "Expense", "Net"), table.headers)
        assertEquals(listOf("2026-05", "$500", "$200", "$300"), table.rows.single().cells)
        assertTrue(table.rows.single().canDrill)
        assertEquals(query, table.rows.single().drill!!.drillDownQuery)
    }

    @Test fun rowsWithoutTransactionsCannotDrill() {
        val widget = ReportWidget("w", ReportWidgetKind.AGE_OF_MONEY, "Age", points = listOf(ReportPoint("2026-05", 34)))
        val row = reportDataTable(widget, money)!!.rows.single()
        assertEquals(listOf("2026-05", "34 days"), row.cells)
        assertFalse(row.canDrill)
    }

    @Test fun sankeyListsIncomeThenExpenseNodes() {
        val widget = ReportWidget(
            "w", ReportWidgetKind.SANKEY, "Sankey", valueCents = 500_00,
            incomeCategories = listOf(ReportCategory("Salary", 500_00, listOf("a"))),
            categories = listOf(ReportCategory("Rent", 200_00, listOf("b")), ReportCategory("Zero", 0)),
        )
        val rows = reportDataTable(widget, money)!!.rows
        assertEquals(listOf(listOf("Income", "Salary", "$500"), listOf("Expense", "Rent", "$200")), rows.map { it.cells })
    }

    @Test fun monteCarloShowsAllFivePercentiles() {
        val widget = ReportWidget(
            "w", ReportWidgetKind.MONTE_CARLO, "MC",
            points = listOf(ReportPoint("2027-05", 3, 1, percentileCents = listOf(100_00, 200_00, 300_00, 400_00, 500_00))),
        )
        assertEquals(listOf("2027-05", "$100", "$200", "$300", "$400", "$500"), reportDataTable(widget, money)!!.rows.single().cells)
    }

    @Test fun notesHaveNoTableAndRangeIsOnlyOfferedForTimeSeries() {
        assertNull(reportDataTable(ReportWidget("w", ReportWidgetKind.MARKDOWN, "Notes"), money))
        assertFalse(canExpand(ReportWidgetKind.MARKDOWN))
        assertTrue(canExpand(ReportWidgetKind.NET_WORTH))
        assertTrue(isRangeable(ReportWidgetKind.NET_WORTH))
        assertFalse(isRangeable(ReportWidgetKind.MONTE_CARLO))
        assertFalse(isRangeable(ReportWidgetKind.BALANCE_FORECAST))
    }
}
