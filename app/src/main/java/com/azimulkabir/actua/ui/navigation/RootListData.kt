package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.model.LoanStatus
import com.azimulkabir.actua.model.PickerChoices

/** The account, payee, category, credit card and loan lists the root screen shares, loaded in one pass. */
internal data class RootListData(
    val accounts: List<Account>,
    val hideReconciledAccountIds: Set<String>,
    val categoryNames: List<String>,
    val categoryChoices: PickerChoices,
    val payeeNames: List<String>,
    val suggestedPayees: List<String>,
    val creditCards: List<CreditCardStatus>,
    val loans: List<LoanStatus>,
    val loanSnoozedMonths: Map<String, String>,
) {
    companion object {
        /** What shows before the first load, and for a budget that has not loaded yet. */
        val Empty = RootListData(
            accounts = emptyList(),
            hideReconciledAccountIds = emptySet(),
            categoryNames = emptyList(),
            categoryChoices = PickerChoices.EMPTY,
            payeeNames = emptyList(),
            suggestedPayees = emptyList(),
            creditCards = emptyList(),
            loans = emptyList(),
            loanSnoozedMonths = emptyMap(),
        )

        /** Blocking: call off the main thread. */
        fun load(repository: ActuaRepository): RootListData {
            val loans = repository.loans()
            return RootListData(
                accounts = repository.accounts(),
                hideReconciledAccountIds = repository.hideReconciledAccountIds(),
                categoryNames = repository.categoryNames(),
                categoryChoices = repository.categoryChoices(),
                payeeNames = repository.payeeNames(),
                suggestedPayees = repository.suggestedPayeeNames(),
                creditCards = repository.creditCards(),
                loans = loans,
                loanSnoozedMonths = repository.snoozedMonthsOf(loans),
            )
        }
    }
}
