package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
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
        assertEquals(
            listOf("Post transaction", "Post transaction today", "Skip next scheduled date"),
            upcomingTransactionActions(recurring = true).map { it.label },
        )
    }

    @Test fun oneOffSchedulesOfferPostAndCompleteLikeActual() {
        assertEquals(
            listOf("Post transaction", "Post transaction today", "Mark as completed"),
            upcomingTransactionActions(recurring = false).map { it.label },
        )
    }

    @Test fun recurringScheduleIdsOnlyIncludesRecurringSchedules() {
        val schedules = listOf(
            item("monthly", ScheduleDateCondition.Recurring(RecurConfig(RecurConfig.Frequency.MONTHLY, 1, date))),
            item("once", null),
        )

        assertEquals(setOf("monthly"), recurringScheduleIds(schedules))
    }
}
