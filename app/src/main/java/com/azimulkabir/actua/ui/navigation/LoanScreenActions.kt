package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.LoanStatus
import com.azimulkabir.actua.ui.transactions.LoanPaymentRequest

/**
 * The loan-account callbacks and derived state for the transactions screen. Kept out of
 * [AppNavigation] because that composable's lambda is already near the JVM method size limit.
 */
internal class LoanScreenActions(
    private val repository: ActuaRepository,
    private val loanAccount: Account?,
    private val mutate: (Int, () -> Boolean) -> Unit,
) {
    fun record(request: LoanPaymentRequest) {
        val account = loanAccount ?: return
        mutate(R.string.navigation_recording_loan_payment) {
            repository.recordLoanPayment(
                account.id, request.fromAccountId, request.paymentCents,
                request.interestCents, request.escrowCents, request.date,
            )
        }
    }

    /** Skips this month's payment target ([month] = `YYYY-MM`), or resumes it with null. */
    fun snooze(month: String?) {
        val account = loanAccount ?: return
        mutate(R.string.navigation_updating_loan_target) { repository.snoozeLoanTarget(account.id, month) }
    }
}

/** The monthly target of the loan on [loanAccount]'s paired category, read once per data change. */
@Composable
internal fun rememberLoanTarget(
    repository: ActuaRepository, loans: List<LoanStatus>, loanAccount: Account?, dataVersion: Int,
): Long? {
    val categoryId = loans.firstOrNull { it.accountId == loanAccount?.id }?.config?.categoryId ?: return null
    return remember(dataVersion, categoryId) {
        repository.loanTargetMonthly(categoryId, java.time.YearMonth.now().toString())
    }
}
