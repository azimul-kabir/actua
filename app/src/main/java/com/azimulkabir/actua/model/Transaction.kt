package com.azimulkabir.actua.model

data class Transaction(
    val id: String,
    val date: String,
    val payee: String,
    val category: String,
    val account: String,
    val amount: Int,
    val cleared: Boolean,
    val amountCents: Long = amount.toLong() * 100,
    val type: Type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE,
    val transferAccount: String? = null,
    val notes: String = "",
    val splits: List<SplitLine> = emptyList(),
)

data class SplitLine(
    val category: String = "",
    val amountCents: Long = 0,
    val notes: String = "",
    val payee: String = "",
    val isOpposite: Boolean = false,
    val childId: String? = null,
)

enum class Type { EXPENSE, INCOME, TRANSFER }
