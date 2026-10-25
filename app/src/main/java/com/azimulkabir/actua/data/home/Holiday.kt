package com.azimulkabir.actua.data.home

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A non-working date the user listed, with an optional label such as the holiday's name. */
data class Holiday(val date: LocalDate, val label: String = "")

/** Stores a budget's holidays as one JSON array; sorted, one entry per date, malformed entries dropped. */
object HolidayCodec {
    fun encode(holidays: List<Holiday>): String = JSONArray().also { array ->
        normalize(holidays).forEach { holiday ->
            array.put(JSONObject().put("date", holiday.date.toString()).put("label", holiday.label))
        }
    }.toString()

    fun decode(raw: String?): List<Holiday> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val parsed = buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val date = runCatching { LocalDate.parse(entry.optString("date")) }.getOrNull() ?: continue
                add(Holiday(date, entry.optString("label")))
            }
        }
        return normalize(parsed)
    }

    /** Sorted by date; when a date is listed twice the last entry wins, and labels are trimmed. */
    fun normalize(holidays: List<Holiday>): List<Holiday> =
        holidays.associateBy { it.date }.values.map { it.copy(label = it.label.trim()) }.sortedBy { it.date }
}
