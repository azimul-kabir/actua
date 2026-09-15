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
    fun capabilities(dataGeneration: Long = 0L) = metadata.activeCapabilities(dataGeneration)

    @Synchronized
    fun create(name: String, color: String? = null, description: String? = null, hidden: Boolean = false): ActualTag? {
        val normalized = name.trim()
        val existing = tags().firstOrNull { it.tag == normalized }
        if (existing != null) return existing
        return withWriter { writer ->
            val id = writer.create(normalized, color, description)
            if (hidden) writer.update(id, hidden = true)
            ActualTag(id, normalized, color, description, hidden)
        }
    }

    @Synchronized
    fun update(tag: ActualTag, name: String, color: String?, description: String?, hidden: Boolean): Boolean {
        val normalized = name.trim()
        if (normalized != tag.tag && tags().any { it.id != tag.id && it.tag == normalized }) return false
        return withWriter { writer ->
            writer.update(
                id = tag.id,
                tag = normalized.takeIf { it != tag.tag },
                color = color,
                description = description,
                hidden = hidden,
                updateColor = true,
                updateDescription = true,
            )
            true
        } ?: false
    }

    @Synchronized
    fun delete(tag: ActualTag): Boolean = withWriter { writer -> writer.delete(tag.id); true } ?: false

    private fun <T> withWriter(block: (ActualTagWriter) -> T): T? {
        val budgetId = ActiveBudgetStore(appContext).budgetId ?: return null
        val file = BudgetFileManager(appContext).databaseFile(budgetId)
        if (!file.exists()) return null
        return ActualBudgetDatabase.open(file).use { database ->
            block(ActualTagWriter(database, onWrite = {
                ActualSyncScheduler.scheduleMutation(appContext)
                WidgetUpdater.requestAll(appContext)
            }))
        }
    }
}
