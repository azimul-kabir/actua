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
            // A deleted tag with this name is brought back rather than duplicated (see ActualTagWriter.create).
            val revived = database.fetchTagRowByName(normalized)?.deleted == true
            val writer = tagWriter(database)
            val id = writer.create(normalized, color, description)
            if (hidden || revived) writer.update(id, hidden = hidden)
            invalidate()
            ActualTag(id, normalized, color, description, hidden)
        }
    }

    @Synchronized
    fun update(tag: ActualTag, name: String, color: String?, description: String?, hidden: Boolean): Boolean {
        val normalized = validateTagName(name)
        if (normalized != tag.tag && tags().any { it.id != tag.id && it.tag == normalized }) return false
        return withDatabase { database ->
            // Deleted tags keep their name too (`tags.tag` is UNIQUE), so check every row before rewriting notes.
            val owner = database.fetchTagRowByName(normalized)?.id
            if (normalized != tag.tag && owner != null && owner != tag.id) return@withDatabase false
            val writer = tagWriter(database)
            if (normalized != tag.tag) writer.rename(tag.id, tag.tag, normalized)
            writer.update(
                id = tag.id,
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
    require(it.isNotEmpty() && it.none { char -> char == '#' || TagSyntax.isWhitespace(char) }) {
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
        // A # that follows another # never opens a tag (Actual's `(?<!#)#`), so the run and the word after it stay.
        val run = TagSyntax.hashRun(notes, index)
        if (run > 1) {
            val wordEnd = TagSyntax.nameEnd(notes, index + run)
            out.append(notes, index, wordEnd)
            index = wordEnd
            continue
        }
        val start = index + 1
        val end = TagSyntax.nameEnd(notes, start)
        val token = notes.substring(start, end)
        out.append('#').append(if (token == oldName) newName else token)
        index = end
    }
    return out.toString()
}
