package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAutomationDocumentTest {
    @Test fun scheduleDefinitionsRemainReadOnlyInsteadOfBeingEvaluatedAsZero() {
        val raw = BudgetTarget(
            type = BudgetTarget.Type.SCHEDULE,
            scheduleId = "schedule-123",
        ).toGoalDef()

        val document = BudgetAutomationDocument.decode(raw, "ui")

        assertTrue(document.hasUnsupported)
        assertEquals(listOf("schedule"), document.unsupportedTypes)
        assertTrue(document.supported.isEmpty())
    }

    @Test fun scheduleWithoutReferenceIsRejectedByCodec() {
        val raw = """[{"directive":"template","type":"schedule","priority":1}]"""

        assertEquals(null, BudgetTarget.fromGoalDef(raw, "ui"))
    }
}
