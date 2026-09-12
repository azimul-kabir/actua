package com.azimulkabir.actua.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetTargetCodecTest {
    @Test fun supportedTargetsRoundTripThroughActualGoalDef() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SPENDING, 2_000_000, "2026-09"),
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 100_000, startingDate = "2026-09-01"),
            BudgetTarget(BudgetTarget.Type.BY_DATE, 1_200_000, "2027-09"),
            BudgetTarget(BudgetTarget.Type.REFILL, 50_000),
            BudgetTarget(BudgetTarget.Type.WEEKLY_SPENDING, 5_000, startingDate = "2026-09-01"),
            BudgetTarget(BudgetTarget.Type.AVERAGE, averageMonths = 6),
        )
        targets.forEach { target ->
            val encoded = target.toGoalDef()
            assertNotNull(JSONArray(encoded))
            assertEquals(target, BudgetTarget.fromGoalDef(encoded, "ui"))
        }
    }

    @Test fun notesAndUnknownVisualTemplatesAreNotClaimedByEditor() {
        val periodic = BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 10_000,
            startingDate = "2026-09-01").toGoalDef()
        assertEquals(null, BudgetTarget.fromGoalDef(periodic, "notes"))
        assertEquals(null, BudgetTarget.fromGoalDef("[{\"type\":\"percentage\",\"directive\":\"template\"}]", "ui"))
    }

    @Test fun multipleSupportedAutomationsRoundTripAsOneGoalDefinition() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 10_000, startingDate = "2026-09-01", priority = 2),
            BudgetTarget(BudgetTarget.Type.BY_DATE, 120_000, targetMonth = "2027-09", priority = 3),
            BudgetTarget(BudgetTarget.Type.AVERAGE, averageMonths = 3),
        )
        val encoded = requireNotNull(BudgetAutomationDocument.encode(targets))
        val decoded = BudgetAutomationDocument.decode(encoded, "ui")
        assertEquals(targets, decoded.supported)
        assertEquals(emptyList<String>(), decoded.unsupportedTypes)
        assertEquals(true, decoded.editable)
    }

    @Test fun advancedAndNotesManagedDocumentsStayLocked() {
        val advanced = BudgetAutomationDocument.decode(
            "[{\"type\":\"periodic\",\"directive\":\"template\",\"priority\":1,\"amount\":100,\"period\":{\"period\":\"month\",\"amount\":1}},{\"type\":\"percentage\",\"directive\":\"template\"}]",
            "ui",
        )
        assertEquals(1, advanced.supported.size)
        assertEquals(listOf("percentage"), advanced.unsupportedTypes)
        assertEquals(true, advanced.hasUnsupported)

        val notes = BudgetAutomationDocument.decode(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 10_000).toGoalDef(), "notes",
        )
        assertEquals(false, notes.editable)
        assertEquals(true, notes.hasUnsupported)
    }
}
