package com.azimulkabir.actua.data.preferences

import android.content.Context
import org.json.JSONObject

/** Device-local entity icons, scoped to the local budget ID. */
internal class IconPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences("icon_preferences", Context.MODE_PRIVATE)

    fun categories(budgetId: String): Map<String, String> = icons("category", budgetId)
    fun accounts(budgetId: String): Map<String, String> = icons("account", budgetId)

    fun setCategory(budgetId: String, categoryId: String, iconKey: String?) =
        setIcon("category", budgetId, categoryId, iconKey)

    fun setAccount(budgetId: String, accountId: String, iconKey: String?) =
        setIcon("account", budgetId, accountId, iconKey)

    private fun icons(kind: String, budgetId: String): Map<String, String> {
        val json = readIcons("${kind}_$budgetId")
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private fun setIcon(kind: String, budgetId: String, id: String, iconKey: String?) {
        val key = "${kind}_$budgetId"
        val icons = readIcons(key)
        if (iconKey == null) icons.remove(id) else icons.put(id, iconKey)
        val editor = values.edit()
        if (icons.length() == 0) editor.remove(key) else editor.putString(key, icons.toString())
        editor.apply()
    }

    private fun readIcons(key: String): JSONObject = JSONObject(values.getString(key, null) ?: "{}")
}
