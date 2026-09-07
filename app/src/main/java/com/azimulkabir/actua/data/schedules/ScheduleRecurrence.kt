package com.azimulkabir.actua.data.schedules

import org.json.JSONObject
import kotlin.math.abs

data class RecurConfig(
    val frequency: Frequency, val interval: Int, val start: DayDate,
    val patterns: List<Pattern> = emptyList(), val skipWeekend: Boolean = false,
    val weekendSolveMode: String = "after", val endMode: String = "never",
    val endOccurrences: Int? = null, val endDate: DayDate? = null,
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
    data class Pattern(val type: String, val value: Int)

    fun toJson(): JSONObject = JSONObject().put("start", start.iso)
        .put("frequency", frequency.name.lowercase()).put("interval", interval.coerceAtLeast(1))
        .put("skipWeekend", skipWeekend).put("weekendSolveMode", weekendSolveMode).put("endMode", endMode)
        .also { json ->
            if (patterns.isNotEmpty()) json.put("patterns", org.json.JSONArray().also { array ->
                patterns.forEach { array.put(JSONObject().put("type", it.type).put("value", it.value)) }
            })
            endOccurrences?.let { json.put("endOccurrences", it.coerceAtLeast(1)) }
            endDate?.let { json.put("endDate", it.iso) }
        }

    companion object {
        fun parse(json: JSONObject): RecurConfig? {
            val frequency = when (json.optString("frequency")) {
                "daily" -> Frequency.DAILY; "weekly" -> Frequency.WEEKLY
                "monthly" -> Frequency.MONTHLY; "yearly" -> Frequency.YEARLY; else -> return null
            }
            val start = DayDate.fromIso(json.optString("start")) ?: return null
            val interval = when (val raw = json.opt("interval")) {
                null, JSONObject.NULL -> 1
                is Int -> raw.coerceAtLeast(1)
                else -> return null
            }
            val patterns = buildList {
                val array = json.optJSONArray("patterns")
                if (array != null) for (index in 0 until array.length()) {
                    val pattern = array.optJSONObject(index) ?: return null
                    val type = pattern.optString("type")
                    val value = pattern.opt("value") as? Int ?: return null
                    if (value == 0 || (type == "day" && abs(value) > 31) ||
                        (type != "day" && (type !in WEEKDAYS || abs(value) > 5))) return null
                    add(Pattern(type, value))
                }
            }
            val endMode = json.optString("endMode", "never")
            val endOccurrences = (json.opt("endOccurrences") as? Int)
            val endDate = json.optString("endDate").takeIf(String::isNotBlank)?.let(DayDate::fromIso)
            if (endMode == "on_date" && endDate == null) return null
            if (endMode == "after_n_occurrences" && (endOccurrences ?: 0) <= 0) return null
            return RecurConfig(frequency, interval, start, if (frequency == Frequency.MONTHLY) patterns else emptyList(),
                json.optBoolean("skipWeekend", false), json.optString("weekendSolveMode", "after"),
                endMode, endOccurrences, endDate)
        }
        private val WEEKDAYS = setOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
    }
}

object ScheduleRecurrence {
    private const val PERIOD_CAP = 20_000
    private val weekdays = mapOf("SU" to 1, "MO" to 2, "TU" to 3, "WE" to 4, "TH" to 5, "FR" to 6, "SA" to 7)
    private sealed interface Kind { data object Plain : Kind; data class Days(val values: List<Int>) : Kind; data class Weekdays(val values: List<Pair<Int, Int>>) : Kind }
    private data class SubRule(val config: RecurConfig, val kind: Kind)

    fun nextOccurrence(config: RecurConfig, onOrAfter: DayDate): DayDate? {
        var result = subRules(config).mapNotNull { first(it) { date -> date >= onOrAfter } }.minOrNull()
            ?: subRules(config).mapNotNull(::last).maxOrNull() ?: return null
        if (config.skipWeekend && result.isWeekend) result =
            if (config.weekendSolveMode == "before") previousFriday(result) else nextMonday(result)
        return result
    }

    fun upcomingDates(config: RecurConfig, count: Int, from: DayDate): List<DayDate> {
        val dates = mutableListOf<DayDate>(); var cursor = from
        while (dates.size < count) {
            val next = nextOccurrence(config, cursor) ?: break
            if (dates.isEmpty() && next < from.addingDays(-2)) break
            if (dates.lastOrNull()?.let { next <= it } == true) break
            dates += next; cursor = next.addingDays(1)
        }
        return dates
    }

