package com.azimulkabir.actua.data.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HolidayCodecTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun roundTripsSortedByDateWithTrimmedLabels() {
        val encoded = HolidayCodec.encode(
            listOf(Holiday(d(2026, 6, 1), " Eid "), Holiday(d(2026, 2, 21), "Language Martyrs' Day"), Holiday(d(2026, 3, 26))),
        )
        assertEquals(
            listOf(Holiday(d(2026, 2, 21), "Language Martyrs' Day"), Holiday(d(2026, 3, 26), ""), Holiday(d(2026, 6, 1), "Eid")),
            HolidayCodec.decode(encoded),
        )
    }

    @Test
    fun aDateListedTwiceKeepsTheLastLabel() {
        assertEquals(
            listOf(Holiday(d(2026, 3, 26), "Independence Day")),
            HolidayCodec.normalize(listOf(Holiday(d(2026, 3, 26), "old"), Holiday(d(2026, 3, 26), "Independence Day"))),
        )
    }

    @Test
    fun missingOrMalformedStorageDecodesToNoHolidays() {
        assertEquals(emptyList<Holiday>(), HolidayCodec.decode(null))
        assertEquals(emptyList<Holiday>(), HolidayCodec.decode(""))
        assertEquals(emptyList<Holiday>(), HolidayCodec.decode("not json"))
        assertEquals(emptyList<Holiday>(), HolidayCodec.decode("{}"))
    }

    @Test
    fun malformedEntriesAreDroppedAndTheRestKept() {
        val raw = """[{"date":"2026-05-25","label":"Buddha"},{"date":"nope"},{"label":"no date"},42,{"date":"2026-02-30"}]"""
        assertEquals(listOf(Holiday(d(2026, 5, 25), "Buddha")), HolidayCodec.decode(raw))
    }
}
