package com.azimulkabir.actua.data.budget

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Read-only access to Actual Budget tag metadata for presentation purposes.
 *
 * Tags remain stored in transaction notes. This store only exposes the synced
 * metadata from Actual's `tags` table and never mutates either notes or tag rows.
 */
class TagMetadataStore(context: Context) {
    private val appContext = context.applicationContext
    private val activeBudget = ActiveBudgetStore(appContext)
    private val files = BudgetFileManager(appContext)

    fun activeTagColors(dataGeneration: Long = 0L): Map<String, String> {
        val budgetId = activeBudget.budgetId ?: return emptyMap()
        val file = files.databaseFile(budgetId)
        if (!file.exists()) return emptyMap()
        val modified = file.lastModified()

        synchronized(cacheLock) {
            if (
                cachedBudgetId == budgetId &&
                cachedModified == modified &&
                cachedDataGeneration == dataGeneration
            ) {
                return cachedColors
            }
        }

        val colors = runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use(::readTagColors)
        }.getOrDefault(emptyMap())

        synchronized(cacheLock) {
            cachedBudgetId = budgetId
            cachedModified = modified
            cachedDataGeneration = dataGeneration
            cachedColors = colors
        }
        return colors
    }

    private companion object {
        val cacheLock = Any()
        var cachedBudgetId: String? = null
        var cachedModified: Long = Long.MIN_VALUE
        var cachedDataGeneration: Long = Long.MIN_VALUE
        var cachedColors: Map<String, String> = emptyMap()
    }
}

internal fun readTagColors(database: SQLiteDatabase): Map<String, String> {
    val hasTags = database.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='tags' LIMIT 1",
        null,
    ).use { it.moveToFirst() }
    if (!hasTags) return emptyMap()

    // Match Actual Budget's tags schema directly. Do not assume lifecycle columns
    // such as tombstone/hidden exist, because older Actual databases may not have them.
    return database.rawQuery(
        "SELECT tag, color FROM tags WHERE tag IS NOT NULL",
        null,
    ).use { cursor ->
        buildMap {
            val tagIndex = cursor.getColumnIndexOrThrow("tag")
            val colorIndex = cursor.getColumnIndexOrThrow("color")
            while (cursor.moveToNext()) {
                val tag = cursor.getString(tagIndex)?.takeIf(String::isNotBlank) ?: continue
                val color = if (cursor.isNull(colorIndex)) null else cursor.getString(colorIndex)
                color?.takeIf(String::isNotBlank)?.let { put(tag, it) }
            }
        }
    }
}
