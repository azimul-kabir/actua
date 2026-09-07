package com.azimulkabir.actua.data.schedules

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/** Time-zone-free calendar day used for all schedule math. */
data class DayDate(val year: Int, val month: Int, val day: Int) : Comparable<DayDate> {
    private val date: LocalDate = LocalDate.of(year, month, day)
    val yyyymmdd get() = year * 10_000 + month * 100 + day
    val iso get() = "%04d-%02d-%02d".format(year, month, day)
    val weekday get() = date.dayOfWeek.value % 7 + 1 // Sunday = 1
    val isWeekend get() = weekday == 1 || weekday == 7
    override fun compareTo(other: DayDate) = yyyymmdd.compareTo(other.yyyymmdd)
    fun addingDays(days: Int) = from(date.plusDays(days.toLong()))
    fun addingMonths(months: Int) = from(date.plusMonths(months.toLong()))
    fun daysUntil(other: DayDate) = ChronoUnit.DAYS.between(date, other.date).toInt()

    companion object {
        fun fromYyyymmdd(value: Int): DayDate? = runCatching {
            from(LocalDate.of(value / 10_000, value / 100 % 100, value % 100))
        }.getOrNull()
        fun fromIso(value: String): DayDate? = runCatching { from(LocalDate.parse(value.take(10))) }.getOrNull()
        fun from(date: LocalDate) = DayDate(date.year, date.monthValue, date.dayOfMonth)
        fun lastDay(year: Int, month: Int) = LocalDate.of(year, month, 1).lengthOfMonth()
        fun today(now: Date = Date(), zone: ZoneId = ZoneId.systemDefault()) =
            from(now.toInstant().atZone(zone).toLocalDate())
    }
}
