package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.MerkleTree
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.system.measureNanoTime

/** Opt-in measurements for #1600; no timing thresholds on shared CI or emulator claims of real-device speed. */
class MerkleWriteCostBenchmarkTest {
    @Test fun measureTransactionSaveAndMerkleBookkeeping() {
        assumeTrue(
            "Run explicitly with -e merkleWriteBenchmark true",
            InstrumentationRegistry.getArguments().getString("merkleWriteBenchmark") == "true",
        )
        report("device=${Build.MODEL}, API=${Build.VERSION.SDK_INT}, ABI=${Build.SUPPORTED_ABIS.first()}")
        for (size in listOf(10_000, 100_000, 500_000)) {
            withBudget(size) { database ->
                var notifications = 0
                val writer = ActualTransactionWriter(
                    database, nodeId = "0000000000000001", nowMillis = { SAVE_TIME },
                    onWrite = { notifications++ },
                )
                val original = requireNotNull(database.fetchTransaction("measurement"))
                var edit = 0
                val save = samples {
                    writer.updateTransaction(original.copy(notes = "Synthetic edit ${edit++}"), setOf("notes"))
                }
                val rebuild = samples { database.deriveMerkleFromMessageLog() }
                val current = database.deriveMerkleFromMessageLog()
                assertEquals(current.root, database.loadClock()?.merkle)
                assertEquals(WARMUPS + SAMPLES, notifications)
                assertEquals(size + WARMUPS + SAMPLES, database.messageCountAfter(HlcTimestamp.ZERO.toString()))

                // A lower bound for the proposed bookkeeping only, NOT an optimized transaction save.
                // Start each sample from the same known-correct baseline; never change production writes.
                val next = HlcTimestamp(SAVE_TIME + 60_000, 0, "0000000000000001")
                var candidate = MerkleTree()
                val insertAndPrune = samples { candidate = current.inserting(next).pruned() }
                assertEquals(current.root.hash xor next.hash(), candidate.root.hash)
                report("seed=$size, save_ns=${save.joinToString(",")}")
                report("seed=$size, rebuild_ns=${rebuild.joinToString(",")}")
                report("seed=$size, incremental_tree_only_ns=${insertAndPrune.joinToString(",")}")
            }
        }
    }

    private fun samples(block: () -> Unit): List<Long> {
        repeat(WARMUPS) { block() }
        return List(SAMPLES) { measureNanoTime(block) }
    }

    private fun report(text: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "MERKLE_WRITE_COST $text\n")
        })
    }

    private fun withBudget(size: Int, block: (ActualBudgetDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "merkle-cost-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val file = File(directory, "db.sqlite")
        try {
            BlankBudgetFactory.create(file)
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.beginTransaction()
                try {
                    raw.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone) VALUES ('checking', 'Synthetic Checking', 0, 0, 0)")
                    raw.execSQL("INSERT INTO transactions (id, acct, amount, date, notes, tombstone) VALUES ('measurement', 'checking', -1234, 20240101, 'Synthetic', 0)")
                    raw.compileStatement(
                        "INSERT INTO messages_crdt (timestamp, dataset, row, `column`, value) VALUES (?, 'transactions', 'measurement', 'notes', 'S:Synthetic')",
                    ).use { insert ->
                        repeat(size) { index ->
                            insert.bindString(1, HlcTimestamp(HISTORY_START + index * 1_000L, 0, "0000000000000002").toString())
                            insert.executeInsert()
                        }
                    }
                    raw.setTransactionSuccessful()
                } finally {
                    raw.endTransaction()
                }
            }
            ActualBudgetDatabase.open(file).use(block)
        } finally {
            BudgetOpenProbe.invalidate(file)
            directory.deleteRecursively()
        }
    }

    companion object {
        private const val WARMUPS = 2
        private const val SAMPLES = 7
        private const val HISTORY_START = 1_704_067_200_000L // 2024-01-01 UTC
        private const val SAVE_TIME = 1_799_798_400_000L // fixed synthetic local clock, after the history
    }
}
