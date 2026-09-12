package com.azimulkabir.actua.data.location

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import java.util.UUID

/** Actual-compatible local-first CRDT mutations for synchronized payee locations. */
class PayeeLocationWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId, nowMillis = nowMillis)

    init { database.maxMessageTimestamp()?.let(com.azimulkabir.actua.data.sync.HlcTimestamp::parse)?.let(clock::advance) }

    @Synchronized
    fun record(payeeId: String, coordinates: Coordinates): PayeeLocation? {
        if (!database.payeeLocationWritesSupported()) return null
        require(payeeId.isNotBlank())
        require(database.fetchPayees().any { it.id == payeeId && it.transferAccountId == null }) {
            "Location requires an ordinary payee"
        }
        if (!LocationUtils.shouldRecord(coordinates, database.fetchPayeeLocations(payeeId))) return null
        val location = PayeeLocation(idFactory(), payeeId, coordinates.latitude, coordinates.longitude, nowMillis())
        persist(listOf(
            message(location.id, "payee_id", location.payeeId),
            message(location.id, "latitude", location.latitude),
            message(location.id, "longitude", location.longitude),
            message(location.id, "created_at", location.createdAt),
            message(location.id, "tombstone", 0),
        ))
        return location
    }

    @Synchronized
    fun delete(locationId: String): Boolean {
        if (!database.payeeLocationWritesSupported()) return false
        if (database.fetchPayeeLocations().none { it.id == locationId }) return false
        persist(listOf(message(locationId, "tombstone", 1)))
        return true
    }

    @Synchronized
    fun deleteAllForPayee(payeeId: String): Int {
        if (!database.payeeLocationWritesSupported()) return 0
        val locations = database.fetchPayeeLocations(payeeId)
        if (locations.isEmpty()) return 0
        persist(locations.map { message(it.id, "tombstone", 1) })
        return locations.size
    }

    private fun message(row: String, column: String, value: Any?) =
        CrdtMessage(clock.send(), "payee_locations", row, column, CrdtValue.serialize(value))

    private fun persist(messages: List<CrdtMessage>) {
        database.receiveMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(
            clock.current().toString(), database.deriveMerkleFromMessageLog().root,
        ))
        onWrite()
    }
}
