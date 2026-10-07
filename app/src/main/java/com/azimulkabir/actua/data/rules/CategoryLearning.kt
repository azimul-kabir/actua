package com.azimulkabir.actua.data.rules

/**
 * Actual's category learning (`getProbableCategory` / `updateCategoryRules` in loot-core
 * `transactions/transaction-rules.ts`): after a transaction gets a category, each touched payee's
 * latest transactions decide whether a `payee is X → set category` rule is created or updated.
 */
object CategoryLearning {
    /** Synced preference that turns learning off when it isn't `"true"` (default on). */
    const val PREFERENCE_ID = "learn-categories"

    /** How far back and ahead of the touched transactions the register is read (upstream: 180 days). */
    const val WINDOW_DAYS = 180

    private const val LATEST_TRANSACTIONS = 5
    private const val MIN_SCORE = 3

    /** One non-parent register row, already mapped to live payee/category ids. */
    data class RegisterRow(val id: String, val payeeId: String?, val categoryId: String?)

    /** A touched transaction: added with a category, or whose category was set to a non-null value. */
    data class Touched(val id: String, val payeeId: String?)

    fun enabled(preferenceValue: String?): Boolean = (preferenceValue ?: "true") == "true"

    /**
     * Upstream `getProbableCategory`, including its quirk: the newest row seeds the winner, and only a
     * strictly higher score replaces it, so an uncategorized newest row means no category.
     */
    fun probableCategory(newestFirst: List<RegisterRow>): String? {
        if (newestFirst.isEmpty()) return null
        val scores = newestFirst.mapNotNull(RegisterRow::categoryId).groupingBy { it }.eachCount()
        var winnerCategory = newestFirst.first().categoryId
        var winnerScore = winnerCategory?.let(scores::get)
        for (row in newestFirst.drop(1)) {
            val score = row.categoryId?.let(scores::get) ?: continue
            if (winnerScore != null && score > winnerScore) {
                winnerScore = score
                winnerCategory = row.categoryId
            }
        }
        return winnerCategory.takeIf { (winnerScore ?: 0) >= MIN_SCORE }
    }

    /**
     * Payee → category to learn. [register] is the learning register (open accounts, payees with
     * `learn_categories = 1`) ordered newest first; a payee is learned only when one of its latest
     * five rows is a touched transaction.
     */
    fun categoriesToSet(touched: List<Touched>, register: List<RegisterRow>): Map<String, String> {
        val touchedIds = touched.mapTo(mutableSetOf(), Touched::id)
        val byPayee = register.groupBy(RegisterRow::payeeId)
        val result = linkedMapOf<String, String>()
        touched.mapNotNull(Touched::payeeId).distinct().forEach { payeeId ->
            val latest = byPayee[payeeId].orEmpty().take(LATEST_TRANSACTIONS)
            if (latest.none { it.id in touchedIds }) return@forEach
            probableCategory(latest)?.let { result[payeeId] = it }
        }
        return result
    }

    /**
     * Rules to save: every existing default-stage `payee is/isNot X → set category` setter for the
     * payee is pointed at the learned category, or a new `payee is X` rule is created
     * (upstream `getIsSetterRules(null, 'payee', 'category', …)`).
     */
    fun rulesToSave(learned: Map<String, String>, rules: List<Rule>, newRuleId: () -> String): List<Rule> =
        learned.flatMap { (payeeId, categoryId) ->
            val setters = rules.filter { it.isPayeeCategorySetter(payeeId) }
            if (setters.isEmpty()) {
                listOf(Rule(
                    newRuleId(), Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                    listOf(Rule.Condition("is", "payee", RuleValue.Text(payeeId))),
                    listOf(Rule.Action("set", "category", RuleValue.Text(categoryId))),
                ))
            } else {
                setters.filter { it.actions.single().value.text != categoryId }
                    .map { it.copy(actions = listOf(it.actions.single().copy(value = RuleValue.Text(categoryId)))) }
            }
        }

    private fun Rule.isPayeeCategorySetter(payeeId: String): Boolean {
        val action = actions.singleOrNull() ?: return false
        val condition = conditions.singleOrNull() ?: return false
        return stage == Rule.Stage.DEFAULT &&
            action.op == "set" && action.field == "category" &&
            (condition.op == "is" || condition.op == "isNot") &&
            condition.field == "payee" && condition.value.text == payeeId
    }
}
