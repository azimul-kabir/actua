package com.azimulkabir.actua.ui.accounts

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type

internal data class AccountMonthlySummary(
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
}

internal object AccountMonthlySummaryCalculator {
    fun calculate(transactions: List<Transaction>): AccountMonthlySummary {
        var income = 0L
        var expenses = 0L
        transactions.filterNot { it.type == Type.TRANSFER }.forEach { transaction ->
            if (transaction.splits.isEmpty()) {
                when (transaction.categoryIsIncome) {
                    true -> income += transaction.amountCents
                    false -> expenses -= transaction.amountCents
                    null -> Unit
                }
            } else {
                transaction.splits.forEach { split ->
                    val signedAmount = if ((transaction.amountCents < 0) xor split.isOpposite) {
                        -split.amountCents
                    } else {
                        split.amountCents
                    }
                    when (split.categoryIsIncome) {
                        true -> income += signedAmount
                        false -> expenses -= signedAmount
                        null -> Unit
                    }
                }
            }
        }
        return AccountMonthlySummary(income, expenses)
    }
}
