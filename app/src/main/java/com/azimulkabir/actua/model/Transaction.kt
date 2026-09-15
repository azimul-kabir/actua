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

/** A copy of this transaction detached from its schedule link and split children, ready to be saved as a new one. */
fun Transaction.asDuplicate(): Transaction = copy(
    id = "",
    reconciled = false,
    rulesApplied = true,
    scheduleId = null,
    splits = splits.map { it.copy(childId = null) },
)
