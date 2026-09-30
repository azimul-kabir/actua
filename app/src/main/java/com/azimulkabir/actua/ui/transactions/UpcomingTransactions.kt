package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Transaction

// Actual's preview rows also keep missed occurrences so they can still be posted or skipped
// (`isForPreview` in loot-core `shared/schedules.ts`); paid, completed and far-off schedules stay out.
private val upcomingStatuses = setOf(ScheduleStatus.DUE, ScheduleStatus.UPCOMING, ScheduleStatus.MISSED)

/** Projects each due/upcoming/missed schedule occurrence as a synthetic, unposted [Transaction] row. */
fun upcomingTransactionsFrom(schedules: List<ScheduleListItem>): List<Transaction> =
    schedules.mapNotNull { item ->
        if (item.status !in upcomingStatuses) return@mapNotNull null
        val date = item.schedule.nextDate ?: return@mapNotNull null
        val accountName = item.accountName ?: return@mapNotNull null
        val amountCents = item.schedule.postAmount
        val missed = item.status == ScheduleStatus.MISSED
        Transaction(
            id = "upcoming-${item.schedule.id}-${date.yyyymmdd}",
            date = date.yyyymmdd.toString(),
            payee = item.title,
            category = if (missed) "Missed" else "Upcoming",
            account = accountName,
            amount = (amountCents / 100).toInt(),
            cleared = false,
            amountCents = amountCents,
            scheduleId = item.schedule.id,
            isUpcoming = true,
            isMissed = missed,
        )
    }

/** Actions offered when tapping an upcoming row, matching Actual's scheduled-transaction menu. */
enum class UpcomingTransactionAction(val label: String) {
    POST("Post transaction"),
    POST_TODAY("Post transaction today"),
    SKIP("Skip next scheduled date"),
    COMPLETE("Mark as completed"),
}

/** As in Actual, recurring schedules can skip their next date and one-off schedules can be completed instead. */
fun upcomingTransactionActions(recurring: Boolean): List<UpcomingTransactionAction> =
    listOf(UpcomingTransactionAction.POST, UpcomingTransactionAction.POST_TODAY) +
        if (recurring) UpcomingTransactionAction.SKIP else UpcomingTransactionAction.COMPLETE

/** Ids of the recurring schedules among [schedules], used to pick which upcoming-row actions apply. */
fun recurringScheduleIds(schedules: List<ScheduleListItem>): Set<String> =
    schedules.filter { it.schedule.isRecurring }.mapTo(mutableSetOf()) { it.schedule.id }
