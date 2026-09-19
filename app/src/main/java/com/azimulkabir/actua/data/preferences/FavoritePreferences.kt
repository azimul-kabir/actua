package com.azimulkabir.actua.data.preferences

import android.content.Context

/** Device-local quick-access pins; Actual has no equivalent synced fields. */
class FavoritePreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun ids(budgetId: String, type: Type): Set<String> =
        values.getStringSet(key(budgetId, type), emptySet()).orEmpty().toSet()

    fun contains(budgetId: String, type: Type, id: String): Boolean = id in ids(budgetId, type)

    fun set(budgetId: String, type: Type, id: String, favorite: Boolean) {
        val updated = ids(budgetId, type).toMutableSet().apply {
            if (favorite) add(id) else remove(id)
        }
        values.edit().putStringSet(key(budgetId, type), updated).apply()
    }

    enum class Type { CATEGORY, ACCOUNT, REPORT }

    private fun key(budgetId: String, type: Type) = "${type.name.lowercase()}_$budgetId"
    private companion object { const val NAME = "favorite_preferences" }
}
