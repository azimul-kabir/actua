package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetNoteAutomationParserTest {
    @Test fun parsesSupportedNoteTemplatesAndGoal() {
        val parsed = BudgetNoteAutomationParser.parse(
            """
            Power bill
            #template-2 copy from 12 months ago
            #template average 3 months
            #goal 500
            """.trimIndent(),
        )

        assertTrue(parsed.valid)
        assertEquals(
            listOf(
                BudgetTarget(BudgetTarget.Type.COPY, lookBackMonths = 12, priority = 2),
                BudgetTarget(BudgetTarget.Type.AVERAGE, averageMonths = 3, priority = 1),
                BudgetTarget(BudgetTarget.Type.GOAL, 50_000),
            ),
            parsed.targets,
        )
    }

    @Test fun malformedOrPartiallyUnsupportedNotesAreNotValidForRefresh() {
        val parsed = BudgetNoteAutomationParser.parse(
            """
            #template 100
            #template schedule Rent
            """.trimIndent(),
        )

        assertTrue(!parsed.valid)
        assertEquals(1, parsed.targets.size)
        assertTrue(parsed.errors.single().contains("unsupported"))
    }

    @Test fun notesManagedSupportedDefinitionsRemainEvaluableButNotEditable() {
        val target = BudgetTarget(BudgetTarget.Type.COPY, lookBackMonths = 2)
        val document = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"copy","lookBack":2}]""",
            "notes",
        )

        assertEquals(listOf(target), document.supported)
        assertEquals(emptyList<String>(), document.unsupportedTypes)
        assertEquals(false, document.editable)
        assertTrue(document.hasUnsupported)
    }
}
