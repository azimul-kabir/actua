package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BudgetTargetGoalDefRoundTripTest {
    @Test fun scheduleTargetRoundTripsScheduleIdThroughGoalDef() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "sched-123")
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals(BudgetTarget.Type.SCHEDULE, parsed!!.type)
        assertEquals("sched-123", parsed.scheduleId)
        assertEquals(null, parsed.scheduleName)
    }

    @Test fun scheduleTargetRoundTripsScheduleNameWhenIdIsMissing() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleName = "Car insurance")
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals("Car insurance", parsed!!.scheduleName)
    }

    @Test fun automationDocumentRoundTripsAnEditedScheduleLink() {
        val original = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"schedule","priority":1,"scheduleId":"old-id"}]""",
            "ui",
        )
        assertEquals(1, original.supported.size)
        assertEquals("old-id", original.supported.single().scheduleId)

        val relinked = listOf(original.supported.single().copy(scheduleId = "new-id", scheduleName = "Rent"))
        val encoded = BudgetAutomationDocument.encode(relinked)
        val redecoded = BudgetAutomationDocument.decode(encoded, "ui")

        assertEquals(false, redecoded.hasUnsupported)
        assertEquals("new-id", redecoded.supported.single().scheduleId)
        assertEquals("Rent", redecoded.supported.single().scheduleName)
    }

    @Test fun automationDocumentRoundTripsAClearedScheduleLinkAsRemoval() {
        val original = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"schedule","priority":1,"scheduleId":"old-id"}]""",
            "ui",
        )
        val encoded = BudgetAutomationDocument.encode(emptyList())
        assertEquals(null, encoded)
        assertEquals(1, original.supported.size)
    }
}
