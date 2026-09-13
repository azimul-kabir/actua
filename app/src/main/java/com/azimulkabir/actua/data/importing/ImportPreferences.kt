package com.azimulkabir.actua.data.importing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ImportHistoryEntry(
    val sourceName: String,
    val format: StatementFormat,
    val accountName: String,
    val imported: Int,
    val skipped: Int,
    val timestampMillis: Long,
)

class ImportPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("transaction_import", Context.MODE_PRIVATE)

    fun saveProfile(name: String, mapping: ImportColumnMapping) {
        val json = JSONObject().put("roles", JSONArray(mapping.roles.map { it.name }))
            .put("datePattern", mapping.datePattern).put("expensesArePositive", mapping.expensesArePositive)
        preferences.edit().putString("profile:$name", json.toString()).putString("lastProfile", name).apply()
    }

    fun profile(name: String): ImportColumnMapping? = runCatching {
        val json = JSONObject(preferences.getString("profile:$name", null) ?: return null)
        val roles = json.getJSONArray("roles").let { array ->
            (0 until array.length()).map { ImportColumnRole.valueOf(array.getString(it)) }
        }
        ImportColumnMapping(roles, json.optString("datePattern", "Auto"), json.optBoolean("expensesArePositive"))
    }.getOrNull()

    fun profileNames(): List<String> = preferences.all.keys.filter { it.startsWith("profile:") }
        .map { it.removePrefix("profile:") }.sorted()

    fun lastProfile(): Pair<String, ImportColumnMapping>? {
        val name = preferences.getString("lastProfile", null) ?: return null
        return profile(name)?.let { name to it }
    }

    fun addHistory(entry: ImportHistoryEntry) {
        val rows = history().toMutableList().apply { add(0, entry) }.take(20)
        val json = JSONArray(rows.map { row -> JSONObject()
            .put("source", row.sourceName).put("format", row.format.name).put("account", row.accountName)
            .put("imported", row.imported).put("skipped", row.skipped).put("time", row.timestampMillis) })
        preferences.edit().putString("history", json.toString()).apply()
    }

    fun history(): List<ImportHistoryEntry> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let { row ->
            ImportHistoryEntry(row.getString("source"), StatementFormat.valueOf(row.getString("format")),
                row.getString("account"), row.getInt("imported"), row.getInt("skipped"), row.getLong("time"))
        } }
    }.getOrDefault(emptyList())

    fun clearHistory() = preferences.edit().remove("history").apply()
}
