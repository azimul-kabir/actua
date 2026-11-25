package com.azimulkabir.actua.ui.reports

import com.azimulkabir.actua.data.export.TransactionCsv
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import com.azimulkabir.actua.model.SankeyDiagram
import com.azimulkabir.actua.model.SankeyDiagramNode
import com.azimulkabir.actua.model.SankeyLayer
import com.azimulkabir.actua.model.SankeyNodeRole
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

    @Test fun sankeyListsItsNodesLayerByLayer() {
        fun node(name: String, layer: SankeyLayer, column: Int, cents: Long, drill: Boolean = true) = SankeyDiagramNode(
            name, name, layer, SankeyNodeRole.NORMAL, column, cents, 0.0, if (drill) DrillDownQuery(categoryName = name) else null,
        )
        val diagram = SankeyDiagram(
            nodes = listOf(
                node("Rent", SankeyLayer.CATEGORY, 2, 200_00),
                node("Salary", SankeyLayer.INCOME_CATEGORY, 0, 500_00),
                node("Checking", SankeyLayer.ACCOUNT, 1, 500_00, drill = false),
                node("Zero", SankeyLayer.CATEGORY, 2, 0),
            ),
            links = emptyList(), columnLayers = listOf(SankeyLayer.INCOME_CATEGORY, SankeyLayer.ACCOUNT, SankeyLayer.CATEGORY),
        )
        val widget = ReportWidget("w", ReportWidgetKind.SANKEY, "Sankey", valueCents = 500_00, sankey = diagram)
        val table = reportDataTable(widget, money)!!
        assertEquals(listOf("Layer", "Name", "Amount"), table.headers)
        assertEquals(
            listOf(
                listOf("Income categories", "Salary", "$500"), listOf("Accounts", "Checking", "$500"), listOf("Categories", "Rent", "$200"),
            ),
            table.rows.map { it.cells },
        )
        assertEquals(listOf(true, false, true), table.rows.map { it.canDrill })
    }

    @Test fun monteCarloShowsAllFivePercentiles() {
        val widget = ReportWidget(
            "w", ReportWidgetKind.MONTE_CARLO, "MC",
            points = listOf(ReportPoint("2027-05", 3, 1, percentileCents = listOf(100_00, 200_00, 300_00, 400_00, 500_00))),
        )
        assertEquals(listOf("2027-05", "$100", "$200", "$300", "$400", "$500"), reportDataTable(widget, money)!!.rows.single().cells)
    }

    @Test fun csvModeKeepsTheSameRowsWithExactAmountsAndLocaleNeutralNumbers() {
        val widget = ReportWidget(
            "w", ReportWidgetKind.CASH_FLOW, "Cash Flow",
            points = listOf(ReportPoint("2026-05", 500_05, 200_10), ReportPoint("2026-06", 0, 12_34)),
        )
        val shown = reportDataTable(widget, money)!!
        val exact = reportDataTable(widget, TransactionCsv::amount, csv = true)!!
        assertEquals(shown.headers, exact.headers)
        assertEquals(shown.rows.size, exact.rows.size)
        assertEquals(
            listOf(listOf("2026-05", "500.05", "200.10", "299.95"), listOf("2026-06", "0.00", "12.34", "-12.34")),
            exact.rows.map { it.cells },
        )
    }

    @Test fun csvModeMovesUnitsIntoHeadersSoNumbersStayNumeric() {
        val age = ReportWidget("w", ReportWidgetKind.AGE_OF_MONEY, "Age", points = listOf(ReportPoint("2026-05", 34)))
        val ageTable = reportDataTable(age, money, csv = true)!!
        assertEquals(listOf("Period", "Age (days)"), ageTable.headers)
        assertEquals(listOf("2026-05", "34"), ageTable.rows.single().cells)

        val custom = ReportWidget(
            "w", ReportWidgetKind.CUSTOM_REPORT, "Custom",
            categories = listOf(ReportCategory("Rent", 750_00), ReportCategory("Food", 250_00), ReportCategory("Empty", 0)),
        )
        val table = reportDataTable(custom, TransactionCsv::amount, csv = true)!!
        assertEquals(listOf("Name", "Amount", "Share (%)"), table.headers)
        // Rows with no amount are hidden, exactly as on screen.
        assertEquals(listOf(listOf("Rent", "750.00", "75.0"), listOf("Food", "250.00", "25.0")), table.rows.map { it.cells })
    }

    @Test fun csvNumbersIgnoreTheDeviceLocale() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            val custom = ReportWidget(
                "w", ReportWidgetKind.CUSTOM_REPORT, "Custom",
                categories = listOf(ReportCategory("A", 1_00), ReportCategory("B", 2_00)),
            )
            assertEquals("33.3", reportDataTable(custom, TransactionCsv::amount, csv = true)!!.rows.first().cells[2])
        } finally {
            java.util.Locale.setDefault(previous)
        }
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
