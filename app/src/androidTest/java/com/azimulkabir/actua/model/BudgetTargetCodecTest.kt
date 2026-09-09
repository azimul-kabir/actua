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
}
