package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleEditorData
import com.azimulkabir.actua.data.rules.RuleValue
import org.junit.Assert.assertEquals
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers issue #741: a schedule's "Edit as rule" opens its rule directly and returns on close. */
@RunWith(AndroidJUnit4::class)
class RulesScreenScheduleRuleTest {
    @get:JUnitRule
    val compose = createComposeRule()

    private val scheduleRule = Rule(
        id = "schedule-rule",
        stage = Rule.Stage.DEFAULT,
        conditionsOp = Rule.ConditionsOp.AND,
        conditions = listOf(Rule.Condition("is", "payee", RuleValue.Text("payee-id"))),
        actions = listOf(Rule.Action("link-schedule", null, RuleValue.Text("schedule-id"))),
    )

    @Test
    fun opensTheScheduleRuleAndReturnsWhenCancelled() {
        var returned = 0
        compose.setContent {
            MaterialTheme {
                RulesScreen(
                    rules = listOf(scheduleRule),
                    supported = true,
                    scheduleOwnedRuleIds = setOf(scheduleRule.id),
                    editorData = RuleEditorData(),
                    onBack = {},
                    onSave = { _, _ -> },
                    onDelete = { _, _ -> },
                    initialRuleId = scheduleRule.id,
                    onInitialRuleClosed = { returned++ },
                )
            }
        }

        compose.onNodeWithText("Edit rule").assertExists()
        compose.onNodeWithText("Delete rule").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, returned)
    }
}
