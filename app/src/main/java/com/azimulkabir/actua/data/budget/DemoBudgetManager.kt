package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Owns creation, compatibility repair, and reset of Actua's local-only demonstration budget. */
object DemoBudgetManager {
    const val BUDGET_ID = "demo"
    const val BUDGET_NAME = "Actua Demo Budget"

    fun isDemoBudget(budgetId: String?): Boolean = budgetId == BUDGET_ID

    /** Held by [recreate]; [repairIfNeeded] skips while it is held so a reset is never seeded twice. */
    private val recreateLock = ReentrantLock()

    /**
     * Beta.6 could create a demo with a schedules table that lacked sort_order,
     * while the schedule read model queried that column during app startup. Repair
     * only that incompatible demo shape so existing fixed demo edits are preserved.
     *
     * Reads of the active budget call this, so it can race a reset. A demo that is being
     * recreated, cannot be opened, or has no schedules table yet is left alone.
     */
    fun repairIfNeeded(files: BudgetFileManager) {
        if (!recreateLock.tryLock()) return
        try {
            if (needsRepair(files)) recreate(files)
        } finally {
            recreateLock.unlock()
        }
    }

    private fun needsRepair(files: BudgetFileManager): Boolean {
        val databaseFile = files.databaseFile(BUDGET_ID)
        if (!databaseFile.isFile) return false
        return runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                database.rawQuery("PRAGMA table_info(schedules)", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
                    columns.isNotEmpty() && "sort_order" !in columns
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Recreates the demo from the current blank-budget schema, then layers curated
     * sample data on top. The metadata intentionally contains no cloud identity,
     * so the normal sync runner cannot upload or merge this budget with a server.
     */
    fun recreate(files: BudgetFileManager): BudgetMetadata = recreateLock.withLock {
        val directory = files.budgetDirectory(BUDGET_ID)
        if (directory.exists()) {
            check(directory.deleteRecursively()) { "Unable to reset the demo budget" }
        }
        check(directory.mkdirs()) { "Unable to create the demo budget directory" }

        try {
            BlankBudgetFactory.create(files.databaseFile(BUDGET_ID))
            SQLiteDatabase.openDatabase(
                files.databaseFile(BUDGET_ID).path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { database ->
                DemoBudgetSeeder.seed(database)
                // Actual resolves transaction/schedule payees through payee_mapping.
                database.execSQL(
                    "INSERT OR REPLACE INTO payee_mapping(id,targetId) SELECT id,id FROM payees WHERE tombstone=0",
                )
            }

            val metadataJson = JSONObject()
                .put("id", BUDGET_ID)
                .put("budgetName", BUDGET_NAME)
                .put("demo", true)
            files.metadataFile(BUDGET_ID).writeText(metadataJson.toString())
            BudgetMetadata.fromJson(metadataJson)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }
}
