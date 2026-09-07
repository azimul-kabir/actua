package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.time.DateTimeException
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

data class RuleContext(
    val offBudgetAccountIds: Set<String> = emptySet(),
    val categoryGroupIds: Map<String, String> = emptyMap(),
    val payeeNames: Map<String, String> = emptyMap(),
)
data class RuleRunResult(
    val transaction: ActualTransaction, val changedFields: Set<String>,
    val pendingPayeeName: String?, val isDeleted: Boolean,
)

object RulesEngine {
    fun apply(transaction: ActualTransaction, rules: List<Rule>, context: RuleContext = RuleContext()): RuleRunResult {
        val bag = Bag(transaction, context)
        val before = bag.snapshot()
        RuleRanker.rank(rules).filter { rule ->
            rule.conditions.isNotEmpty() && if (rule.conditionsOp == Rule.ConditionsOp.AND)
                rule.conditions.all { evaluate(it, bag) } else rule.conditions.any { evaluate(it, bag) }
        }.forEach { rule -> rule.actions.forEach { apply(it, bag) } }
        return RuleRunResult(bag.transaction(), bag.changed(before), bag.pendingPayeeName, bag.deleted)
    }

    private fun evaluate(condition: Rule.Condition, bag: Bag): Boolean {
        if (condition.op == "onBudget") return bag.onBudget == true
        if (condition.op == "offBudget") return bag.onBudget == false
        return when (RuleSchema.type(condition.field)) {
            RuleFieldType.DATE -> condition.value.text?.let { RuleDateMatcher.matches(bag.number("date")?.toInt(), condition.op, it) } == true
            RuleFieldType.NUMBER -> evaluateNumber(condition, bag)
            RuleFieldType.BOOLEAN -> condition.op == "is" && condition.value.flag != null && bag.flag(condition.field) == condition.value.flag
            else -> evaluateText(condition, bag)
        }
    }

    private fun evaluateNumber(condition: Rule.Condition, bag: Bag): Boolean {
        var amount = bag.number(condition.field)?.toDouble() ?: return false
        if (condition.options["outflow"]?.flag == true) { if (amount > 0) return false; amount = -amount }
        else if (condition.options["inflow"]?.flag == true && amount < 0) return false
        if (condition.op == "isbetween") {
            val values = (condition.value as? RuleValue.ObjectValue)?.value ?: return false
            val a = values["num1"]?.number ?: return false; val b = values["num2"]?.number ?: return false
            return amount in minOf(a, b)..maxOf(a, b)
        }
        val target = condition.value.number ?: return false
        return when (condition.op) {
            "is" -> amount == target
            "isapprox" -> abs(amount - target) <= (abs(target) * .075).roundToLong()
            "gt" -> amount > target; "gte" -> amount >= target; "lt" -> amount < target; "lte" -> amount <= target
            else -> false
        }
    }

    private fun evaluateText(condition: Rule.Condition, bag: Bag): Boolean {
        val actual = bag.text(condition.field) ?: if (RuleSchema.type(condition.field) == RuleFieldType.STRING) "" else null
        val target = condition.value.text
        return when (condition.op) {
            "is" -> if (target == null) actual == null else actual.equals(target, true)
            "isNot" -> if (target == null) actual != null else !actual.equals(target, true)
            "contains" -> actual != null && target != null && actual.contains(target, true)
            "doesNotContain" -> actual != null && target != null && !actual.contains(target, true)
            "oneOf", "notOneOf" -> actual != null && condition.value.list != null &&
                condition.value.list!!.any { it.text.equals(actual, true) }.let { if (condition.op == "oneOf") it else !it }
            "matches" -> actual != null && target != null && runCatching { Regex(target.lowercase()).containsMatchIn(actual.lowercase()) }.getOrDefault(false)
            "hasTags", "hasAnyTag" -> actual != null && target != null && TagFilter.extract(target).map { TagFilter.contains(actual, it) }
                .let { if (condition.op == "hasTags") it.all { hit -> hit } else it.any { hit -> hit } }
            else -> false
        }
    }

    private fun apply(action: Rule.Action, bag: Bag) {
        when (action.op) {
            "set" -> if (action.options["template"] == null && action.options["formula"] == null &&
                (action.options["splitIndex"]?.number ?: 0.0) <= 0) action.field?.let { bag.set(it, action.value) }
            "prepend-notes" -> action.value.text?.let { bag.set("notes", RuleValue.Text(if (bag.text("notes").isNullOrEmpty()) it else it + bag.text("notes"))) }
            "append-notes" -> action.value.text?.let { bag.set("notes", RuleValue.Text(if (bag.text("notes").isNullOrEmpty()) it else bag.text("notes") + it)) }
            "link-schedule" -> action.value.text?.let { bag.set("schedule", RuleValue.Text(it)) }
            "delete-transaction" -> bag.deleted = true
        }
    }

