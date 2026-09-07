package com.azimulkabir.actua.data.schedules

enum class ScheduleStatus { COMPLETED, PAID, DUE, UPCOMING, MISSED, SCHEDULED }

object ScheduleUpcomingLength {
    fun days(raw: String?, today: DayDate): Int {
        val value = raw?.takeIf(String::isNotEmpty) ?: "7"
        val monthStart = DayDate(today.year, today.month, 1)
        return when (value) {
            "currentMonth" -> DayDate.lastDay(today.year, today.month) - today.day
            "oneMonth" -> monthStart.daysUntil(monthStart.addingMonths(1))
            else -> {
                if ('-' !in value) return value.toIntOrNull() ?: 7
                val parts = value.split('-', limit = 2); val amount = parts[0].toIntOrNull()?.coerceAtLeast(1) ?: return 7
                when (parts[1]) {
                    "day" -> amount; "week" -> amount * 7
                    "month" -> monthStart.daysUntil(today.addingMonths(amount)) + 1
                    "year" -> monthStart.daysUntil(today.addingMonths(amount * 12)) + 1
                    else -> 7
                }
            }
        }
    }
}

object ScheduleStatusCalculator {
    fun status(nextDate: DayDate?, completed: Boolean, hasTransaction: Boolean,
        upcomingLength: String?, today: DayDate): ScheduleStatus {
        if (completed) return ScheduleStatus.COMPLETED
        if (hasTransaction) return ScheduleStatus.PAID
        if (nextDate == null) return ScheduleStatus.SCHEDULED
        if (nextDate == today) return ScheduleStatus.DUE
        if (nextDate > today && nextDate <= today.addingDays(ScheduleUpcomingLength.days(upcomingLength, today))) return ScheduleStatus.UPCOMING
        if (nextDate < today) return ScheduleStatus.MISSED
        return ScheduleStatus.SCHEDULED
    }

    fun occurrenceMatchStartDate(nextDate: DayDate, dateOp: String?, postsTransaction: Boolean): DayDate =
        if (dateOp == "is" || postsTransaction) nextDate else nextDate.addingDays(-2)
}
