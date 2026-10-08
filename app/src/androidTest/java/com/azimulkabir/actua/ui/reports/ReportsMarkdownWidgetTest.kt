package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** actua#957: the Markdown widget renders formatted text like upstream's `MarkdownCard`. */
@RunWith(AndroidJUnit4::class)
class ReportsMarkdownWidgetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theDefaultWidgetContentShowsFormattedTextInsteadOfMarkdownSyntax() {
        val widget = ReportWidget(
            id = "notes", kind = ReportWidgetKind.MARKDOWN, name = "Notes",
            markdown = "### Text Widget\n\nEdit this widget to change the **markdown** content.",
        )
        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(ReportDashboardPage("main", "Main", listOf(widget)))),
                    hideDecimalPlaces = false,
                )
            }
        }

        compose.onNodeWithText("Text Widget").assertIsDisplayed()
        compose.onNodeWithText("Edit this widget to change the markdown content.").assertIsDisplayed()
        compose.onNodeWithText("###", substring = true).assertDoesNotExist()
        compose.onNodeWithText("**", substring = true).assertDoesNotExist()
    }
}
