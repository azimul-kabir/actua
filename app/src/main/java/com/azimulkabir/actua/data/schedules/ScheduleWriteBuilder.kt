package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ScheduleWritePlan(
    val scheduleId: String, val writes: List<RowWrite>, val conditions: JSONArray? = null,
) {
    data class RowWrite(val dataset: String, val row: String, val fields: LinkedHashMap<String, Any?>)
}

/** A future transaction's details, as Actual's "convert to schedule" reads them. [splits] is non-null for a split parent. */
data class OneTimeScheduleSource(
    val date: DayDate,
    val amountCents: Long,
    val payeeId: String?,
    val accountId: String,
    val categoryId: String?,
    val notes: String?,
    val splits: List<Split>? = null,
) {
    data class Split(val amountCents: Long, val categoryId: String?, val notes: String?)
}

object ScheduleWriteBuilder {
    private val nameDate = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    /**
     * Actual's `createSingleTimeScheduleFromTransaction` (desktop-client `util/schedule-actions.ts`, mobile
     * `onSchedule`) on top of `schedule/create`: a non-recurring schedule that posts a transaction on
     * [OneTimeScheduleSource.date]. Conditions are `date is`, `amount is`, `payee is` (when set) and
     * `account is`; the rule's actions are the `link-schedule` action followed by `set` actions that
     * carry the category and notes (per split line for a split parent, with the parent's notes at
     * `splitIndex` 0). The schedule is named `Auto-created future transaction (MMM dd, yyyy) - <now>`.
     */
    fun createOneTime(source: OneTimeScheduleSource, scheduleId: String, ruleId: String, nextDateRowId: String,
        now: Long): ScheduleWritePlan {
        fun condition(field: String, value: Any) = JSONObject().put("op", "is").put("field", field).put("value", value)
        val conditions = JSONArray().put(condition("date", source.date.iso)).put(condition("amount", source.amountCents))
        source.payeeId?.takeIf(String::isNotEmpty)?.let { conditions.put(condition("payee", it)) }
        conditions.put(condition("account", source.accountId))

        fun set(field: String, value: String, splitIndex: Int? = null) = Rule.Action(
            "set", field, RuleValue.Text(value),
            splitIndex?.let { mapOf("splitIndex" to RuleValue.Number(it.toDouble())) }.orEmpty(),
        )
        val actions = mutableListOf(Rule.Action("link-schedule", null, RuleValue.Text(scheduleId)))
        val splits = source.splits
        if (splits != null) {
            source.notes?.takeIf(String::isNotEmpty)?.let { actions += set("notes", it, 0) }
            splits.forEachIndexed { index, split ->
                val splitIndex = index + 1
                actions += Rule.Action(
                    "set-split-amount", null, RuleValue.Number(split.amountCents.toDouble()),
                    mapOf("splitIndex" to RuleValue.Number(splitIndex.toDouble()), "method" to RuleValue.Text("fixed-amount")),
                )
                split.categoryId?.takeIf(String::isNotEmpty)?.let { actions += set("category", it, splitIndex) }
                split.notes?.takeIf(String::isNotEmpty)?.let { actions += set("notes", it, splitIndex) }
            }
        } else {
            source.categoryId?.takeIf(String::isNotEmpty)?.let { actions += set("category", it) }
            source.notes?.takeIf(String::isNotEmpty)?.let { actions += set("notes", it) }
        }
        val actionsJson = Rule("", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, emptyList(), actions).actionsJson

        val name = "Auto-created future transaction (${source.date.localDate.format(nameDate)}) - $now"
        return ScheduleWritePlan(scheduleId, listOf(
            row("rules", ruleId, "stage" to null, "conditions_op" to "and",
                "conditions" to conditions.toString(), "actions" to actionsJson, "tombstone" to 0),
            row("schedules_next_date", nextDateRowId, "schedule_id" to scheduleId,
                "local_next_date" to source.date.yyyymmdd, "local_next_date_ts" to now,
                "base_next_date" to source.date.yyyymmdd, "base_next_date_ts" to now),
            row("schedules", scheduleId, "rule" to ruleId, "name" to name,
                "posts_transaction" to 1, "custom_upcoming_length" to null, "completed" to 0, "tombstone" to 0),
        ), conditions)
    }

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
