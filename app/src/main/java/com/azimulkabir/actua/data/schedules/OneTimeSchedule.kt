package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.data.budget.model.ActualTransaction

/**
 * How far a future transaction is from today, against the budget's upcoming window. Actual asks for
 * confirmation when the date is beyond the window (`calculateFutureTransactionInfo`), because the
 * schedule then stays out of the upcoming list for a while.
 */
data class OneTimeScheduleWindow(val daysUntil: Int, val upcomingDays: Int) {
    val beyondWindow: Boolean get() = daysUntil > upcomingDays
}

/** Why "Make one-time schedule" doesn't apply to a transaction, or null when it does. */
enum class OneTimeScheduleProblem(val message: String) {
    NOT_FUTURE("Only future-dated transactions can be made into a schedule."),
    SPLIT_LINE("A split line can't be scheduled on its own."),
    ALREADY_SCHEDULED("This transaction already belongs to a schedule."),
    RECONCILED("A reconciled transaction is locked."),
    NO_ACCOUNT("Account is a required field."),
}

object OneTimeSchedule {
    /** Actual's `isFutureTransaction` (date after today), plus the cases its editor leaves out. */
    fun problem(transaction: ActualTransaction, today: DayDate): OneTimeScheduleProblem? = when {
        transaction.parentId != null -> OneTimeScheduleProblem.SPLIT_LINE
        transaction.accountId.isBlank() -> OneTimeScheduleProblem.NO_ACCOUNT
        transaction.scheduleId != null -> OneTimeScheduleProblem.ALREADY_SCHEDULED
        transaction.reconciled -> OneTimeScheduleProblem.RECONCILED
        transaction.date <= today.yyyymmdd -> OneTimeScheduleProblem.NOT_FUTURE
        else -> null
    }

    fun window(transaction: ActualTransaction, today: DayDate, upcomingLength: String?): OneTimeScheduleWindow? {
        val date = DayDate.fromYyyymmdd(transaction.date) ?: return null
        return OneTimeScheduleWindow(
            daysUntil = today.daysUntil(date),
            upcomingDays = ScheduleUpcomingLength.days(ScheduleUpcomingLength.effective(null, upcomingLength), today),
        )
    }

    /** The details Actual copies into the schedule; [children] are a split parent's lines in order. */
    fun source(transaction: ActualTransaction, children: List<ActualTransaction>): OneTimeScheduleSource =
        OneTimeScheduleSource(
            date = requireNotNull(DayDate.fromYyyymmdd(transaction.date)) { "The transaction has an invalid date" },
            amountCents = transaction.amountCents,
            payeeId = transaction.payeeId,
            accountId = transaction.accountId,
            categoryId = transaction.categoryId,
            notes = transaction.notes,
            splits = if (transaction.isParent) {
                children.map { OneTimeScheduleSource.Split(it.amountCents, it.categoryId, it.notes) }
            } else {
                null
            },
        )
}
