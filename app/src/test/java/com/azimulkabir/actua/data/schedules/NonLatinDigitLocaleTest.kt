package com.azimulkabir.actua.data.schedules

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** Dates and month keys are stored, synced and sent to servers, so they stay ASCII whatever the device locale. */
class NonLatinDigitLocaleTest {
    private val original = Locale.getDefault()
    private val locales = listOf("ar-EG", "fa-IR", "bn-BD", "mr-IN", "ne-NP").map(Locale::forLanguageTag)

    @Before fun remember() { Locale.setDefault(original) }
    @After fun restore() { Locale.setDefault(original) }

    private fun <T> underEachLocale(block: () -> T): List<T> = locales.map { locale ->
        Locale.setDefault(locale)
        block()
    }

    @Test fun isoDayIsAscii() {
        underEachLocale { DayDate(2026, 11, 12).iso }.forEach { assertEquals("2026-11-12", it) }
    }

    @Test fun formattedDayAndMonthKeysAreAscii() {
        underEachLocale { formatIsoDay(20261112) }.forEach { assertEquals("2026-11-12", it) }
        underEachLocale { formatIsoDay(20260105) }.forEach { assertEquals("2026-01-05", it) }
        underEachLocale { formatIsoMonth(2026, 3) }.forEach { assertEquals("2026-03", it) }
    }

    @Test fun recurrenceJsonMatchesEnUs() {
        val config = RecurConfig(
            RecurConfig.Frequency.MONTHLY, 1, DayDate(2026, 1, 10),
            endMode = "on_date", endDate = DayDate(2027, 2, 3),
        )
        Locale.setDefault(Locale.US)
        val expected = config.toJson().toString()
        underEachLocale { config.toJson().toString() }.forEach { assertEquals(expected, it) }
    }

    @Test fun oneTimeScheduleMatchesEnUs() {
        val source = OneTimeScheduleSource(DayDate(2026, 11, 5), -1_250, "payee-1", "acct-1", null, null)
        fun plan() = ScheduleWriteBuilder.createOneTime(source, "sched", "rule", "next", now = 1_700_000_000_123L)
            .writes.map { it.dataset to it.fields.toString() }
        Locale.setDefault(Locale.US)
        val expected = plan()
        underEachLocale { plan() }.forEach { assertEquals(expected, it) }
    }

    @Test fun datesWrittenInNativeDigitsStillLoad() {
        val arabic = "٢٠٢٦-١١-١٢"
        val bengali = "২০২৬-১১-১২"
        assertEquals(DayDate(2026, 11, 12), DayDate.fromIso(arabic))
        assertEquals(DayDate(2026, 11, 12), DayDate.fromIso(bengali))
        assertEquals(DayDate(2026, 11, 12), DayDate.fromIso("2026-11-12"))
    }

    @Test fun recurrenceWrittenInNativeDigitsStillParses() {
        val json = org.json.JSONObject()
            .put("start", "٢٠٢٦-٠١-١٠").put("frequency", "monthly").put("interval", 1)
            .put("endMode", "on_date").put("endDate", "٢٠٢٧-٠٢-٠٣")
        val config = requireNotNull(RecurConfig.parse(json))
        assertEquals(DayDate(2026, 1, 10), config.start)
        assertEquals(DayDate(2027, 2, 3), config.endDate)
    }

    @Test fun invalidDatesAreStillRejected() {
        assertEquals(null, DayDate.fromIso("not-a-date"))
        assertEquals(null, DayDate.fromIso("٢٠٢٦-١٣-٤٠"))
    }
}
