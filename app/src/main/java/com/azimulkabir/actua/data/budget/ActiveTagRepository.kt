package com.azimulkabir.actua.data.budget

import android.content.Context
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import com.azimulkabir.actua.widget.WidgetUpdater

/** UI-facing access to the active budget's canonical Actual tag dataset. */
class ActiveTagRepository(context: Context) {
    private val appContext = context.applicationContext
    private val metadata = TagMetadataStore(appContext)

    fun tags(dataGeneration: Long = 0L): List<ActualTag> = metadata.activeTags(dataGeneration)

    @Synchronized
    fun create(name: String): ActualTag? {
        val normalized = name.trim()
        val existing = tags().firstOrNull { it.tag == normalized }
        if (existing != null) return existing

        val budgetId = ActiveBudgetStore(appContext).budgetId ?: return null
        val file = BudgetFileManager(appContext).databaseFile(budgetId)
        if (!file.exists()) return null
        return ActualBudgetDatabase.open(file).use { database ->
            val writer = ActualTagWriter(database, onWrite = {
                ActualSyncScheduler.scheduleMutation(appContext)
                WidgetUpdater.requestAll(appContext)
            })
            val id = writer.create(normalized)
            ActualTag(id = id, tag = normalized)
        }
    }
}
