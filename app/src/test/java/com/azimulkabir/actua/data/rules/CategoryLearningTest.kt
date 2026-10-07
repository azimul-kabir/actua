package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.rules.CategoryLearning.RegisterRow
import com.azimulkabir.actua.data.rules.CategoryLearning.Touched
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of loot-core `getProbableCategory` / `updateCategoryRules` (#894). */
class CategoryLearningTest {
    private fun rows(payee: String, vararg categories: String?) =
        categories.mapIndexed { index, category -> RegisterRow("$payee-$index", payee, category) }

    @Test fun preferenceDefaultsOnAndOnlyTrueEnables() {
        assertTrue(CategoryLearning.enabled(null))
        assertTrue(CategoryLearning.enabled("true"))
        assertFalse(CategoryLearning.enabled("false"))
        assertFalse(CategoryLearning.enabled(""))
    }

    @Test fun probableCategoryNeedsThreeVotes() {
        assertEquals("food", CategoryLearning.probableCategory(rows("p", "food", "food", "fun", "food")))
        assertNull(CategoryLearning.probableCategory(rows("p", "food", "food", "fun", "fun")))
        assertNull(CategoryLearning.probableCategory(emptyList()))
    }

    @Test fun probableCategoryKeepsUpstreamTieAndUncategorizedQuirks() {
        // The newest row seeds the winner; only a strictly higher score replaces it.
        assertEquals("fun", CategoryLearning.probableCategory(rows("p", "fun", "food", "food", "food", "fun", "fun")))
        assertEquals("food", CategoryLearning.probableCategory(rows("p", "fun", "food", "food", "food", "fun")))
        // An uncategorized newest row never gets replaced, so nothing is learned.
        assertNull(CategoryLearning.probableCategory(rows("p", null, "food", "food", "food")))
    }

    @Test fun learnsOnlyWhenATouchedRowIsAmongTheLatestFive() {
        val register = rows("p", "food", "food", "food", "food", "food", "old")
        assertEquals(mapOf("p" to "food"), CategoryLearning.categoriesToSet(listOf(Touched("p-0", "p")), register))
        assertEquals(emptyMap<String, String>(), CategoryLearning.categoriesToSet(listOf(Touched("p-5", "p")), register))
        assertEquals(emptyMap<String, String>(), CategoryLearning.categoriesToSet(listOf(Touched("x", null)), register))
    }

    @Test fun createsAPayeeRuleWhenNoSetterExists() {
        val created = CategoryLearning.rulesToSave(mapOf("p" to "food"), emptyList()) { "new-rule" }.single()
        assertEquals("new-rule", created.id)
        assertEquals(Rule.Stage.DEFAULT, created.stage)
        assertEquals(listOf(Rule.Condition("is", "payee", RuleValue.Text("p"))), created.conditions)
        assertEquals(listOf(Rule.Action("set", "category", RuleValue.Text("food"))), created.actions)
    }

    @Test fun updatesEveryDefaultStageSetterAndSkipsOthers() {
        fun setter(id: String, op: String = "is", stage: Rule.Stage = Rule.Stage.DEFAULT, category: String = "old") = Rule(
            id, stage, Rule.ConditionsOp.AND,
            listOf(Rule.Condition(op, "payee", RuleValue.Text("p"))),
            listOf(Rule.Action("set", "category", RuleValue.Text(category))),
        )
        val twoConditions = setter("two").let {
            it.copy(conditions = it.conditions + Rule.Condition("is", "notes", RuleValue.Text("x")))
        }
        val rules = listOf(
            setter("a"), setter("b", op = "isNot"), setter("same", category = "food"),
            setter("pre", stage = Rule.Stage.PRE), twoConditions,
        )

        val saved = CategoryLearning.rulesToSave(mapOf("p" to "food"), rules) { error("no new rule expected") }

        assertEquals(listOf("a", "b"), saved.map(Rule::id))
        assertTrue(saved.all { it.actions.single().value == RuleValue.Text("food") })
    }
}
