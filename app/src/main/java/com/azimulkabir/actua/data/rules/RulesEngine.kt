package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleRecurrence
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

data class RuleContext(
    val offBudgetAccountIds: Set<String> = emptySet(),
    val accountNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val categoryGroupIds: Map<String, String> = emptyMap(),
    val categoryGroupNames: Map<String, String> = emptyMap(),
    val payeeNames: Map<String, String> = emptyMap(),
    val hiddenCategoryIds: Set<String> = emptySet(),
)
data class RuleRunResult(
    val transaction: ActualTransaction, val changedFields: Set<String>,
    val pendingPayeeName: String?, val isDeleted: Boolean,
)

object RulesEngine {
    fun matches(
        transaction: ActualTransaction,
        conditions: List<Rule.Condition>,
        conditionsOp: Rule.ConditionsOp = Rule.ConditionsOp.AND,
        context: RuleContext = RuleContext(),
    ): Boolean {
        if (conditions.isEmpty()) return true
        val bag = Bag(transaction, context)
        return if (conditionsOp == Rule.ConditionsOp.AND) conditions.all { evaluate(it, bag) }
        else conditions.any { evaluate(it, bag) }
    }

    fun apply(transaction: ActualTransaction, rules: List<Rule>, context: RuleContext = RuleContext()): RuleRunResult {
        val bag = Bag(transaction, context, forRuleRun = true)
        val before = bag.snapshot()
        val scheduleId = transaction.scheduleId
        // Rules run one at a time, each checked against the transaction as the rules before it
        // left it, as upstream's runRules loop does (`finalTrans = rules[i].apply(finalTrans)`).
        for (rule in RuleRanker.rank(rules)) {
            val linkedSchedule = rule.linkedScheduleId()
            val fires = when {
                // The schedule's own rule always fires for its transaction, skipping condition
                // checks, while every other schedule-owned rule is excluded entirely -- matching
                // upstream's runRules schedule bypass/exclusion (transaction-rules.ts).
                scheduleId != null && linkedSchedule != null -> linkedSchedule == scheduleId
                else -> conditionsMatch(rule, bag)
            }
            if (fires) rule.actions.forEach { apply(it, bag) }
        }
        return RuleRunResult(bag.transaction(), bag.changed(before), bag.pendingPayeeName, bag.deleted)
    }

    private fun conditionsMatch(rule: Rule, bag: Bag): Boolean = rule.conditions.isNotEmpty() &&
        if (rule.conditionsOp == Rule.ConditionsOp.AND) rule.conditions.all { evaluate(it, bag) }
        else rule.conditions.any { evaluate(it, bag) }

    private fun Rule.linkedScheduleId(): String? = actions.firstOrNull { it.op == "link-schedule" }?.value?.text

    private fun evaluate(condition: Rule.Condition, bag: Bag): Boolean {
        if (condition.op == "onBudget") return bag.onBudget == true
        if (condition.op == "offBudget") return bag.onBudget == false
        return when (RuleSchema.type(condition.field)) {
            RuleFieldType.DATE -> when (val value = condition.value) {
                is RuleValue.Text -> RuleDateMatcher.matches(bag.number("date")?.toInt(), condition.op, value.value) == true
                is RuleValue.ObjectValue -> RuleDateMatcher.matchesRecurring(bag.number("date")?.toInt(), condition.op, value) == true
                else -> false
            }
            RuleFieldType.NUMBER -> evaluateNumber(condition, bag)
            RuleFieldType.BOOLEAN -> condition.op == "is" && condition.value.flag != null && bag.flag(condition.field) == condition.value.flag
            else -> evaluateText(condition, bag)
        }
    }