    private class Bag(private val base: ActualTransaction, context: RuleContext) {
        private val strings = mutableMapOf<String, String?>(
            "account" to base.accountId, "payee" to base.payeeId,
            "payee_name" to (base.payeeId?.let(context.payeeNames::get) ?: base.payeeName),
            "category" to base.categoryId, "category_group" to base.categoryId?.let(context.categoryGroupIds::get),
            "notes" to base.notes, "imported_payee" to base.importedPayee,
            "transfer_id" to base.transferId, "parent_id" to base.parentId, "schedule" to base.scheduleId,
        )
        private val numbers = mutableMapOf("date" to base.date.toLong(), "amount" to base.amountCents)
        private val flags = mutableMapOf("cleared" to base.cleared, "reconciled" to base.reconciled)
        val onBudget = if (base.accountId.isBlank()) null else base.accountId !in context.offBudgetAccountIds
        var pendingPayeeName: String? = null
        var deleted = false
        fun text(field: String) = strings[field]
        fun number(field: String) = numbers[field]
        fun flag(field: String) = flags[field]
        fun set(field: String, value: RuleValue) { when (RuleSchema.type(field)) {
            RuleFieldType.NUMBER -> value.number?.takeIf(Double::isFinite)?.roundToLong()?.let { numbers[field] = it }
            RuleFieldType.BOOLEAN -> value.flag?.let { flags[field] = it }
            RuleFieldType.DATE -> value.text?.replace("-", "")?.toIntOrNull()?.takeIf { it > 9_999_999 }?.let { numbers["date"] = it.toLong() }
            else -> { strings[field] = value.text; if (field == "payee_name") { pendingPayeeName = value.text; strings["payee"] = null }; if (field == "payee") pendingPayeeName = null }
        } }
        fun snapshot() = strings.mapValues { "s:${it.value}" } + numbers.mapValues { "i:${it.value}" } + flags.mapValues { "b:${it.value}" }
        fun changed(before: Map<String, String>) = snapshot().filter { before[it.key] != it.value }.keys
        fun transaction() = base.copy(accountId = text("account") ?: base.accountId, date = number("date")?.toInt() ?: base.date,
            amountCents = number("amount") ?: base.amountCents, payeeId = text("payee"), categoryId = text("category"), notes = text("notes"),
            importedPayee = text("imported_payee"), transferId = text("transfer_id"), parentId = text("parent_id"), scheduleId = text("schedule"),
            cleared = flag("cleared") ?: base.cleared, reconciled = flag("reconciled") ?: base.reconciled, tombstone = deleted || base.tombstone)
    }
}

object RuleDateMatcher {
    fun matches(transactionDate: Int?, op: String, value: String): Boolean? {
        val date = transactionDate ?: return false; val digits = value.replace("-", ""); val target = digits.toIntOrNull() ?: return null
        return when { op == "is" && digits.length == 8 -> date == target; op == "is" && digits.length == 6 -> date / 100 == target
            op == "is" && digits.length == 4 -> date / 10000 == target; op == "isapprox" && digits.length == 8 ->
                try { abs(LocalDate.of(date/10000, date/100%100, date%100).toEpochDay() - LocalDate.of(target/10000, target/100%100, target%100).toEpochDay()) <= 2 } catch (_: DateTimeException) { return null }
            op == "gt" && digits.length == 8 -> date > target; op == "gte" && digits.length == 8 -> date >= target
            op == "lt" && digits.length == 8 -> date < target; op == "lte" && digits.length == 8 -> date <= target; else -> null }
    }
}

object TagFilter {
    fun extract(value: String): List<String> { val seen = linkedSetOf<String>(); value.split(Regex("[\\s#]+")).filter(String::isNotEmpty).forEach { seen += "#$it" }; return seen.toList() }
    fun contains(notes: String, tag: String, caseSensitive: Boolean = false): Boolean {
        val source = if (caseSensitive) notes else notes.lowercase(); val needle = if (caseSensitive) tag else tag.lowercase()
        return Regex("(?<!#)${Regex.escape(needle)}([\\s#]|$)").containsMatchIn(source)
    }
}
