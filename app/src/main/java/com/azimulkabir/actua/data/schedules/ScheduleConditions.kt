package com.azimulkabir.actua.data.schedules

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

sealed interface ScheduledAmount {
    val postAmount: Long
    data class Fixed(val cents: Long) : ScheduledAmount { override val postAmount = cents }
    data class Range(val first: Long, val second: Long) : ScheduledAmount {
        override val postAmount: Long = floor((first + second).toDouble() / 2 + .5).toLong()
    }
}
enum class ScheduleAmountOp(val stored: String) { EXACT("is"), APPROXIMATE("isapprox"), BETWEEN("isbetween") }
sealed interface ScheduleDateCondition {
    data class Fixed(val day: DayDate) : ScheduleDateCondition
    data class Recurring(val config: RecurConfig) : ScheduleDateCondition
    data object Unsupported : ScheduleDateCondition
}
data class ScheduleFormFields(
    val name: String? = null, val payeeId: String? = null, val accountId: String? = null,
    val amount: ScheduledAmount? = null, val amountOp: ScheduleAmountOp = ScheduleAmountOp.APPROXIMATE,
    val date: ScheduleDateCondition? = null, val postsTransaction: Boolean = false,
    val customUpcomingLength: String? = null,
) { val normalizedName get() = name?.trim()?.takeIf(String::isNotEmpty) }

object ScheduleConditions {
    data class Indices(val payee: Int?, val account: Int?, val amount: Int?, val date: Int?) {
        val ordered get() = listOf(payee, account, amount, date)
    }

    fun parse(json: String?): JSONArray = runCatching { if (json == null) JSONArray() else JSONArray(json) }.getOrElse { JSONArray() }
    fun serialize(value: JSONArray): String = value.toString()

    fun extract(conditions: JSONArray): Indices {
        fun find(ops: Set<String>, fields: List<String>): Int? {
            fields.forEach { field -> for (index in 0 until conditions.length()) {
                val row = conditions.optJSONObject(index) ?: continue
                if (row.optString("op") in ops && row.optString("field") == field) return index
            } }
            return null
        }
        return Indices(find(setOf("is"), listOf("payee", "description")),
            find(setOf("is"), listOf("account", "acct")),
            find(setOf("is", "isapprox", "isbetween"), listOf("amount")),
            find(setOf("is", "isapprox"), listOf("date")))
    }

    fun build(fields: ScheduleFormFields, existing: JSONArray): JSONArray {
        val date = requireNotNull(fields.date) { "A date is required" }
        val amount = requireNotNull(fields.amount) { "A valid amount is required" }
        val indices = extract(existing)
        fun update(index: Int?, op: String, field: String, value: Any?): JSONObject? {
            if (index != null) return copy(existing.getJSONObject(index)).put("value", value ?: JSONObject.NULL)
            if (value == null && field != "payee") return null
            return JSONObject().put("op", op).put("field", field).put("value", value ?: JSONObject.NULL)
        }
        return JSONArray().also { output ->
            listOfNotNull(
                update(indices.payee, "is", "payee", fields.payeeId),
                update(indices.account, "is", "account", fields.accountId),
                update(indices.date, "isapprox", "date", dateValue(date)),
                JSONObject().put("op", fields.amountOp.stored).put("field", "amount").put("value", amountValue(amount)),
            ).forEach(output::put)
        }
    }

    fun merge(existing: JSONArray, schedule: JSONArray): JSONArray {
        val oldSlots = extract(existing).ordered; val newSlots = extract(schedule).ordered
        val result = JSONArray(); for (index in 0 until existing.length()) result.put(copy(existing.getJSONObject(index)))
        val appended = mutableListOf<JSONObject>()
        newSlots.forEachIndexed { slot, newIndex -> if (newIndex != null) {
            val replacement = copy(schedule.getJSONObject(newIndex)); val oldIndex = oldSlots[slot]
            if (oldIndex != null) result.put(oldIndex, replacement) else appended += replacement
        } }
        appended.forEach(result::put); return result
    }

    fun syncedActions(conditions: JSONArray, actions: JSONArray): JSONArray? {
        val amountIndex = extract(conditions).amount ?: return null
        val amount = scheduledAmount(conditions.getJSONObject(amountIndex).opt("value"))
        val result = JSONArray(); var changed = false
        for (index in 0 until actions.length()) {
            val action = copy(actions.getJSONObject(index)); val options = action.optJSONObject("options")
            if (action.optString("op") == "set" && action.optString("field") == "amount" &&
                options?.has("template") != true && options?.has("formula") != true && action.optLong("value") != amount) {
                action.put("value", amount); changed = true
            }
            result.put(action)
        }
        return result.takeIf { changed }
    }

    fun jsonPaths(conditions: JSONArray): Indices = extract(conditions)
    fun scheduledAmount(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is JSONObject -> ScheduledAmount.Range(value.optLong("num1"), value.optLong("num2")).postAmount
        else -> 0
    }
    fun amountValue(amount: ScheduledAmount): Any = when (amount) {
        is ScheduledAmount.Fixed -> amount.cents
        is ScheduledAmount.Range -> JSONObject().put("num1", amount.first).put("num2", amount.second)
    }
    fun dateValue(date: ScheduleDateCondition): Any = when (date) {
        is ScheduleDateCondition.Fixed -> date.day.iso
        is ScheduleDateCondition.Recurring -> date.config.toJson()
        ScheduleDateCondition.Unsupported -> "Unsupported repeat"
    }
    fun nextDate(date: ScheduleDateCondition, today: DayDate): DayDate? = when (date) {
        is ScheduleDateCondition.Fixed -> date.day
        is ScheduleDateCondition.Recurring -> ScheduleRecurrence.nextOccurrence(date.config, today)
        ScheduleDateCondition.Unsupported -> null
    }

    fun dateCondition(value: Any?): ScheduleDateCondition = when (value) {
        is String -> DayDate.fromIso(value)?.let(ScheduleDateCondition::Fixed) ?: ScheduleDateCondition.Unsupported
        is JSONObject -> RecurConfig.parse(value)?.let(ScheduleDateCondition::Recurring) ?: ScheduleDateCondition.Unsupported
        else -> ScheduleDateCondition.Unsupported
    }

    private fun copy(value: JSONObject) = JSONObject(value.toString())
}
