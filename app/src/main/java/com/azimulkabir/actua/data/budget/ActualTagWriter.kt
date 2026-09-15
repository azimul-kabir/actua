package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import java.util.UUID

/**
 * Canonical Actual tag mutations through the same CRDT message path as other entities.
 *
 * This deliberately does not rewrite transaction notes when a tag name changes. Actual's
 * full rename operation is a separate semantic operation because it updates matching note
 * tokens atomically; that behavior belongs to the rename/integration slice rather than a
 * metadata-only update.
 */
class ActualTagWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId)

    init { database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance) }

    @Synchronized
    fun create(tag: String, color: String? = null, description: String? = null): String {
        val id = idFactory()
        persist(fields(id, linkedMapOf(
            "tag" to validTag(tag),
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
        tag?.let { values["tag"] = validTag(it) }
        if (updateColor) values["color"] = color?.trim()?.takeIf(String::isNotEmpty)
        if (updateDescription) values["description"] = description
        hidden?.let { values["hidden"] = if (it) 1 else 0 }
        require(values.isNotEmpty()) { "No tag fields to update" }
        persist(fields(id, values))
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
