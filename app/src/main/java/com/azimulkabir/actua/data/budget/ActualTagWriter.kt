package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import java.util.UUID

/**
 * Canonical Actual tag mutations through the same CRDT message path as other entities.
 *
 * [update] changes metadata only; [rename] also rewrites the tag in transaction notes, like
 * Actual's `renameTag`.
 */
class ActualTagWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId, highWater = database::messageLogHighWater)

    init { database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance) }

    /**
     * Actual's `createTag`: a name that already has a row, even a deleted one, reuses that row and
     * clears its tombstone. `tags.tag` is UNIQUE, so a second row with the name would fail to apply
     * here and on every Actual client.
     */
    @Synchronized
    fun create(tag: String, color: String? = null, description: String? = null): String {
        val name = validTag(tag)
        val id = database.fetchTagRowByName(name)?.id ?: idFactory()
        persist(fields(id, linkedMapOf(
            "tag" to name,
            "color" to color?.trim()?.takeIf(String::isNotEmpty),
            "description" to description,
            "tombstone" to 0,
        )))
        return id
    }

    @Synchronized
    fun update(
        id: String,
        tag: String? = null,
        color: String? = null,
        description: String? = null,
        hidden: Boolean? = null,
        updateColor: Boolean = false,
        updateDescription: Boolean = false,
    ) {
        require(id.isNotBlank())
        val values = linkedMapOf<String, Any?>()
        tag?.let {
            val name = validTag(it)
            // Actual's `renameTag` rejects a name held by any other row, deleted ones included.
            require(database.fetchTagRowByName(name)?.id.let { owner -> owner == null || owner == id }) {
                "A tag with that name already exists"
            }
            values["tag"] = name
        }
        if (updateColor) values["color"] = color?.trim()?.takeIf(String::isNotEmpty)
        if (updateDescription) values["description"] = description
        hidden?.let { values["hidden"] = if (it) 1 else 0 }
        require(values.isNotEmpty()) { "No tag fields to update" }
        persist(fields(id, values))
    }

    /**
     * Actual's `renameTag`: the new name and every live transaction row whose notes carry `#[oldName]`,
     * split lines included, written in one batch so notes and tag never disagree.
     */
    @Synchronized
    fun rename(id: String, oldName: String, newName: String) {
        require(id.isNotBlank())
        val name = validTag(newName)
        if (name == oldName) return
        require(database.fetchTagRowByName(name)?.id.let { owner -> owner == null || owner == id }) {
            "A tag with that name already exists"
        }
        val noteMessages = database.fetchNotesWithHashes().mapNotNull { (transactionId, notes) ->
            val renamed = renameTagInNotes(notes, oldName, name)
            if (renamed == notes) null
            else CrdtMessage(clock.send(), "transactions", transactionId, "notes", CrdtValue.serialize(renamed))
        }
        persist(fields(id, mapOf("tag" to name)) + noteMessages)
    }

    /** Actual deletes managed metadata by tombstoning the tag; note text is left intact. */
    fun delete(id: String) {
        require(id.isNotBlank())
        persist(fields(id, mapOf("tombstone" to 1)))
    }

    private fun fields(id: String, values: Map<String, Any?>): List<CrdtMessage> =
        values.map { (column, value) ->
            CrdtMessage(clock.send(), "tags", id, column, CrdtValue.serialize(value))
        }

    private fun persist(messages: List<CrdtMessage>) {
        database.applyLocalMessages(messages)
        database.saveClock(
            ActualBudgetDatabase.ClockRecord(
                clock.current().toString(),
                database.deriveMerkleFromMessageLog().root,
            ),
        )
        onWrite()
    }

    private fun validTag(value: String): String = value.trim().also {
        require(TAG_NAME.matches(it)) { "Tag names cannot be empty or contain whitespace or #" }
    }

    private companion object {
        val TAG_NAME = Regex("^[^#\\s]+$")
    }
}
