package com.azimulkabir.actua.data.sync

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException

data class HlcTimestamp(
    val millis: Long,
    val counter: Int,
    val node: String,
) : Comparable<HlcTimestamp> {
    init {
        require(counter in 0..MAX_COUNTER)
    }

    override fun toString(): String {
        val iso = FORMATTER.format(Instant.ofEpochMilli(millis))
        val paddedNode = node.takeLast(16).padStart(16, '0')
        return "$iso-${counter.toString(16).uppercase().padStart(4, '0')}-$paddedNode"
    }

    override fun compareTo(other: HlcTimestamp): Int = toString().compareTo(other.toString())

    fun hash(): Int = MurmurHash3.hash(toString()).toInt()

    companion object {
        const val MAX_COUNTER = 0xffff
        private const val MAX_MILLIS = 253_402_300_800_000L
        private val FORMATTER = DateTimeFormatterBuilder().appendInstant(3).toFormatter().withZone(ZoneOffset.UTC)
        private val PATTERN = Regex(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z)-([0-9A-Fa-f]{1,4})-([^-]{1,16})$",
        )

        val ZERO = HlcTimestamp(0, 0, "0000000000000000")
        val MAX = HlcTimestamp(MAX_MILLIS - 1, MAX_COUNTER, "FFFFFFFFFFFFFFFF")

        fun parse(value: String): HlcTimestamp? {
            val match = PATTERN.matchEntire(value) ?: return null
            return try {
                val millis = Instant.parse(match.groupValues[1]).toEpochMilli()
                if (millis !in 0 until MAX_MILLIS) return null
                HlcTimestamp(
                    millis = millis,
                    counter = match.groupValues[2].toInt(16),
                    node = match.groupValues[3],
                )
            } catch (_: DateTimeParseException) {
                null
            }
        }

        fun since(isoTimestamp: String): String = "$isoTimestamp-0000-0000000000000000"

        fun minutesSinceEpoch(value: String): Long? {
            if (value.length < 16) return null
            fun number(start: Int, end: Int): Int? = value.substring(start, end).toIntOrNull()
            if (value.getOrNull(4) != '-' || value.getOrNull(7) != '-' ||
                value.getOrNull(10) != 'T' || value.getOrNull(13) != ':'
            ) return null
            val year = number(0, 4) ?: return null
            val month = number(5, 7) ?: return null
            val day = number(8, 10) ?: return null
            val hour = number(11, 13) ?: return null
            val minute = number(14, 16) ?: return null
            if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
            return daysSinceEpoch(year, month, day) * 1_440 + hour * 60L + minute
        }

        private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
            val shiftedYear = year - if (month <= 2) 1 else 0
            val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
            val yearOfEra = shiftedYear - era * 400
            val dayOfYear = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era.toLong() * 146_097 + dayOfEra - 719_468
        }
    }
}
