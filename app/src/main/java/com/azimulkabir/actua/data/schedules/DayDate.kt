package com.azimulkabir.actua.data.schedules

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Time-zone-free calendar day used for all schedule math. */
data class DayDate(val year: Int, val month: Int, val day: Int) : Comparable<DayDate> {
    private val date: LocalDate = LocalDate.of(year, month, day)
    val yyyymmdd get() = year * 10_000 + month * 100 + day
    val iso get() = formatIsoDay(yyyymmdd)
    val weekday get() = date.dayOfWeek.value % 7 + 1 // Sunday = 1
    val isWeekend get() = weekday == 1 || weekday == 7
    val dayOfWeek: java.time.DayOfWeek get() = date.dayOfWeek
    val localDate: LocalDate get() = date
    override fun compareTo(other: DayDate) = yyyymmdd.compareTo(other.yyyymmdd)
    fun addingDays(days: Int) = from(date.plusDays(days.toLong()))
    fun addingMonths(months: Int) = from(date.plusMonths(months.toLong()))
    fun daysUntil(other: DayDate) = ChronoUnit.DAYS.between(date, other.date).toInt()

    companion object {
        fun fromYyyymmdd(value: Int): DayDate? = runCatching {
            from(LocalDate.of(value / 10_000, value / 100 % 100, value % 100))
        }.getOrNull()
        /** Also reads dates written in a locale's own digits, which older builds stored on some devices. */
        fun fromIso(value: String): DayDate? = runCatching { from(LocalDate.parse(asciiDigits(value.take(10)))) }.getOrNull()
        fun from(date: LocalDate) = DayDate(date.year, date.monthValue, date.dayOfMonth)
        fun lastDay(year: Int, month: Int) = LocalDate.of(year, month, 1).lengthOfMonth()
        fun today(now: Date = Date(), zone: ZoneId = ZoneId.systemDefault()) =
            from(now.toInstant().atZone(zone).toLocalDate())
    }
}

/**
 * A day as `YYYY-MM-DD` and a month as `YYYY-MM`, always in ASCII digits. These are stored, synced and
 * sent to servers, so they must not follow the device locale (some, like Arabic, use their own digits).
 */
internal fun formatIsoDay(yyyymmdd: Int): String =
    String.format(Locale.ROOT, "%04d-%02d-%02d", yyyymmdd / 10_000, yyyymmdd / 100 % 100, yyyymmdd % 100)

internal fun formatIsoMonth(year: Int, month: Int): String = String.format(Locale.ROOT, "%04d-%02d", year, month)

private fun asciiDigits(value: String): String =
    value.map { c -> Character.digit(c, 10).takeIf { it >= 0 }?.let { '0' + it } ?: c }.joinToString("")
