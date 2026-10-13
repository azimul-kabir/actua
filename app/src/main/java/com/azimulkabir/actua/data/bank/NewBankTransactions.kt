package com.azimulkabir.actua.data.bank

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The transactions bank sync added that haven't been looked at yet, by account (#1066), as
 * Actual's `newTransactions`/`updatedAccounts`. Device-local and per budget: only transaction ids
 * are kept, and nothing is written to the budget.
 */
class NewBankTransactions(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("bank_sync_new", Context.MODE_PRIVATE)

    /** Unseen added transaction ids in [budgetId], by account id. */
    fun all(budgetId: String): Map<String, Set<String>> = runCatching {
        val stored = JSONObject(preferences.getString(key(budgetId), "{}") ?: "{}")
        stored.keys().asSequence().associateWith { accountId ->
            val ids = stored.getJSONArray(accountId)
            (0 until ids.length()).mapTo(linkedSetOf()) { ids.getString(it) }
        }
    }.getOrDefault(emptyMap())

    /** Adds a sync's [added] ids to those already unseen in [budgetId]. */
    fun record(budgetId: String, added: Map<String, List<String>>) {
        if (added.isEmpty()) return
        synchronized(NewBankTransactions::class.java) { write(budgetId, mergeNewBankTransactions(all(budgetId), added)) }
    }

    /** Marks [accountId]'s new transactions in [budgetId] as seen. */
    fun clear(budgetId: String, accountId: String) {
        synchronized(NewBankTransactions::class.java) {
            val current = all(budgetId)
            if (accountId in current) write(budgetId, current - accountId)
        }
    }

    /** Calls [onChange] when any budget's new transactions change; invoke the returned function to stop. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun write(budgetId: String, byAccount: Map<String, Set<String>>) {
        val json = JSONObject().apply { byAccount.forEach { (accountId, ids) -> put(accountId, JSONArray(ids.toList())) } }
        preferences.edit().apply {
            if (byAccount.isEmpty()) remove(key(budgetId)) else putString(key(budgetId), json.toString())
        }.apply()
    }

    private fun key(budgetId: String) = "new:$budgetId"
}

/** The most unseen transactions kept for one account; older ones stop being marked new. */
internal const val MAX_NEW_BANK_TRANSACTIONS_PER_ACCOUNT = 200

/** [current] with [added] appended per account, keeping each account's newest [limit] ids. */
internal fun mergeNewBankTransactions(
    current: Map<String, Set<String>>,
    added: Map<String, List<String>>,
    limit: Int = MAX_NEW_BANK_TRANSACTIONS_PER_ACCOUNT,
): Map<String, Set<String>> = (current.keys + added.keys).associateWith { accountId ->
    val merged = LinkedHashSet(current[accountId].orEmpty()).apply { addAll(added[accountId].orEmpty()) }
    merged.toList().takeLast(limit).toCollection(linkedSetOf())
}.filterValues { it.isNotEmpty() }
