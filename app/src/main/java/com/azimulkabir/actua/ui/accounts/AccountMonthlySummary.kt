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
    /**
     * A transfer between two on-budget accounts (or two off-budget accounts) nets to zero and is
     * excluded. A transfer crossing the on/off-budget boundary leaves the budgeted pool of money,
     * so its on-budget leg counts as income/expense by amount sign, matching the off-budget leg
     * exclusion Actual's own reports use (e.g. the Spending widget, see #531); the off-budget leg
     * itself is skipped to avoid double-counting the same real-world movement.
     */
    fun calculate(transactions: List<Transaction>, offBudgetAccountNames: Set<String> = emptySet()): AccountMonthlySummary {
        var income = 0L
        var expenses = 0L
        transactions.forEach { transaction ->
            if (transaction.type == Type.TRANSFER) {
                val ownOffBudget = transaction.account in offBudgetAccountNames
                val counterpartOffBudget = transaction.transferAccount?.let { it in offBudgetAccountNames } ?: ownOffBudget
                if (!ownOffBudget && counterpartOffBudget) {
                    if (transaction.amountCents >= 0) income += transaction.amountCents else expenses -= transaction.amountCents
                }
                return@forEach
            }
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
