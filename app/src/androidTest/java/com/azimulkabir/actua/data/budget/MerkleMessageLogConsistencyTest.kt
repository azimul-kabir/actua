package com.azimulkabir.actua.data.budget

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.MerkleTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.random.Random

class MerkleMessageLogConsistencyTest {
    @Test fun persistedIncrementalCandidateMatchesRealLogAfterOutOfOrderAndDuplicateBatches() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "merkle-consistency-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val file = File(directory, "db.sqlite")
        try {
            BlankBudgetFactory.create(file)
            val messages = List(200) { index ->
                CrdtMessage(
                    HlcTimestamp(1_704_067_200_000L + index * 60_000, 0, "0000000000000001"),
                    "accounts", "synthetic", "name", "S:Synthetic $index",
                )
            }.shuffled(Random(1600))
            var candidate = MerkleTree()
            messages.chunked(17).forEach { batch ->
                ActualBudgetDatabase.open(file).use { database ->
                    // Clock persisted by the previous batch, like a new writer after reopening.
                    candidate = database.loadClock()?.let { MerkleTree(it.merkle) } ?: candidate
                    val inserted = database.insertMessages(batch + batch)
                    assertEquals(batch.size, inserted.size)
                    inserted.forEach { candidate = candidate.inserting(it.timestamp) }
                    candidate = candidate.pruned()
                    assertEquals(database.deriveMerkleFromMessageLog(), candidate)
                    assertTrue(database.insertMessages(batch).isEmpty())
                    assertEquals(database.deriveMerkleFromMessageLog(), candidate)
                    database.saveClock(ActualBudgetDatabase.ClockRecord(batch.maxOf { it.timestamp }.toString(), candidate.root))
                }
            }
        } finally {
            BudgetOpenProbe.invalidate(file)
            directory.deleteRecursively()
        }
    }
}
