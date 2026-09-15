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
        val normalized = validateTagName(name)
        val existing = tags().firstOrNull { it.tag == normalized }
        if (existing != null) return existing
        return withDatabase { database ->
            val writer = tagWriter(database)
            val id = writer.create(normalized, color, description)
            if (hidden) writer.update(id, hidden = true)
            invalidate()
            ActualTag(id, normalized, color, description, hidden)
        }
    }

    @Synchronized
    fun update(tag: ActualTag, name: String, color: String?, description: String?, hidden: Boolean): Boolean {
        val normalized = validateTagName(name)
        if (normalized != tag.tag && tags().any { it.id != tag.id && it.tag == normalized }) return false
        return withDatabase { database ->
            if (normalized != tag.tag) {
                val transactions = database.fetchTransactions(limit = Int.MAX_VALUE)
                val changed = transactions.mapNotNull { transaction ->
                    val renamed = renameTagInNotes(transaction.notes, tag.tag, normalized)
                    if (renamed == transaction.notes) null else transaction to transaction.copy(notes = renamed)
                }
                if (changed.isNotEmpty()) {
                    ActualTransactionWriter(database).mutate(updates = changed)
                }
            }
            tagWriter(database).update(
                id = tag.id,
                tag = normalized.takeIf { it != tag.tag },
                color = color,
                description = description,
                hidden = hidden,
                updateColor = true,
                updateDescription = true,
            )
            invalidate()
            true
        } ?: false
    }

    @Synchronized
    fun delete(tag: ActualTag): Boolean = withDatabase { database ->
        tagWriter(database).delete(tag.id)
        invalidate()
        true
    } ?: false

    private fun tagWriter(database: ActualBudgetDatabase) = ActualTagWriter(database, onWrite = ::scheduleMutation)

    private fun scheduleMutation() {
        ActualSyncScheduler.scheduleMutation(appContext)
        WidgetUpdater.requestAll(appContext)
    }

    private fun invalidate() {
        metadata.invalidate()
        WidgetUpdater.requestAll(appContext)
    }

    private fun <T> withDatabase(block: (ActualBudgetDatabase) -> T): T? {
        val budgetId = ActiveBudgetStore(appContext).budgetId ?: return null
        val file = BudgetFileManager(appContext).databaseFile(budgetId)
        if (!file.exists()) return null
        return ActualBudgetDatabase.open(file).use(block)
    }
}

internal fun validateTagName(value: String): String = value.trim().also {
    require(it.isNotEmpty() && it.none { char -> char == '#' || char.isWhitespace() }) {
        "Tag names cannot be empty or contain whitespace or #"
    }
}

/** Actual-compatible token rename: escaped ## hashes and longer tag names are left untouched. */
internal fun renameTagInNotes(notes: String, oldName: String, newName: String): String {
    if (notes.isEmpty() || oldName == newName) return notes
    val out = StringBuilder(notes.length)
    var index = 0
    while (index < notes.length) {
        if (notes[index] != '#') {
            out.append(notes[index++])
            continue
        }
        if (index + 1 < notes.length && notes[index + 1] == '#') {
            out.append("##")
            index += 2
            while (index < notes.length && notes[index] != '#' && !notes[index].isWhitespace()) out.append(notes[index++])
            continue
        }
        val start = index + 1
        var end = start
        while (end < notes.length && notes[end] != '#' && !notes[end].isWhitespace()) end++
        val token = notes.substring(start, end)
        out.append('#').append(if (token == oldName) newName else token)
        index = end
    }
    return out.toString()
}
