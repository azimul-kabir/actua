package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.R
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
            listOf(R.string.upcoming_post_transaction, R.string.upcoming_post_transaction_today, R.string.upcoming_skip_next_scheduled_date),
            upcomingTransactionActions(recurring = true).map { it.labelRes },
        )
    }

    @Test fun oneOffSchedulesOfferPostAndCompleteLikeActual() {
        assertEquals(
            listOf(R.string.upcoming_post_transaction, R.string.upcoming_post_transaction_today, R.string.upcoming_mark_completed),
            upcomingTransactionActions(recurring = false).map { it.labelRes },
        )
    }

    @Test fun actionLabelResourcesKeepActualsMenuTexts() {
        assertEquals("Post transaction", TransactionStringResources.english("upcoming_post_transaction"))
        assertEquals("Post transaction today", TransactionStringResources.english("upcoming_post_transaction_today"))
        assertEquals("Skip next scheduled date", TransactionStringResources.english("upcoming_skip_next_scheduled_date"))
        assertEquals("Mark as completed", TransactionStringResources.english("upcoming_mark_completed"))
    }

    @Test fun recurringScheduleIdsOnlyIncludesRecurringSchedules() {
        val schedules = listOf(
            item("monthly", ScheduleDateCondition.Recurring(RecurConfig(RecurConfig.Frequency.MONTHLY, 1, date))),
            item("once", null),
        )

        assertEquals(setOf("monthly"), recurringScheduleIds(schedules))
    }
}
