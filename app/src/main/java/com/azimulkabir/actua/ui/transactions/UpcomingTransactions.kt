package com.azimulkabir.actua.ui.transactions

import androidx.annotation.StringRes
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleRecurrence
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.data.schedules.ScheduleUpcomingLength
import com.azimulkabir.actua.model.Transaction
import java.time.DayOfWeek

// Actual's preview rows (`isForPreview` / `computeSchedulePreviewTransactions` in loot-core
// `shared/schedules.ts`): due, upcoming, missed and paid schedules; completed and far-off ones stay out.
private val upcomingStatuses = setOf(ScheduleStatus.DUE, ScheduleStatus.UPCOMING, ScheduleStatus.MISSED, ScheduleStatus.PAID)

/**
 * Projects schedule occurrences as synthetic, unposted [Transaction] rows, as Actual's
 * `computeSchedulePreviewTransactions` does: a recurring schedule lists every occurrence from its
 * next date through the end of its upcoming window (`custom_upcoming_length`, else
 * [defaultUpcomingLength]); a paid schedule leaves out its already-paid next date. Schedules
 * without an account are included with no account name. Rows sort newest first, then by amount.
 * Dates are shown for [weekendDays]; the occurrences themselves stay Actual's own.
 */
fun upcomingTransactionsFrom(
    schedules: List<ScheduleListItem>,
    today: DayDate = DayDate.today(),
    defaultUpcomingLength: String? = null,
    weekendDays: Set<DayOfWeek> = MonthStartRule.DEFAULT_WEEKEND_DAYS,
): List<Transaction> =
    schedules.flatMap { item ->
        if (item.status !in upcomingStatuses) return@flatMap emptyList()
        val next = item.schedule.nextDate ?: return@flatMap emptyList()
        val windowEnd = today.addingDays(
            ScheduleUpcomingLength.days(
                ScheduleUpcomingLength.effective(item.schedule.customUpcomingLength, defaultUpcomingLength), today,
            ),
        )
        val dates = mutableListOf(next)
        (item.schedule.dateCondition as? ScheduleDateCondition.Recurring)?.config?.let { config ->
            var day = next
            while (day <= windowEnd) {
                val occurrence = ScheduleRecurrence.nextOccurrence(config, day) ?: break
                if (occurrence > windowEnd) break
                if (occurrence in dates) {
                    day = day.addingDays(1)
                    continue
                }
                dates += occurrence
                day = occurrence.addingDays(1)
            }
        }
        if (item.status == ScheduleStatus.PAID) dates.removeAt(0)
        val amountCents = item.schedule.postAmount
        val recurrence = (item.schedule.dateCondition as? ScheduleDateCondition.Recurring)?.config
        dates.map { date ->
            val missed = item.status == ScheduleStatus.MISSED && date == next
            val shown = recurrence?.let { ScheduleRecurrence.displayDate(it, date, weekendDays) } ?: date
            Transaction(
                id = "upcoming-${item.schedule.id}-${date.yyyymmdd}",
                date = shown.yyyymmdd.toString(),
                payee = item.title,
                category = if (missed) "Missed" else "Upcoming",
                account = item.accountName.orEmpty(),
                amount = (amountCents / 100).toInt(),
                cleared = false,
                amountCents = amountCents,
                scheduleId = item.schedule.id,
                isUpcoming = true,
                isMissed = missed,
            )
        }
    }.sortedWith(compareByDescending<Transaction> { it.date }.thenBy { it.amountCents })

/** Actions offered when tapping an upcoming row, matching Actual's scheduled-transaction menu. */
enum class UpcomingTransactionAction(@StringRes val labelRes: Int) {
    POST(R.string.upcoming_post_transaction),
    POST_TODAY(R.string.upcoming_post_transaction_today),
    SKIP(R.string.upcoming_skip_next_scheduled_date),
    COMPLETE(R.string.upcoming_mark_completed),
}

/** As in Actual, recurring schedules can skip their next date and one-off schedules can be completed instead. */
fun upcomingTransactionActions(recurring: Boolean): List<UpcomingTransactionAction> =
    listOf(UpcomingTransactionAction.POST, UpcomingTransactionAction.POST_TODAY) +
        if (recurring) UpcomingTransactionAction.SKIP else UpcomingTransactionAction.COMPLETE

/** Ids of the recurring schedules among [schedules], used to pick which upcoming-row actions apply. */
fun recurringScheduleIds(schedules: List<ScheduleListItem>): Set<String> =
    schedules.filter { it.schedule.isRecurring }.mapTo(mutableSetOf()) { it.schedule.id }