    fun skipSearchStart(nextDate: DayDate, config: RecurConfig): DayDate {
        var date = nextDate
        if (config.skipWeekend && config.weekendSolveMode == "before" && (date.weekday == 6 || date.isWeekend)) date = nextMonday(date)
        return date.addingDays(1)
    }

    fun nextMonday(date: DayDate): DayDate { var result = date; while (result.weekday != 2) result = result.addingDays(1); return result }
    private fun previousFriday(date: DayDate): DayDate { var result = date; while (result.weekday != 6) result = result.addingDays(-1); return result }

    private fun subRules(config: RecurConfig): List<SubRule> {
        if (config.frequency != RecurConfig.Frequency.MONTHLY || config.patterns.isEmpty()) return listOf(SubRule(config, Kind.Plain))
        val result = mutableListOf<SubRule>()
        config.patterns.filter { it.type == "day" }.map { it.value }.takeIf(List<Int>::isNotEmpty)?.let { result += SubRule(config, Kind.Days(it)) }
        config.patterns.filter { it.type != "day" }.map { requireNotNull(weekdays[it.type]) to it.value }
            .takeIf(List<Pair<Int, Int>>::isNotEmpty)?.let { result += SubRule(config, Kind.Weekdays(it)) }
        return result
    }

    private fun first(rule: SubRule, predicate: (DayDate) -> Boolean): DayDate? {
        var count = 0; var result: DayDate? = null
        enumerate(rule) { date ->
            count++
            if (rule.config.endMode == "after_n_occurrences" && count > requireNotNull(rule.config.endOccurrences)) return@enumerate false
            if (rule.config.endMode == "on_date" && date > requireNotNull(rule.config.endDate)) return@enumerate false
            if (predicate(date)) { result = date; false } else true
        }
        return result
    }

    private fun last(rule: SubRule): DayDate? {
        if (rule.config.endMode !in setOf("after_n_occurrences", "on_date")) return null
        var count = 0; var result: DayDate? = null
        enumerate(rule) { date ->
            if (rule.config.endMode == "on_date" && date > requireNotNull(rule.config.endDate)) return@enumerate false
            count++
            if (rule.config.endMode == "after_n_occurrences" && count > requireNotNull(rule.config.endOccurrences)) return@enumerate false
            result = date; true
        }
        return result
    }

    private fun enumerate(rule: SubRule, body: (DayDate) -> Boolean) {
        val config = rule.config
        when (config.frequency) {
            RecurConfig.Frequency.DAILY -> { var date = config.start; repeat(PERIOD_CAP) { if (!body(date)) return; date = date.addingDays(config.interval) } }
            RecurConfig.Frequency.WEEKLY -> { var date = config.start; repeat(PERIOD_CAP) { if (!body(date)) return; date = date.addingDays(7 * config.interval) } }
            RecurConfig.Frequency.YEARLY -> repeat(PERIOD_CAP) { index ->
                val year = config.start.year + index * config.interval
                if (config.start.day <= DayDate.lastDay(year, config.start.month) && !body(DayDate(year, config.start.month, config.start.day))) return
            }
            RecurConfig.Frequency.MONTHLY -> repeat(PERIOD_CAP) { index ->
                val months = config.start.month - 1 + index * config.interval
                val year = config.start.year + months / 12; val month = months % 12 + 1; val last = DayDate.lastDay(year, month)
                val candidates = when (val kind = rule.kind) {
                    Kind.Plain -> if (config.start.day <= last) listOf(DayDate(year, month, config.start.day)) else emptyList()
                    is Kind.Days -> kind.values.map { if (it > 0) it else last + 1 + it }.filter { it in 1..last }.map { DayDate(year, month, it) }
                    is Kind.Weekdays -> kind.values.mapNotNull { nthWeekday(year, month, it.first, it.second) }
                }
                candidates.distinct().sorted().filter { it >= config.start }.forEach { if (!body(it)) return }
            }
        }
    }

    private fun nthWeekday(year: Int, month: Int, weekday: Int, nth: Int): DayDate? {
        val last = DayDate.lastDay(year, month)
        val day = if (nth > 0) 1 + (weekday - DayDate(year, month, 1).weekday + 7) % 7 + (nth - 1) * 7
        else last - (DayDate(year, month, last).weekday - weekday + 7) % 7 + (nth + 1) * 7
        return day.takeIf { it in 1..last }?.let { DayDate(year, month, it) }
    }
}
