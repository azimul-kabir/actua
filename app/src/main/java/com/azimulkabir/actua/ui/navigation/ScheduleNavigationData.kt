package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.home.NonWorkingDays
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.transactions.upcomingTransactionsFrom

/** Keep schedules and their upcoming-window projection together while a refresh is pending. */
internal data class ScheduleNavigationData(
    val schedules: List<ScheduleListItem>,
    val upcomingTransactions: List<Transaction>,
) {
    companion object {
        /** Blocking: the navigation loader runs this under the repository's background-operation guard. */
        fun load(repository: ActuaRepository, nonWorkingDays: NonWorkingDays): ScheduleNavigationData {
            val schedules = repository.schedules(nonWorkingDays = nonWorkingDays)
            return ScheduleNavigationData(
                schedules,
                upcomingTransactionsFrom(
                    schedules,
                    defaultUpcomingLength = repository.upcomingScheduleLength(),
                    nonWorkingDays = nonWorkingDays,
                ),
            )
        }
    }
}
