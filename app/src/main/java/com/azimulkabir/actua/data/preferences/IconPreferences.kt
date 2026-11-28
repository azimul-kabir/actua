package com.azimulkabir.actua.data.preferences

import android.content.Context
import org.json.JSONObject

/** Device-local category icons, scoped to the local budget ID. */
internal class IconPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences("icon_preferences", Context.MODE_PRIVATE)

    fun categories(budgetId: String): Map<String, String> {
        val json = categoryIcons(budgetId)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun setCategory(budgetId: String, categoryId: String, iconKey: String?) {
        val icons = categoryIcons(budgetId)
        if (iconKey == null) icons.remove(categoryId) else icons.put(categoryId, iconKey)
        val key = "category_$budgetId"
        val editor = values.edit()
        if (icons.length() == 0) editor.remove(key) else editor.putString(key, icons.toString())
        editor.apply()
    }

    private fun categoryIcons(budgetId: String): JSONObject =
        JSONObject(values.getString("category_$budgetId", null) ?: "{}")
}
