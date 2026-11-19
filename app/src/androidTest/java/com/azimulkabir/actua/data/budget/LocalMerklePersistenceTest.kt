package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.MerkleNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** #1600: the incremental baseline must never outlive the committed log it represents. */
class LocalMerklePersistenceTest {
    @Test fun localBatchesPersistTheWholeTreeAndDeduplicateAcceptedRows() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { database ->
            val messages = List(180) { message(it) }.shuffled(Random(1600))
            messages.chunked(13).forEach { batch ->
                val inserted = database.localWrite { database.applyLocalMessages(batch + batch) }
                assertEquals(batch.size, inserted.size)
                assertCurrentTree(database)
                val before = database.loadClock()
                assertTrue(database.localWrite { database.applyLocalMessages(batch) }.isEmpty())
                assertEquals(before, database.loadClock())
            }
            assertEquals(messages.size, database.messageCountAfter(HlcTimestamp.ZERO.toString()))
        }
    }

    @Test fun coldOpenRepairsMissingMalformedAndIncorrectStoredTrees() {
        for (clock in listOf<String?>(null, "not-json", "{\"timestamp\":\"2024-01-01T00:00:00.000Z-0000-0000000000000001\",\"merkle\":{\"hash\":123}}")) {
            withBudget { file ->
                ActualBudgetDatabase.open(file).use { it.insertMessages(listOf(message(0))) }
                raw(file) { if (clock != null) it.execSQL("INSERT OR REPLACE INTO messages_clock (id, clock) VALUES (1, ?)", arrayOf(clock)) }
                ActualBudgetDatabase.open(file).use { database ->
                    write(database, message(1))
                    assertEquals(2, database.messageCountAfter(HlcTimestamp.ZERO.toString()))
                    assertCurrentTree(database)
                }
            }
        }
    }

    @Test fun failedClockPersistenceRollsBackTheRowAndMessage() = withBudget { file ->
        raw(file) {
            it.execSQL("CREATE TRIGGER fail_clock BEFORE INSERT ON messages_clock BEGIN SELECT RAISE(ABORT, 'synthetic clock failure'); END")
        }
        ActualBudgetDatabase.open(file).use { database ->
            assertThrows(SQLiteException::class.java) { write(database, message(0)) }
            assertTrue(database.fetchAccounts().isEmpty())
            assertNull(database.maxMessageTimestamp())
            assertNull(database.loadClock())
            raw(file) { it.execSQL("DROP TRIGGER fail_clock") }
            write(database, message(1))
            assertEquals(1, database.messageCountAfter(HlcTimestamp.ZERO.toString()))
            assertCurrentTree(database)
        }
    }

    @Test fun outerRollbackDiscardsAWarmIncrementalTree() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { database ->
            write(database, message(0))
            val before = database.loadClock()
            assertThrows(IllegalStateException::class.java) {
                database.atomically {
                    write(database, message(1))
                    error("synthetic interruption before commit")
                }
            }
            assertEquals(before, database.loadClock())
            assertEquals(1, database.messageCountAfter(HlcTimestamp.ZERO.toString()))
            write(database, message(2))
            assertCurrentTree(database)
        }
    }

    @Test fun caughtNestedFailureStillInvalidatesATreeRebuiltBeforeParentRollback() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { database ->
            write(database, message(0))
            val before = database.loadClock()
            database.atomically {
                runCatching {
                    database.atomically {
                        write(database, message(1))
                        error("caught nested failure")
                    }
                }
                write(database, message(2))
            }
            // SQLite rolls back the outer transaction even when the caller catches its child's failure.
            assertEquals(before, database.loadClock())
            assertEquals(1, database.messageCountAfter(HlcTimestamp.ZERO.toString()))
            write(database, message(3))
            assertCurrentTree(database)
        }
    }

    @Test fun remoteAndSnapshotInsertsInvalidateTheWarmBaseline() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { database ->
            write(database, message(100))
            database.receiveMessages(listOf(message(0), message(0)))
            write(database, message(101))
            assertCurrentTree(database)
            database.insertMessages(listOf(message(1)))
            write(database, message(102))
            assertEquals(5, database.messageCountAfter(HlcTimestamp.ZERO.toString()))
            assertCurrentTree(database)
        }
    }

    @Test fun anotherConnectionIncludingRawSqlInvalidatesTheWarmBaseline() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { first ->
            ActualBudgetDatabase.open(file).use { second ->
                write(first, message(0))
                write(second, message(1))
                write(first, message(2))
                assertCurrentTree(first)
                raw(file) { sql ->
                    val m = message(3)
                    sql.execSQL("INSERT INTO messages_crdt (timestamp, dataset, row, `column`, value) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(m.timestamp.toString(), m.dataset, m.row, m.column, m.value))
                }
                write(second, message(4))
                write(first, message(5))
                assertEquals(6, first.messageCountAfter(HlcTimestamp.ZERO.toString()))
                assertCurrentTree(first)
            }
        }
    }

    @Test fun concurrentConnectionsCommitCompleteTreesWithoutLosingMessages() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { first ->
            ActualBudgetDatabase.open(file).use { second ->
                write(first, message(0))
                val pool = Executors.newFixedThreadPool(2)
                val start = CountDownLatch(1)
                try {
                    val jobs = listOf(first, second).mapIndexed { node, database ->
                        pool.submit {
                            check(start.await(10, TimeUnit.SECONDS))
                            repeat(20) { write(database, message(1 + it * 2 + node)) }
                        }
                    }
                    start.countDown()
                    jobs.forEach { it.get(30, TimeUnit.SECONDS) }
                } finally {
                    pool.shutdownNow()
                }
                assertEquals(41, first.messageCountAfter(HlcTimestamp.ZERO.toString()))
                assertCurrentTree(first)
            }
        }
    }

    @Test fun syncBoundarySaveIncludesLocalMessagesCommittedAfterItsSnapshot() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { database ->
            val synced = message(0)
            database.insertMessages(listOf(synced))
            write(database, message(1))
            database.saveClockFromMessageLog(synced.timestamp.toString())
            assertCurrentTree(database)
            assertEquals(synced.timestamp.toString(), database.loadClock()?.timestamp)
            assertEquals(listOf(message(1)), database.getMessagesSince(synced.timestamp.toString()))
            // A stale persisted clock tree cannot poison the connection's log-derived baseline.
            database.saveClock(ActualBudgetDatabase.ClockRecord(synced.timestamp.toString(), MerkleNode(123)))
            write(database, message(2))
            assertCurrentTree(database)
        }
    }

    @Test fun separateBudgetsKeepIndependentBaselines() = withBudget { firstFile ->
        withBudget { secondFile ->
            ActualBudgetDatabase.open(firstFile).use { first ->
                ActualBudgetDatabase.open(secondFile).use { second ->
                    write(first, message(0))
                    write(second, message(1))
                    write(first, message(2))
                    assertEquals(2, first.messageCountAfter(HlcTimestamp.ZERO.toString()))
                    assertEquals(1, second.messageCountAfter(HlcTimestamp.ZERO.toString()))
                    assertCurrentTree(first)
                    assertCurrentTree(second)
                }
            }
        }
    }

    @Test fun reopeningAfterACommittedLocalWritePreservesRowLogAndClock() = withBudget { file ->
        ActualBudgetDatabase.open(file).use { write(it, message(0)) }
        ActualBudgetDatabase.open(file).use { database ->
            assertEquals("Synthetic 0", database.fetchAccounts().single().name)
            assertNotNull(database.loadClock())
            assertCurrentTree(database)
            write(database, message(1))
            assertCurrentTree(database)
        }
    }

    private fun write(database: ActualBudgetDatabase, message: CrdtMessage) =
        database.localWrite { database.applyLocalMessages(listOf(message)) }

    private fun assertCurrentTree(database: ActualBudgetDatabase) {
        assertEquals(database.deriveMerkleFromMessageLog().root, requireNotNull(database.loadClock()).merkle)
    }

    private fun message(index: Int) = CrdtMessage(
        HlcTimestamp(1_704_067_200_000L + index * 60_000, 0, "0000000000000001"),
        "accounts", "synthetic", "name", "S:Synthetic $index",
    )

    private fun raw(file: File, block: (SQLiteDatabase) -> Unit) =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use(block)

    private fun withBudget(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "local-merkle-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val file = File(directory, "db.sqlite")
        try {
            BlankBudgetFactory.create(file)
            block(file)
        } finally {
            BudgetOpenProbe.invalidate(file)
            directory.deleteRecursively()
        }
    }
}
