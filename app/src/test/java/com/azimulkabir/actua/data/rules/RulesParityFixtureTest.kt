package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Replays the #669 rules fixture through Actua's [RulesEngine]. The fixture is produced by
 * docs/tools/rules-fixture/generate.mjs from Actual's own `runRules` (@actual-app/api 26.9.0), so
 * every expected value is what Actual does, not what this test's author thinks it does. A
 * difference fails unless [KNOWN_DIVERGENCES] lists it with its issue; a listed difference that no
 * longer occurs also fails, so the list stays current. Never regenerate the fixture to pass.
 */
class RulesParityFixtureTest {
    private val fixture = JSONObject(
        requireNotNull(javaClass.getResource("/rules-parity/upstream-26.9.0.json")) {
            "Missing fixture: run docs/tools/rules-fixture/generate.mjs"
        }.readText(),
    )
    private val entities = fixture.getJSONObject("entities")
    private val payees = entities.getJSONArray("payees").objects().associateBy { it.getString("id") }
    private val payeeNames = payees.mapValues { it.value.optString("name") }
    private val categories = entities.getJSONArray("categories").objects()
    private val context = RuleContext(
        offBudgetAccountIds = entities.getJSONArray("accounts").objects()
            .filter { it.optInt("offbudget") == 1 }.mapTo(mutableSetOf()) { it.getString("id") },
        accountNames = entities.getJSONArray("accounts").objects().associate { it.getString("id") to it.getString("name") },
        categoryNames = categories.associate { it.getString("id") to it.getString("name") },
        categoryGroupIds = categories.associate { it.getString("id") to it.getString("cat_group") },
        categoryGroupNames = entities.getJSONArray("categoryGroups").objects()
            .associate { it.getString("id") to it.getString("name") },
        payeeNames = payeeNames,
    )
    private val rules = fixture.getJSONArray("rules").objects().map {
        Rule.parse(
            it.getString("id"), it.optString("stage").ifBlank { null }.takeUnless { stage -> stage == "null" },
            it.optString("conditions_op"), it.getString("conditions"), it.getString("actions"),
        )
    }

    @Test
    fun actuaRulesEngineMatchesActualOnEveryFixtureCase() {
        val cases = fixture.getJSONArray("cases").objects()
        assertTrue("The fixture has no cases", cases.isNotEmpty())
        val differences = linkedMapOf<String, String>()
        val counters = mutableMapOf<String, Int>()
        for (case in cases) {
            val name = case.getString("name")
            val index = counters.merge(name, 1, Int::plus)!!
            val input = case.getJSONObject("input")
            val expected = case.getJSONObject("output")
            val result = RulesEngine.apply(input.toTransaction(), rules, context)
            val actual = result.toOutput()
            for (field in FIELDS) {
                val want = expected.normalized(field)
                val got = actual[field]
                if (want != got) differences["$name #$index $field"] = "Actual $want, Actua $got"
            }
        }
        val unexpected = differences.filterKeys { it !in KNOWN_DIVERGENCES }
        val stale = KNOWN_DIVERGENCES.keys.filterNot(differences::containsKey)
        if (unexpected.isNotEmpty() || stale.isNotEmpty()) {
            fail(buildString {
                appendLine("Rules fixture differences (${cases.size} cases):")
                unexpected.forEach { (key, value) -> appendLine("  NEW  $key: $value") }
                stale.forEach { appendLine("  FIXED (remove from KNOWN_DIVERGENCES)  $it") }
            })
        }
    }

    private fun JSONObject.toTransaction(): ActualTransaction {
        val payee = optNullableString("payee")
        val category = optNullableString("category")
        return ActualTransaction(
            id = getString("id"),
            accountId = getString("account"),
            date = getString("date").replace("-", "").toInt(),
            amountCents = getLong("amount"),
            payeeId = payee,
            payeeName = payee?.let(payeeNames::get),
            categoryId = category,
            categoryName = category?.let(context.categoryNames::get),
            notes = optNullableString("notes"),
            cleared = optBoolean("cleared", false),
            reconciled = false,
            transferId = null,
            isParent = false,
            parentId = null,
            tombstone = false,
            sortOrder = null,
            importedPayee = optNullableString("imported_payee"),
            scheduleId = optNullableString("schedule"),
            transferAccountId = payee?.let(payees::get)?.optNullableString("transfer_acct"),
        )
    }

    private fun RuleRunResult.toOutput(): Map<String, String?> = mapOf(
        "account" to transaction.accountId,
        "date" to transaction.date.toString().let { "${it.take(4)}-${it.substring(4, 6)}-${it.takeLast(2)}" },
        "amount" to transaction.amountCents.toString(),
        "payee" to (pendingPayeeName ?: transaction.payeeId?.let(payeeNames::get))?.lowercase(),
        "category" to transaction.categoryId,
        "notes" to transaction.notes,
        "cleared" to transaction.cleared.toString(),
        "schedule" to transaction.scheduleId,
        "tombstone" to (if (isDeleted) "1" else "0"),
        "subtransactions" to JSONArray().apply {
            splitChildren.forEach { child ->
                put(JSONObject().put("amount", child.transaction.amountCents)
                    .put("category", child.transaction.categoryId ?: JSONObject.NULL))
            }
        }.toString(),
    )

    /** Actual's output in the same shape: payee by (case-insensitive) name, everything else as stored. */
    private fun JSONObject.normalized(field: String): String? = when (field) {
        "payee" -> optNullableString("payee")?.let { payeeNames[it] ?: optNullableString("payee_name") ?: it }?.lowercase()
        "amount" -> if (isNull("amount")) null else getLong("amount").toString()
        "cleared" -> if (isNull("cleared")) null else (get("cleared") == true || get("cleared") == 1).toString()
        "tombstone" -> optInt("tombstone").toString()
        "subtransactions" -> getJSONArray("subtransactions").let { if (it.length() == 0) "[]" else it.toString() }
        else -> optNullableString(field)
    }

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else get(key).toString()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private companion object {
        val FIELDS = listOf("account", "date", "amount", "payee", "category", "notes", "cleared", "schedule", "tombstone", "subtransactions")

        /** "<case name> #<transaction> <field>" → the issue tracking the divergence. */
        val KNOWN_DIVERGENCES: Map<String, String> = emptyMap()
    }
}
