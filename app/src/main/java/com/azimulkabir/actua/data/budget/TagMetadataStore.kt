package com.azimulkabir.actua.data.budget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.data.budget.model.ActualTagCapabilities

/** Read-only access to Actual Budget's canonical synced tag metadata. */
class TagMetadataStore(context: Context) {
    private val appContext = context.applicationContext
    private val activeBudget = ActiveBudgetStore(appContext)
    private val files = BudgetFileManager(appContext)

    fun activeTags(dataGeneration: Long = 0L): List<ActualTag> = snapshot(dataGeneration).tags
    fun activeTagColors(dataGeneration: Long = 0L): Map<String, String> = activeTags(dataGeneration).mapNotNull { tag -> tag.color?.takeIf(String::isNotBlank)?.let { tag.tag to it } }.toMap()
    fun activeCapabilities(dataGeneration: Long = 0L): ActualTagCapabilities = snapshot(dataGeneration).capabilities

    fun invalidate() = synchronized(cacheLock) {
        cachedBudgetId = null
        cachedModified = Long.MIN_VALUE
        cachedDataGeneration = Long.MIN_VALUE
        cachedSnapshot = TagSnapshot.EMPTY
    }

    private fun snapshot(dataGeneration: Long): TagSnapshot {
        val budgetId = activeBudget.budgetId ?: return TagSnapshot.EMPTY
        val file = files.databaseFile(budgetId)
        if (!file.exists()) return TagSnapshot.EMPTY
        val modified = file.lastModified()
        synchronized(cacheLock) {
            if (cachedBudgetId == budgetId && cachedModified == modified && cachedDataGeneration == dataGeneration) return cachedSnapshot
        }
        val value = runCatching { SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use(::readTags) }.getOrDefault(TagSnapshot.EMPTY)
        synchronized(cacheLock) {
            cachedBudgetId = budgetId; cachedModified = modified; cachedDataGeneration = dataGeneration; cachedSnapshot = value
        }
        return value
    }

    private companion object {
        val cacheLock = Any(); var cachedBudgetId: String? = null; var cachedModified: Long = Long.MIN_VALUE
        var cachedDataGeneration: Long = Long.MIN_VALUE; var cachedSnapshot: TagSnapshot = TagSnapshot.EMPTY
    }
}

internal data class TagSnapshot(val tags: List<ActualTag>, val capabilities: ActualTagCapabilities) {
    companion object { val EMPTY = TagSnapshot(emptyList(), ActualTagCapabilities(available = false, hidden = false)) }
}

internal fun readTags(database: SQLiteDatabase): TagSnapshot {
    val hasTags = database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='tags' LIMIT 1", null).use { it.moveToFirst() }
    if (!hasTags) return TagSnapshot.EMPTY
    val columns = database.rawQuery("PRAGMA table_info(tags)", null).use { cursor -> buildSet { val nameIndex = cursor.getColumnIndexOrThrow("name"); while (cursor.moveToNext()) add(cursor.getString(nameIndex)) } }
    if ("id" !in columns || "tag" !in columns) return TagSnapshot.EMPTY
    val colorExpr = if ("color" in columns) "color" else "NULL AS color"
    val descriptionExpr = if ("description" in columns) "description" else "NULL AS description"
    val hiddenExpr = if ("hidden" in columns) "hidden" else "0 AS hidden"
    val tombstoneFilter = if ("tombstone" in columns) "WHERE tombstone = 0 OR tombstone IS NULL" else ""
    val tags = database.rawQuery("SELECT id, tag, $colorExpr, $descriptionExpr, $hiddenExpr FROM tags $tombstoneFilter ORDER BY tag COLLATE NOCASE, tag", null).use { cursor -> buildList {
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)?.takeIf(String::isNotBlank) ?: continue; val tag = cursor.getString(1)?.takeIf(String::isNotBlank) ?: continue
            add(ActualTag(id, tag, if (cursor.isNull(2)) null else cursor.getString(2), if (cursor.isNull(3)) null else cursor.getString(3), !cursor.isNull(4) && cursor.getInt(4) == 1))
        }
    } }
    return TagSnapshot(tags, ActualTagCapabilities(available = true, hidden = "hidden" in columns))
}

internal fun readTagColors(database: SQLiteDatabase): Map<String, String> = readTags(database).tags.mapNotNull { tag -> tag.color?.takeIf(String::isNotBlank)?.let { tag.tag to it } }.toMap()
