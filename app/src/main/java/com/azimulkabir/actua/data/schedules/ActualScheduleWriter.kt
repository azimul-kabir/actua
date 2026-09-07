package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock

class ActualScheduleWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId)
    init { database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance) }

    @Synchronized
    fun advance(nextDateRowId: String, newDate: DayDate, baseNextDateTimestamp: Long?) {
        val messages = listOf(
            message(nextDateRowId, "local_next_date", newDate.yyyymmdd),
            message(nextDateRowId, "local_next_date_ts", baseNextDateTimestamp),
        )
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    @Synchronized
    fun apply(plan: ScheduleWritePlan) {
        val messages = plan.writes.flatMap { write -> write.fields.map { (column, value) ->
            CrdtMessage(clock.send(), write.dataset, write.row, column, CrdtValue.serialize(value))
        } }
        database.applyLocalMessages(messages)
        plan.conditions?.let { database.writeScheduleJsonPaths(plan.scheduleId, it) }
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    private fun message(row: String, column: String, value: Any?) =
        CrdtMessage(clock.send(), "schedules_next_date", row, column, CrdtValue.serialize(value))
}
