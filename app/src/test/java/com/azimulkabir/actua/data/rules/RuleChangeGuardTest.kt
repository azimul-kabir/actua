package com.azimulkabir.actua.data.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleChangeGuardTest {
    @Test fun fillsAnEmptyFieldFromARule() {
        assertTrue(RuleChangeGuard.shouldApplyRuleChange("category", null, "grocery"))
        assertTrue(RuleChangeGuard.shouldApplyRuleChange("category", "", "grocery"))
        assertTrue(RuleChangeGuard.shouldApplyRuleChange("cleared", false, true))
    }

    @Test fun keepsAManuallyEnteredValue() {
        assertFalse(RuleChangeGuard.shouldApplyRuleChange("category", "groceries", "clothing"))
    }

    @Test fun notesAppendSurvivesRepeatedRuleRunsWithoutDuplicating() {
        val manual = "lunch"
        val afterFirstRun = "lunch #cafe"
        assertTrue(RuleChangeGuard.shouldApplyRuleChange("notes", manual, afterFirstRun))
        // Re-running the same rule on its own output must not duplicate the tag.
        assertFalse(RuleChangeGuard.shouldApplyRuleChange("notes", afterFirstRun, "$afterFirstRun #cafe"))
    }

    @Test fun notesOverwriteIsTreatedAsManualAndKept() {
        assertFalse(RuleChangeGuard.shouldApplyRuleChange("notes", "lunch", "totally different"))
    }
}
