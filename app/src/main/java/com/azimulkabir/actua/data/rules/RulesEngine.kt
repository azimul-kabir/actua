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
    val runningBalanceCents: (ActualTransaction) -> Long = { 0L },
    val balanceOfCents: (ActualTransaction, String) -> Long = { _, _ -> 0L },
)
data class RuleRunResult(
    val transaction: ActualTransaction, val changedFields: Set<String>,
    val pendingPayeeName: String?, val isDeleted: Boolean,
    val splitChildren: List<RuleSplitChild> = emptyList(),
)
data class RuleSplitChild(val transaction: ActualTransaction, val pendingPayeeName: String?)

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

    fun apply(
        transaction: ActualTransaction,
        rules: List<Rule>,
        context: RuleContext = RuleContext(),
        idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
    ): RuleRunResult {
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
            if (fires) applyActions(rule.actions, bag, idFactory)
        }
        return RuleRunResult(
            bag.transaction(), bag.changed(before), bag.pendingPayeeName, bag.deleted, bag.splitChildren(),
        )
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
            // A rule run's transaction has no transfer/parent field, so upstream's Condition.eval
            // never matches them; only report filters (conditionsToAQL) can test them.
            RuleFieldType.BOOLEAN -> condition.op == "is" && condition.value.flag != null &&
                !(bag.forRuleRun && condition.field in setOf("transfer", "parent")) &&
                bag.flag(condition.field) == condition.value.flag
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
                // Only conditionsToAQL (report filters) excludes transfers and split parents from
                // `category is (none)`; a rule run matches any empty category, as Condition.eval does.
                actual.isNullOrEmpty() && (condition.field != "category" || bag.forRuleRun || (!bag.isTransfer && !bag.isParent))
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

    private fun applyActions(actions: List<Rule.Action>, bag: Bag, idFactory: () -> String) {
        val splitIndex = { action: Rule.Action -> action.options["splitIndex"]?.number ?: 0.0 }
        val splitActions = actions.filter {
            val index = splitIndex(it)
            index > 0.0 && index.isFinite() && index % 1.0 == 0.0 && index <= MAX_RULE_SPLIT_COUNT
        }
        actions.filter { splitIndex(it) == 0.0 }.forEach { apply(it, bag) }
        if (splitActions.isEmpty() || bag.isChild) return

        val maxIndex = splitActions.maxOf { splitIndex(it).toInt() }
        bag.ensureSplitChildren(maxIndex, idFactory)
        splitActions.forEach { action ->
            val index = splitIndex(action).toInt() - 1
            val childBag = bag.childBag(index)
            apply(action, childBag)
            bag.updateSplitChild(index, childBag.transaction(), childBag.pendingPayeeName)
        }
        val amountActions = splitActions.filter { it.op == "set-split-amount" }
        val fixedPercent = amountActions.filter { it.options["method"]?.text == "fixed-percent" }
        val remainder = amountActions.filter { it.options["method"]?.text == "remainder" }
        if (fixedPercent.isNotEmpty()) {
            val remainderAfterFixed = bag.remainingSplitAmount()
            fixedPercent.forEach { action ->
                val index = splitIndex(action).toInt() - 1
                val percent = action.value.number ?: 0.0
                bag.setSplitChildAmount(index, jsRound(remainderAfterFixed * percent / 100.0))
            }
        }
        if (remainder.isNotEmpty()) {
            val remainderAfterPercent = bag.remainingSplitAmount()
            val share = jsRound(remainderAfterPercent.toDouble() / remainder.size)
            remainder.forEach { action ->
                val index = splitIndex(action).toInt() - 1
                bag.setSplitChildAmount(index, share)
            }
            val lastIndex = remainder.maxOf { splitIndex(it).toInt() - 1 }
            bag.setSplitChildAmount(lastIndex, bag.splitChildren()[lastIndex].transaction.amountCents + bag.remainingSplitAmount())
        } else if (amountActions.isNotEmpty() || splitActions.any { it.op == "set" }) {
            val fixedIndices = amountActions.mapTo(mutableSetOf()) {
                splitIndex(it).toInt() - 1
            }
            val flexibleIndex = bag.splitChildren().indices.lastOrNull { it !in fixedIndices }
            if (flexibleIndex != null) {
                bag.setSplitChildAmount(
                    flexibleIndex,
                    bag.splitChildren()[flexibleIndex].transaction.amountCents + bag.remainingSplitAmount(),
                )
            }
        }
        bag.markAsSplitParent()
    }

    private fun jsRound(value: Double) = kotlin.math.floor(value + 0.5).toLong()

    private const val MAX_RULE_SPLIT_COUNT = 100.0

    private fun apply(action: Rule.Action, bag: Bag) {
        when (action.op) {
            "set" -> action.field?.let { field ->
                val template = action.options["template"]?.text
                val formula = action.options["formula"]?.text
                when {
                    formula != null -> bag.setFormula(field, formula)
                    template != null -> bag.setTemplate(field, template)
                    else -> bag.set(field, action.value)
                }
            }
            "set-split-amount" -> when (action.options["method"]?.text) {
                "fixed-amount" -> action.value.number?.let { bag.setAmount(it.roundToLong()) }
                "formula" -> action.options["formula"]?.text?.let { bag.setFormula("amount", it) }
            }
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
        private val parentAmountCents: Long? = null,
        private val balanceTransaction: ActualTransaction = base,
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
        val isParent get() = flag("parent") == true
        var pendingPayeeName: String? = null
        var deleted = false
        private val splitLines = mutableListOf<RuleSplitChild>()
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
        fun setAmount(value: Long) { numbers["amount"] = value }
        fun setFormula(field: String, formula: String) {
            runCatching {
                val result = RuleFormulaEvaluator.evaluate(formula, formulaVariables(), { literal ->
                    context.balanceOfCents(balanceTransaction, literal)
                })
                when (RuleSchema.type(field)) {
                    RuleFieldType.NUMBER -> when (result) {
                        is Number -> set(field, RuleValue.Number(result.toDouble()))
                        else -> result?.toString()?.let(::parseFloat)?.let { set(field, RuleValue.Number(it)) }
                    }
                    RuleFieldType.DATE -> result?.toString()?.let(::parseDate)
                        ?.let { set(field, RuleValue.Text(it)) }
                    RuleFieldType.BOOLEAN -> set(field, RuleValue.Flag(
                        result is Boolean && result || result?.toString().equals("true", true),
                    ))
                    else -> set(field, RuleValue.Text(result?.toString() ?: ""))
                }
            }
        }
        fun setTemplate(field: String, template: String) {
            val variables = formulaVariables()
            val rendered = Regex("""\{\{\{?\s*([^{}]+?)\s*\}?\}\}""").replace(template) { match ->
                val key = match.groupValues[1].removePrefix("this.").trim()
                variables[key.lowercase()]?.toString().orEmpty()
            }
            when (RuleSchema.type(field)) {
                RuleFieldType.NUMBER -> set(field, RuleValue.Number(parseFloat(rendered) ?: 0.0))
                RuleFieldType.DATE -> set(field, RuleValue.Text(parseDate(rendered) ?: "9999-12-31"))
                RuleFieldType.BOOLEAN -> set(field, RuleValue.Flag(rendered == "true"))
                else -> set(field, RuleValue.Text(rendered))
            }
        }
        private fun formulaVariables(): Map<String, Any?> {
            val date = number("date")?.toString()?.let { value ->
                if (value.length == 8) "${value.take(4)}-${value.substring(4, 6)}-${value.takeLast(2)}" else value
            }
            val account = text("account")
            val category = text("category")
            val payee = pendingPayeeName?.let { "new" } ?: text("payee")
            return mapOf(
                "id" to base.id, "account" to account, "account_name" to account?.let(context.accountNames::get).orEmpty(),
                "date" to date, "amount" to number("amount"), "payee" to payee,
                "payee_name" to (pendingPayeeName ?: payee?.let(context.payeeNames::get) ?: base.payeeName),
                "category" to category, "category_name" to category?.let(context.categoryNames::get).orEmpty(),
                "_category_name" to category?.let(context.categoryNames::get).orEmpty(),
                "category_group" to category?.let(context.categoryGroupIds::get),
                "parent_id" to text("parent_id"),
                "notes" to text("notes"), "imported_payee" to text("imported_payee"),
                "schedule" to text("schedule"), "transfer_id" to text("transfer_id"),
                "transferred_id" to text("transfer_id"), "cleared" to flag("cleared"),
                "reconciled" to flag("reconciled"), "transfer" to isTransfer,
                "is_parent" to (flag("parent") == true), "is_child" to (base.parentId != null),
                "starting_balance_flag" to base.startingBalance, "financial_id" to base.financialId,
                "pending" to base.pending, "raw_synced_data" to base.rawSyncedData,
                "sort_order" to base.sortOrder, "tombstone" to (deleted || base.tombstone), "error" to null,
                "_account_name" to account?.let(context.accountNames::get).orEmpty(),
                "balance" to context.runningBalanceCents(balanceTransaction),
                "parent_amount" to (parentAmountCents ?: base.amountCents),
                "today" to LocalDate.now().toString(),
            ).mapKeys { it.key.lowercase() }
        }
        private fun parseFloat(value: String): Double? =
            Regex("""^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?""").find(value.trim())
                ?.value?.toDoubleOrNull()?.takeIf(Double::isFinite)
        private fun parseDate(value: String): String? = runCatching {
            val date = LocalDate.parse(value)
            date.takeIf { it.year in 1900..9999 }?.toString()
        }.getOrNull()
        val isChild get() = base.parentId != null
        fun ensureSplitChildren(count: Int, idFactory: () -> String) {
            val parent = transaction()
            while (splitLines.size < count) {
                val index = splitLines.size
                val child = parent.copy(
                    id = idFactory(), amountCents = 0L, isParent = false, parentId = base.id,
                    transferId = null, sortOrder = (parent.sortOrder ?: 0.0) - index - 1,
                )
                splitLines += RuleSplitChild(child, pendingPayeeName)
            }
        }
        fun childBag(index: Int): Bag {
            val line = splitLines[index]
            return Bag(
                line.transaction, context, forRuleRun = true, parentAmountCents = number("amount"),
                balanceTransaction = balanceTransaction,
            )
                .also { it.pendingPayeeName = line.pendingPayeeName }
        }
        fun updateSplitChild(index: Int, transaction: ActualTransaction, pendingName: String?) {
            splitLines[index] = RuleSplitChild(transaction, pendingName)
        }
        fun setSplitChildAmount(index: Int, amount: Long) {
            val line = splitLines[index]
            splitLines[index] = line.copy(transaction = line.transaction.copy(amountCents = amount))
        }
        fun remainingSplitAmount(): Long = (number("amount") ?: base.amountCents) -
            splitLines.sumOf { it.transaction.amountCents }
        fun splitChildren(): List<RuleSplitChild> = splitLines.toList()
        fun markAsSplitParent() {
            flags["parent"] = true
            flags["is_parent"] = true
            strings["payee"] = null
            pendingPayeeName = null
        }
        fun snapshot() = strings.mapValues { "s:${it.value}" } + numbers.mapValues { "i:${it.value}" } + flags.mapValues { "b:${it.value}" }
        fun changed(before: Map<String, String>) = snapshot().filter { before[it.key] != it.value }.keys
        fun transaction() = base.copy(accountId = text("account") ?: base.accountId, date = number("date")?.toInt() ?: base.date,
            amountCents = number("amount") ?: base.amountCents, payeeId = strings["payee"], categoryId = text("category"), notes = text("notes"),
            importedPayee = text("imported_payee"), transferId = text("transfer_id"), parentId = text("parent_id"), scheduleId = text("schedule"),
            cleared = flag("cleared") ?: base.cleared, reconciled = flag("reconciled") ?: base.reconciled,
            isParent = flag("parent") ?: base.isParent, tombstone = deleted || base.tombstone)
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
