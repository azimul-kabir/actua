package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction

/**
 * loot-core `addTransfer`: rules run on a new transfer's other leg (which has no category), and only
 * the resulting `notes`, `cleared` and `schedule` are kept. A schedule a rule links to the other leg
 * is copied to the original leg too.
 */
object TransferLegRules {
    fun apply(
        source: ActualTransaction,
        partner: ActualTransaction,
        rules: List<Rule>,
        context: RuleContext,
    ): Pair<ActualTransaction, ActualTransaction> {
        val input = partner.copy(categoryId = null, categoryName = null, scheduleId = source.scheduleId)
        val ruled = RulesEngine.apply(input, rules, context).transaction
        val schedule = ruled.scheduleId ?: source.scheduleId
        return source.copy(scheduleId = schedule) to
            partner.copy(notes = ruled.notes, cleared = ruled.cleared, scheduleId = schedule)
    }
}
