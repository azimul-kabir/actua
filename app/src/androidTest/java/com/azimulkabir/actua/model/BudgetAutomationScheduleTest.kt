package com.azimulkabir.actua.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetAutomationScheduleTest {
    @Test fun scheduleTemplateRoundTripsItsStableReferenceWithoutApproximation() {
        val target = BudgetTarget(
            type = BudgetTarget.Type.SCHEDULE,
            priority = 2,
            scheduleId = "schedule-123",
            scheduleName = "Rent",
        )

        assertEquals(target, BudgetTarget.fromGoalDef(target.toGoalDef(), "ui"))
    }

    @Test fun scheduleTemplateCanRoundTripNameOnlyForNotesCompatibility() {
        val target = BudgetTarget(
            type = BudgetTarget.Type.SCHEDULE,
            scheduleName = "Annual insurance",
        )

        assertEquals(target, BudgetTarget.fromGoalDef(target.toGoalDef(), "ui"))
    }

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
