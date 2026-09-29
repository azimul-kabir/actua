package com.azimulkabir.actua.model

data class Transaction(
    val id: String,
    val date: String,
    val payee: String,
    val category: String,
    val account: String,
    val amount: Int,
    val cleared: Boolean,
    val reconciled: Boolean = false,
    val amountCents: Long = amount.toLong() * 100,
    val type: Type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE,
    val transferAccount: String? = null,
    val notes: String = "",
    val splits: List<SplitLine> = emptyList(),
    val categoryIsIncome: Boolean? = null,
    val rulesApplied: Boolean = false,
    val scheduleId: String? = null,
    /** True when [category] was explicitly picked by the user, so a matching rule must not overwrite it. */
    val categoryIsExplicit: Boolean = false,
    /** True for a synthetic row projecting a schedule's next occurrence — not a posted transaction. */
    val isUpcoming: Boolean = false,
    /** True when [account] is an off-budget account, which Actual never requires a category for. */
    val accountOffBudget: Boolean = false,
)

data class SplitLine(
    val category: String = "",
    val amountCents: Long = 0,
    val notes: String = "",
    val payee: String = "",
    val isOpposite: Boolean = false,
    val childId: String? = null,
    val categoryIsIncome: Boolean? = null,
)

enum class Type { EXPENSE, INCOME, TRANSFER }

/**
 * A list row as a save draft. A transfer form's `account` is the sending account, so an incoming
 * leg (`amountCents >= 0`, the rule the editor and `ActualTransactionFormService` share) swaps its
 * accounts; saving the raw row would reverse the transfer.
 */
fun Transaction.asTransferDraft(): Transaction =
    if (type == Type.TRANSFER && amountCents >= 0 && transferAccount != null) {
        copy(account = transferAccount, transferAccount = account, amountCents = -amountCents, amount = -amount)
    } else this

/** A copy of this transaction detached from its schedule link and split children, ready to be saved as a new one. */
fun Transaction.asDuplicate(): Transaction = copy(
    id = "",
    reconciled = false,
    rulesApplied = true,
    scheduleId = null,
    splits = splits.map { it.copy(childId = null) },
)
