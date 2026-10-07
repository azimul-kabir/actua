package com.azimulkabir.actua.data.importing

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.rules.RulesEngine

/**
 * Runs rules on imported rows the way Actual's import does: each row's payee is resolved by name
 * before rules run (a new name gets a provisional id), and a payee is created only for a name the
 * row still uses afterwards, so a rule that renames the payee leaves no orphan behind
 * (accounts/sync.ts `normalizeTransactions` → `runRules` → `createNewPayees`).
 */
internal object ImportRules {
    /** A row ready to insert; [createPayeeName] is the payee to resolve or create for it. */
    data class Prepared(val transaction: ActualTransaction, val createPayeeName: String?)

    fun apply(
        drafts: List<ActualTransaction>,
        rules: List<Rule>,
        context: RuleContext,
        existingPayeeId: (String) -> String?,
        newId: () -> String,
    ): List<Prepared> {
        val provisional = mutableMapOf<String, Pair<String, String>>() // lowercase name → (id, name)
        return drafts.mapNotNull { draft ->
            val name = draft.importedPayee?.trim()?.takeIf(String::isNotEmpty)
            val payeeId = name?.let { existingPayeeId(it) ?: provisional.getOrPut(it.lowercase()) { newId() to it }.first }
            val result = RulesEngine.apply(draft.copy(payeeId = payeeId, payeeName = null), rules, context)
            if (result.isDeleted) return@mapNotNull null
            val ruled = result.transaction
            if (result.pendingPayeeName != null) {
                val renamed = result.pendingPayeeName.trim().takeIf(String::isNotEmpty)
                return@mapNotNull Prepared(ruled.copy(payeeId = null), renamed)
            }
            val newName = provisional.values.firstOrNull { it.first == ruled.payeeId }?.second
            if (newName != null) Prepared(ruled.copy(payeeId = null), newName) else Prepared(ruled, null)
        }
    }
}
