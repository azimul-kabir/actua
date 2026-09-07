package com.azimulkabir.actua.data.schedules

import org.json.JSONArray
import org.json.JSONObject

data class ScheduleWritePlan(
    val scheduleId: String, val writes: List<RowWrite>, val conditions: JSONArray? = null,
) {
    data class RowWrite(val dataset: String, val row: String, val fields: LinkedHashMap<String, Any?>)
}

object ScheduleWriteBuilder {
    fun create(fields: ScheduleFormFields, scheduleId: String, ruleId: String, nextDateRowId: String,
        now: Long, today: DayDate): ScheduleWritePlan {
        val conditions = ScheduleConditions.build(fields, JSONArray())
        val date = requireNotNull(fields.date) { "A date is required" }
        val next = requireNotNull(ScheduleConditions.nextDate(date, today)) { "Unsupported recurrence" }
        val actions = JSONArray().put(JSONObject().put("op", "link-schedule").put("value", scheduleId))
        return ScheduleWritePlan(scheduleId, listOf(
            row("rules", ruleId, "stage" to null, "conditions_op" to "and",
                "conditions" to conditions.toString(), "actions" to actions.toString(), "tombstone" to 0),
            row("schedules_next_date", nextDateRowId, "schedule_id" to scheduleId,
                "local_next_date" to next.yyyymmdd, "local_next_date_ts" to now,
                "base_next_date" to next.yyyymmdd, "base_next_date_ts" to now),
            row("schedules", scheduleId, "rule" to ruleId, "name" to fields.normalizedName,
                "posts_transaction" to flag(fields.postsTransaction), "custom_upcoming_length" to fields.customUpcomingLength,
                "completed" to 0, "tombstone" to 0),
        ), conditions)
    }

    fun update(schedule: ActualScheduleSummary, fields: ScheduleFormFields, now: Long, today: DayDate,
        resetRequested: Boolean = false, newNextDateRowId: () -> String, newRuleId: () -> String): ScheduleWritePlan {
        val existingConditions = ScheduleConditions.parse(schedule.conditionsJson)
        val existingActions = ScheduleConditions.parse(schedule.actionsJson)
        val owned = ScheduleConditions.build(fields, existingConditions)
        val merged = ScheduleConditions.merge(existingConditions, owned)
        val writes = mutableListOf<ScheduleWritePlan.RowWrite>()
        val ruleId = schedule.ruleId ?: newRuleId()
        val ruleFields = linkedMapOf<String, Any?>("conditions" to merged.toString())
        if (schedule.ruleId == null) {
            ruleFields["stage"] = null; ruleFields["conditions_op"] = "and"
            ruleFields["actions"] = JSONArray().put(JSONObject().put("op", "link-schedule").put("value", schedule.id)).toString()
            ruleFields["tombstone"] = 0
        } else ScheduleConditions.syncedActions(merged, existingActions)?.let { ruleFields["actions"] = it.toString() }
        writes += ScheduleWritePlan.RowWrite("rules", ruleId, ruleFields)

        val accountChanged = schedule.accountId != fields.accountId
        val dateChanged = schedule.dateCondition != fields.date
        if (resetRequested || accountChanged || dateChanged) {
            val date = requireNotNull(fields.date); val next = requireNotNull(ScheduleConditions.nextDate(date, today))
            val rowId = schedule.nextDateRowId ?: newNextDateRowId()
            val nextFields = linkedMapOf<String, Any?>()
            if (schedule.nextDateRowId == null) {
                nextFields["schedule_id"] = schedule.id
                nextFields["local_next_date"] = next.yyyymmdd
                nextFields["local_next_date_ts"] = now
            }
            nextFields["base_next_date"] = next.yyyymmdd; nextFields["base_next_date_ts"] = now
            writes += ScheduleWritePlan.RowWrite("schedules_next_date", rowId, nextFields)
        }
        val scheduleFields = linkedMapOf<String, Any?>("name" to fields.normalizedName,
            "posts_transaction" to flag(fields.postsTransaction), "custom_upcoming_length" to fields.customUpcomingLength)
        if (schedule.ruleId == null) scheduleFields["rule"] = ruleId
        writes += ScheduleWritePlan.RowWrite("schedules", schedule.id, scheduleFields)
        return ScheduleWritePlan(schedule.id, writes, merged)
    }

    fun delete(schedule: ActualScheduleSummary): ScheduleWritePlan = ScheduleWritePlan(schedule.id, buildList {
        add(row("schedules", schedule.id, "tombstone" to 1))
        schedule.ruleId?.let { add(row("rules", it, "tombstone" to 1)) }
    })

    fun nextDate(schedule: ActualScheduleSummary, date: DayDate, reset: Boolean, now: Long): ScheduleWritePlan? {
        val rowId = schedule.nextDateRowId ?: return null
        return ScheduleWritePlan(schedule.id, listOf(if (reset)
            row("schedules_next_date", rowId, "base_next_date" to date.yyyymmdd, "base_next_date_ts" to now)
        else row("schedules_next_date", rowId, "local_next_date" to date.yyyymmdd,
            "local_next_date_ts" to schedule.baseNextDateTimestamp)))
    }

    fun columns(scheduleId: String, vararg fields: Pair<String, Any?>) =
        ScheduleWritePlan(scheduleId, listOf(row("schedules", scheduleId, *fields)))

    private fun row(dataset: String, id: String, vararg fields: Pair<String, Any?>) =
        ScheduleWritePlan.RowWrite(dataset, id, linkedMapOf(*fields))
    private fun flag(value: Boolean) = if (value) 1 else 0
}
