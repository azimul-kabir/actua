package com.azimulkabir.actua.data.bank

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import org.json.JSONObject

/** The synced `preferences` ids of Actual's per-account Bank Sync toggles (`<name>-<accountId>`). */
enum class BankSyncFlag(val prefix: String, val default: Boolean) {
    ImportPending("sync-import-pending", true),
    ImportNotes("sync-import-notes", true),
    ImportTransactions("sync-import-transactions", true),
    UpdateDates("sync-update-dates", false),
    ReimportDeleted("sync-reimport-deleted", true),
    ;

    fun preferenceId(accountId: String) = "$prefix-$accountId"
}

/**
 * Actual's `custom-sync-mappings-<accountId>` preference: for payments and for deposits, which
 * provider field supplies the transaction's `date`, `payee` and `notes`.
 */
object BankSyncMappings {
    val DEFAULT: Map<String, String> = mapOf("date" to "date", "payee" to "payeeName", "notes" to "notes")
    val FIELDS = listOf("date", "payee", "notes")

    fun preferenceId(accountId: String) = "custom-sync-mappings-$accountId"

    /** `mappingsFromString`: `{"payment": {"date": …, "payee": …, "notes": …}, "deposit": {…}}`. */
    fun parse(text: String): Map<String, Map<String, String>> {
        val root = runCatching { JSONObject(text) }
            .getOrElse { throw IllegalArgumentException("its custom field mapping can't be read. Fix it in Actual's Bank Sync settings.") }
        return root.keys().asSequence().associateWith { direction ->
            val fields = root.optJSONObject(direction) ?: JSONObject()
            fields.keys().asSequence().associateWith { fields.optString(it) }
        }
    }

    /** `mappingsToString`, in Actual's key order. */
    fun toJson(payment: Map<String, String>, deposit: Map<String, String>): String =
        JSONObject()
            .put("payment", JSONObject().apply { FIELDS.forEach { put(it, payment[it] ?: DEFAULT.getValue(it)) } })
            .put("deposit", JSONObject().apply { FIELDS.forEach { put(it, deposit[it] ?: DEFAULT.getValue(it)) } })
            .toString()
}

/** An account's bank sync toggles and field mappings; anything never set reads as Actual's default. */
data class BankSyncAccountSettings(
    val flags: Map<BankSyncFlag, Boolean>,
    val payment: Map<String, String> = BankSyncMappings.DEFAULT,
    val deposit: Map<String, String> = BankSyncMappings.DEFAULT,
    /** Provider fields seen in this account's recent downloads, to pick mappings from. */
    val availableFields: List<String> = emptyList(),
) {
    operator fun get(flag: BankSyncFlag): Boolean = flags[flag] ?: flag.default

    companion object {
        fun read(database: ActualBudgetDatabase, accountId: String, includeFields: Boolean = true): BankSyncAccountSettings {
            val stored = database.fetchPreferences(BankSyncFlag.entries.map { it.preferenceId(accountId) })
            // Actual's `String(value ?? default) === 'true'`.
            val flags = BankSyncFlag.entries.associateWith { flag ->
                (stored[flag.preferenceId(accountId)] ?: flag.default.toString()) == "true"
            }
            // An unreadable mapping shows as the default here; the sync itself reports it as a problem.
            val mappings = database.fetchPreferences(listOf(BankSyncMappings.preferenceId(accountId)))[BankSyncMappings.preferenceId(accountId)]
                ?.let { runCatching { BankSyncMappings.parse(it) }.getOrNull() }
            fun mapping(direction: String) = BankSyncMappings.FIELDS.associateWith { mappings?.get(direction)?.get(it) ?: BankSyncMappings.DEFAULT.getValue(it) }
            return BankSyncAccountSettings(
                flags, mapping("payment"), mapping("deposit"),
                availableFields = if (includeFields) database.syncedFieldNames(accountId) else emptyList(),
            )
        }
    }
}
