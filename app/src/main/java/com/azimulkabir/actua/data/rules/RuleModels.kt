package com.azimulkabir.actua.data.rules

import org.json.JSONArray
import org.json.JSONObject

data class RuleChoice(val id: String, val name: String)
data class RuleEditorData(
    val accounts: List<RuleChoice> = emptyList(),
    val payees: List<RuleChoice> = emptyList(),
    val categories: List<RuleChoice> = emptyList(),
    val categoryGroups: List<RuleChoice> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

sealed interface RuleValue {
    data class Text(val value: String) : RuleValue
    data class Number(val value: Double) : RuleValue
    data class Flag(val value: Boolean) : RuleValue
    data class ListValue(val value: List<RuleValue>) : RuleValue
    data class ObjectValue(val value: Map<String, RuleValue>) : RuleValue
    data object Null : RuleValue

    val text: String? get() = (this as? Text)?.value
    val number: Double? get() = (this as? Number)?.value
    val flag: Boolean? get() = (this as? Flag)?.value
    val list: List<RuleValue>? get() = (this as? ListValue)?.value

    fun jsonValue(): Any = when (this) {
        is Text -> value
        is Number -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else value
        is Flag -> value
        is ListValue -> JSONArray().apply { value.forEach { put(it.jsonValue()) } }
        is ObjectValue -> JSONObject().apply { value.forEach { (key, item) -> put(key, item.jsonValue()) } }
        Null -> JSONObject.NULL
    }

    companion object {
        fun fromJson(value: Any?): RuleValue = when (value) {
            null, JSONObject.NULL -> Null
            is String -> Text(value)
            is Boolean -> Flag(value)
            is kotlin.Number -> Number(value.toDouble())
            is JSONArray -> ListValue((0 until value.length()).map { fromJson(value.opt(it)) })
            is JSONObject -> ObjectValue(value.keys().asSequence().associateWith { fromJson(value.opt(it)) })
            else -> Null
        }
    }
}

data class Rule(
    val id: String,
    val stage: Stage,
    val conditionsOp: ConditionsOp,
    val conditions: List<Condition>,
    val actions: List<Action>,
) {
    enum class Stage { PRE, DEFAULT, POST }
    enum class ConditionsOp { AND, OR }
    data class Condition(val op: String, val field: String, val value: RuleValue, val options: Map<String, RuleValue> = emptyMap())
    data class Action(val op: String, val field: String?, val value: RuleValue, val options: Map<String, RuleValue> = emptyMap())

    val storedStage: String? get() = when (stage) { Stage.PRE -> "pre"; Stage.POST -> "post"; Stage.DEFAULT -> null }
    val conditionsJson: String get() = JSONArray().apply { conditions.forEach { condition ->
        put(JSONObject().put("op", condition.op).put("field", RuleSchema.internalField(condition.field))
            .put("value", condition.value.jsonValue()).apply {
                RuleSchema.type(condition.field)?.let { put("type", it.name.lowercase()) }
                if (condition.options.isNotEmpty()) put("options", optionsJson(condition.options))
            })
    } }.toString()
    val actionsJson: String get() = JSONArray().apply { actions.forEach { action ->
        put(JSONObject().put("op", action.op).apply {
            when (action.op) {
                "set" -> action.field?.let { field ->
                    put("field", RuleSchema.internalField(field))
                    RuleSchema.type(field)?.let { put("type", it.name.lowercase()) }
                }
                "prepend-notes", "append-notes" -> { put("field", "notes"); put("type", "id") }
            }
            if (action.op != "delete-transaction") put("value", action.value.jsonValue())
            if (action.options.isNotEmpty()) put("options", optionsJson(action.options))
        })
    } }.toString()

    companion object {
        fun empty(id: String = java.util.UUID.randomUUID().toString()) = Rule(
            id, Stage.DEFAULT, ConditionsOp.AND, emptyList(), emptyList(),
        )
        fun parse(id: String, stage: String?, conditionsOp: String?, conditionsJson: String?, actionsJson: String?): Rule = Rule(
            id = id,
            stage = when (stage) { "pre" -> Stage.PRE; "post" -> Stage.POST; else -> Stage.DEFAULT },
            conditionsOp = if (conditionsOp.equals("or", true)) ConditionsOp.OR else ConditionsOp.AND,
            conditions = parseArray(conditionsJson).map { item ->
                val op = item.optString("op").takeIf(String::isNotBlank) ?: error("Invalid rule condition")
                val field = item.optString("field").takeIf(String::isNotBlank) ?: error("Invalid rule condition")
                Condition(op, RuleSchema.publicField(field), RuleValue.fromJson(item.opt("value")), options(item.optJSONObject("options")))
            },
            actions = parseArray(actionsJson).map { item ->
                val op = item.optString("op").takeIf(String::isNotBlank) ?: error("Invalid rule action")
                Action(op, item.optString("field").takeIf(String::isNotBlank)?.let(RuleSchema::publicField),
                    RuleValue.fromJson(item.opt("value")), options(item.optJSONObject("options")))
            },
        )

        private fun parseArray(json: String?): List<JSONObject> {
            if (json == null) return emptyList()
            val array = JSONArray(json)
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
        private fun options(json: JSONObject?): Map<String, RuleValue> = json?.keys()?.asSequence()
            ?.associateWith { RuleValue.fromJson(json.opt(it)) }.orEmpty()
        private fun optionsJson(options: Map<String, RuleValue>) = JSONObject().apply {
            options.forEach { (key, value) -> put(key, value.jsonValue()) }
        }
    }
}

enum class RuleFieldType { ID, STRING, NUMBER, DATE, BOOLEAN }

object RuleSchema {
    private val fieldTypes = mapOf(
        "imported_payee" to RuleFieldType.STRING, "payee" to RuleFieldType.ID,
        "payee_name" to RuleFieldType.STRING, "date" to RuleFieldType.DATE,
        "notes" to RuleFieldType.STRING, "amount" to RuleFieldType.NUMBER,
        "category" to RuleFieldType.ID, "category_group" to RuleFieldType.ID,
        "account" to RuleFieldType.ID, "cleared" to RuleFieldType.BOOLEAN,
        "reconciled" to RuleFieldType.BOOLEAN, "transfer" to RuleFieldType.BOOLEAN,
        "parent" to RuleFieldType.BOOLEAN,
    )
    private val internalToPublic = mapOf(
        "isParent" to "is_parent", "isChild" to "is_child", "acct" to "account",
        "financial_id" to "imported_id", "imported_description" to "imported_payee",
        "transferred_id" to "transfer_id", "description" to "payee",
    )
    private val publicToInternal = internalToPublic.entries.associate { (key, value) -> value to key }
    fun type(field: String) = fieldTypes[field]
    fun publicField(field: String) = internalToPublic[field] ?: field
    fun internalField(field: String) = publicToInternal[field] ?: field
    val conditionFields = listOf("imported_payee", "account", "category", "category_group", "date", "payee", "notes", "amount")
    val actionFields = listOf("category", "payee", "payee_name", "notes", "cleared", "account", "date", "amount")
    fun validOps(field: String): List<String> = when (type(field)) {
        RuleFieldType.DATE -> listOf("is", "isapprox", "gt", "gte", "lt", "lte")
        RuleFieldType.NUMBER -> listOf("is", "isapprox", "isbetween", "gt", "gte", "lt", "lte")
        RuleFieldType.BOOLEAN -> listOf("is")
        RuleFieldType.ID -> listOf("is", "isNot", "oneOf", "notOneOf", "contains", "doesNotContain", "matches") +
            if (field == "account") listOf("onBudget", "offBudget") else emptyList()
        else -> if (field == "notes") {
            listOf("is", "isNot", "contains", "doesNotContain", "matches", "hasTags", "hasAnyTag")
        } else {
            listOf("is", "isNot", "oneOf", "notOneOf", "contains", "doesNotContain", "matches")
        }
    }
    fun fieldLabel(field: String) = when (field) {
        "imported_payee" -> "Imported payee"; "payee_name" -> "Payee (name)"
        "category_group" -> "Category group"; else -> field.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
    fun opLabel(op: String) = mapOf("isNot" to "is not", "oneOf" to "one of", "notOneOf" to "not one of",
        "isapprox" to "is approx", "isbetween" to "is between", "doesNotContain" to "does not contain",
        "hasTags" to "has all tags", "hasAnyTag" to "has any tag", "onBudget" to "is on budget",
        "offBudget" to "is off budget", "gt" to "greater than", "gte" to "greater than or equal",
        "lt" to "less than", "lte" to "less than or equal")[op] ?: op.replace('-', ' ')
}
