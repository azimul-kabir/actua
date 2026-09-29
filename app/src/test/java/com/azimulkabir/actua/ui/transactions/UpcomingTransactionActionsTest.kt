package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.ui.transactions.UpcomingTransactionAction.COMPLETE
import com.azimulkabir.actua.ui.transactions.UpcomingTransactionAction.POST
import com.azimulkabir.actua.ui.transactions.UpcomingTransactionAction.POST_TODAY
import com.azimulkabir.actua.ui.transactions.UpcomingTransactionAction.SKIP
import org.junit.Assert.assertEquals
import org.junit.Test

class UpcomingTransactionActionsTest {
    private val date = DayDate(2026, 10, 5)

    private fun item(id: String, condition: ScheduleDateCondition?) = ScheduleListItem(
        ActualScheduleSummary(id, id, null, date, null, null, "account", null, null,
            ScheduleAmountOp.APPROXIMATE, null, condition, false, false, null, null,
            false, null, null, null),
        ScheduleStatus.UPCOMING, "Checking", null,
    )

    @Test fun recurringSchedulesOfferPostAndSkipLikeActual() {
        assertEquals(listOf(POST, POST_TODAY, SKIP), upcomingTransactionActions(recurring = true))
    }

    @Test fun oneOffSchedulesOfferPostAndCompleteLikeActual() {
        assertEquals(listOf(POST, POST_TODAY, COMPLETE), upcomingTransactionActions(recurring = false))
    }

    @Test fun recurringScheduleIdsOnlyIncludesRecurringSchedules() {
        val schedules = listOf(
            item("monthly", ScheduleDateCondition.Recurring(RecurConfig(RecurConfig.Frequency.MONTHLY, 1, date))),
            item("once", null),
        )

        assertEquals(setOf("monthly"), recurringScheduleIds(schedules))
    }
}
