package com.azimulkabir.actua.data.schedules

import android.content.Context
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.util.UUID

/** Synchronous Android port of iOS SchedulePoster actor. */
class SchedulePoster(
    context: Context,
    private val database: ActualBudgetDatabase,
    private val transactions: ActualTransactionWriter,
    private val schedules: ActualScheduleWriter,
    private val idFactory: () -> String = { UUID.randomUUID().toString().lowercase() },
) {
    private val preferences = context.applicationContext.getSharedPreferences("actua-schedule-poster", Context.MODE_PRIVATE)
    private var running = false

    @Synchronized
    fun runIfNeeded(budgetId: String, today: DayDate = DayDate.today()): Int {
        require(budgetId.isNotBlank())
        if (running || preferences.getInt(gateKey(budgetId), 0) == today.yyyymmdd) return 0
        running = true
        return try {
            val rows = runCatching { database.fetchSchedules(postableOnly = true) }.getOrElse { return 0 }
            var posted = 0; var clean = true
            rows.forEach { schedule ->
                runCatching { process(schedule, today) }
                    .onSuccess { posted += it }.onFailure { clean = false }
            }
            runCatching { advancePaid(today) }.onFailure { clean = false }
            if (clean) preferences.edit().putInt(gateKey(budgetId), today.yyyymmdd).apply()
            posted
        } finally { running = false }
    }

    private fun process(schedule: ActualSchedule, today: DayDate): Int {
        var current = schedule.nextDate; var posted = 0; var iterations = 0
        while (current <= today && iterations < 200) {
            iterations++
            if (!database.hasScheduleTransaction(schedule.id, current.yyyymmdd)) {
                transactions.createTransaction(ActualTransaction(
                    idFactory(), schedule.accountId, current.yyyymmdd, schedule.amount?.postAmount ?: 0,
                    schedule.payeeId, null, schedule.categoryId, null, null, false, false,
                    null, false, null, false, null, null, schedule.id, null,
                ), runTransfers = true)
                posted++
            }
            // Upstream posts an occurrence due today and stops: the next date moves on in a later
            // run, once the schedule reads as paid.
            if (current == today) break
            val recurrence = (schedule.dateCondition as? ScheduleDateCondition.Recurring)?.config ?: break
            val next = ScheduleRecurrence.nextOccurrence(recurrence, current.addingDays(1)) ?: break
            if (next <= current) break
            schedules.advance(schedule.nextDateRowId, next, schedule.baseNextDateTimestamp)
            current = next
        }
        return posted
    }

    /**
     * The rest of upstream `advanceSchedulesService` for paid schedules (a linked transaction covers
     * the current occurrence) on open accounts: a recurring schedule that doesn't auto-post moves to
     * its next occurrence from today (`setNextDate`), and a one-off schedule whose date has passed
     * is completed. Auto-posting recurring schedules move on in [process].
     */
    private fun advancePaid(today: DayDate) {
        val closed = database.fetchAccounts().filter { it.closed }.mapTo(mutableSetOf()) { it.id }
        val candidates = database.fetchScheduleSummaries().filter {
            !it.completed && it.nextDate != null && it.accountId != null && it.accountId !in closed
        }
        if (candidates.isEmpty()) return
        val paid = database.fetchPaidScheduleIds(candidates)
        candidates.filter { it.id in paid }.forEach { schedule ->
            val current = requireNotNull(schedule.nextDate)
            when (val date = schedule.dateCondition) {
                is ScheduleDateCondition.Recurring -> if (!schedule.postsTransaction) {
                    val next = ScheduleRecurrence.nextOccurrence(date.config, today)
                    if (next != null && next != current) {
                        ScheduleWriteBuilder.nextDate(schedule, next, reset = false, now = 0L)?.let(schedules::apply)
                    }
                }
                is ScheduleDateCondition.Fixed -> if (current < today) {
                    schedules.apply(ScheduleWriteBuilder.columns(schedule.id, "completed" to 1))
                }
                else -> Unit
            }
        }
    }

    private fun gateKey(budgetId: String) = "lastScheduleRun-$budgetId"
}