    private fun evaluateNumber(condition: Rule.Condition, bag: Bag): Boolean {
        var amount = bag.number(condition.field)?.toDouble() ?: return false
        val outflow = condition.field == "amount-outflow" || condition.options["outflow"]?.flag == true
        val inflow = condition.field == "amount-inflow" || condition.options["inflow"]?.flag == true
        if (outflow) { if (amount > 0) return false; amount = -amount }
        else if (inflow && amount < 0) return false
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
        if (bag.forRuleRun && RuleSchema.type(condition.field) == RuleFieldType.ID) {
            evaluateIdText(condition, bag)?.let { return it }
        }
        val actual = bag.text(condition.field, condition.op) ?:
            if (RuleSchema.type(condition.field) == RuleFieldType.STRING) "" else null
        val target = condition.value.text
        return when (condition.op) {
            "is" -> if (target.isNullOrEmpty()) {
                actual.isNullOrEmpty() && (condition.field != "category" || (!bag.isTransfer && !bag.isParent))
            } else actual.equals(target, true)
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

    /**
     * A rule run compares contains/doesNotContain/matches on an id field with the stored id, as
     * upstream's Condition.eval does: it lowercases the id but not the value, and a missing id
     * matches nothing. Report filters compare names instead, as upstream's conditionsToAQL does.
     */
    private fun evaluateIdText(condition: Rule.Condition, bag: Bag): Boolean? {
        if (condition.op !in setOf("contains", "doesNotContain", "matches")) return null
        val id = bag.text(condition.field)?.lowercase() ?: return false
        val target = condition.value.text ?: return false
        return when (condition.op) {
            "contains" -> id.contains(target)
            "doesNotContain" -> !id.contains(target)
            else -> runCatching { Regex(target).containsMatchIn(id) }.getOrDefault(false)
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

    private class Bag(
        private val base: ActualTransaction,
        private val context: RuleContext,
        /** Upstream Condition.eval semantics (rule runs) rather than conditionsToAQL (report filters). */
        val forRuleRun: Boolean = false,
    ) {
        private val strings = mutableMapOf<String, String?>(
            "account" to base.accountId, "payee" to base.payeeId,
            "payee_name" to (base.payeeId?.let(context.payeeNames::get) ?: base.payeeName),
            "category" to base.categoryId,
            "notes" to base.notes, "imported_payee" to base.importedPayee,
            "transfer_id" to base.transferId, "parent_id" to base.parentId, "schedule" to base.scheduleId,
        )
        private val numbers = mutableMapOf("date" to base.date.toLong(), "amount" to base.amountCents)
        private val flags = mutableMapOf(
            "cleared" to base.cleared,
            "reconciled" to base.reconciled,
            "transfer" to (base.transferAccountId != null || base.transferId != null),
            "parent" to base.isParent,
            "is_parent" to base.isParent,
            "is_child" to (base.parentId != null),
        )
        val onBudget = if (base.accountId.isBlank()) null else base.accountId !in context.offBudgetAccountIds
        val isTransfer get() = base.transferAccountId != null || base.transferId != null
        val isParent get() = base.isParent
        var pendingPayeeName: String? = null
        var deleted = false
        // Ids and names follow the values earlier rules set. A payee_name an earlier rule set is
        // matched to an existing payee, as upstream's resolvePayeeNameForRules does after each rule.
        // The on/off-budget flag stays with the original account, as upstream's `_account` does.
        private val payeeId get() = strings["payee"] ?: pendingPayeeName?.trim()?.takeIf(String::isNotEmpty)
            ?.let { name -> context.payeeNames.entries.firstOrNull { it.value.trim().equals(name, true) }?.key }
        private val categoryGroupId get() = strings["category"]?.let(context.categoryGroupIds::get)
        fun text(field: String, op: String? = null): String? = if (op in setOf("contains", "doesNotContain", "matches")) {
            when (field) {
                "category" -> strings["category"]?.let { if (it == base.categoryId) base.categoryName ?: context.categoryNames[it] else context.categoryNames[it] }
                "category_group" -> categoryGroupId?.let(context.categoryGroupNames::get)
                "account" -> strings["account"]?.let(context.accountNames::get)
                "payee" -> if (strings["payee"] == base.payeeId && pendingPayeeName == null) base.payeeName else payeeId?.let(context.payeeNames::get)
                else -> strings[field]
            }
        } else when (field) {
            "payee" -> payeeId
            "category_group" -> categoryGroupId
            else -> strings[field]
        }
        fun number(field: String) = if (field == "amount-inflow" || field == "amount-outflow") numbers["amount"] else numbers[field]
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
            amountCents = number("amount") ?: base.amountCents, payeeId = strings["payee"], categoryId = text("category"), notes = text("notes"),
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

    /** A schedule's recurring date condition is stored as a RecurConfig JSON object rather than a string. */
    fun matchesRecurring(transactionDate: Int?, op: String, value: RuleValue.ObjectValue): Boolean? {
        val date = transactionDate ?: return false
        val config = (value.jsonValue() as? JSONObject)?.let(RecurConfig::parse) ?: return null
        val day = DayDate.fromYyyymmdd(date) ?: return null
        return when (op) {
            "is" -> ScheduleRecurrence.occursOn(config, day)
            "isapprox" -> ScheduleRecurrence.occursApprox(config, day)
            else -> null
        }
    }
}

object TagFilter {
    fun extract(value: String): List<String> { val seen = linkedSetOf<String>(); value.split(Regex("[\\s#]+")).filter(String::isNotEmpty).forEach { seen += "#$it" }; return seen.toList() }
    fun contains(notes: String, tag: String, caseSensitive: Boolean = false): Boolean {
        val source = if (caseSensitive) notes else notes.lowercase(); val needle = if (caseSensitive) tag else tag.lowercase()
        return Regex("(?<!#)${Regex.escape(needle)}([\\s#]|$)").containsMatchIn(source)
    }
}
