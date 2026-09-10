package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/** Owns creation and reset of Actua's local-only demonstration budget. */
object DemoBudgetManager {
    const val BUDGET_ID = "demo"
    const val BUDGET_NAME = "Actua Demo Budget"

    fun isDemoBudget(budgetId: String?): Boolean = budgetId == BUDGET_ID

    /**
     * Recreates the demo from the current blank-budget schema, then layers curated
     * sample data on top. The metadata intentionally contains no cloud identity,
     * so the normal sync runner cannot upload or merge this budget with a server.
     */
    fun recreate(files: BudgetFileManager): BudgetMetadata {
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
                // Keep the demo card preference in the same shape as normal Actua writes.
                database.execSQL(
                    "UPDATE preferences SET value=? WHERE id=?",
                    arrayOf(
                        JSONObject()
                            .put("statementDay", 20)
                            .put("dueOffsetDays", 15)
                            .put("limit", 500000)
                            .toString(),
                        "actuali:credit_card:demo-account-credit",
                    ),
                )
            }

            val metadataJson = JSONObject()
                .put("id", BUDGET_ID)
                .put("budgetName", BUDGET_NAME)
                .put("demo", true)
            files.metadataFile(BUDGET_ID).writeText(metadataJson.toString())
            return BudgetMetadata.fromJson(metadataJson)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }
}
