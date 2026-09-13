package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import kotlin.math.abs

internal data class RulePreviewChoices(
    val accountNames: Map<String, String>,
    val offBudgetAccountIds: Set<String>,
    val categoryNames: Map<String, String>,
    val payeeNames: Map<String, String>,
)

internal object TransactionRulePreview {
    fun map(draft: Transaction, result: RuleRunResult, choices: RulePreviewChoices): Transaction {
        val changed = result.changedFields
        val preview = result.transaction
        val validAccountId = preview.accountId.takeIf(choices.accountNames::containsKey)
        val account = if ("account" in changed) {
            validAccountId?.let(choices.accountNames::get) ?: draft.account
        } else draft.account
        val offBudget = validAccountId?.let { it in choices.offBudgetAccountIds } == true
        val category = when {
            offBudget -> ""
            "category" !in changed -> draft.category
            preview.categoryId == null -> ""
            else -> choices.categoryNames[preview.categoryId] ?: draft.category
        }
        val payee = when {
            "payee_name" in changed -> result.pendingPayeeName?.trim().orEmpty()
            "payee" !in changed -> draft.payee
            preview.payeeId == null -> ""
            else -> choices.payeeNames[preview.payeeId] ?: draft.payee
        }
        val amount = if ("amount" in changed) abs(preview.amountCents) else abs(draft.amountCents)
        val type = if ("amount" !in changed || preview.amountCents == 0L) draft.type
            else if (preview.amountCents > 0) Type.INCOME else Type.EXPENSE
        return draft.copy(
            account = account,
            category = category,
            payee = payee,
            amount = (amount / 100L).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            amountCents = amount,
            type = type,
            date = if ("date" in changed) preview.date.toString() else draft.date,
            notes = if ("notes" in changed) preview.notes.orEmpty() else draft.notes,
            cleared = if ("cleared" in changed) preview.cleared else draft.cleared,
            rulesApplied = changed.isNotEmpty(),
        )
    }
}
