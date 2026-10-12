package com.azimulkabir.actua.data.notifications

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.sync.SyncSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** One-tap import of a captured row from its notification (#1046). */
@RunWith(AndroidJUnit4::class)
class CapturedImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = NotificationImportPreferences(context)
    private val coffee = ImportCandidate(1, 20261012, "Coffee", "Imported from com.bank.app", -500, sourceLabel = "com.bank.app")

    @Test fun queuedRowsKeepTheirIdsAcrossRewrites() = withBudget {
        val first = store.enqueue(coffee)
        val second = store.enqueue(coffee.copy(payee = "Fuel"))
        assertNotNull(first.queueId)
        store.replaceQueue(store.queued())
        assertEquals(setOf(first.queueId, second.queueId), store.queued().map { it.queueId }.toSet())
        assertEquals("Coffee", store.removeQueued(requireNotNull(first.queueId))?.payee)
        assertEquals(listOf(second.queueId), store.queued().map { it.queueId })
    }

    @Test fun importingWritesThatRowOnlyAndLeavesTheRestQueued() = withBudget { rows ->
        val tapped = store.enqueue(coffee)
        val other = store.enqueue(coffee.copy(payee = "Fuel", amountCents = -4_000))

        val generation = SyncSignals.dataGeneration.value
        val outcome = importCapturedTransaction(context, requireNotNull(tapped.queueId), "card")

        assertTrue(outcome is CapturedImportOutcome.Imported)
        assertEquals(listOf("card" to -500L), rows())
        // An open app is told to reload, so the new row and balance show without a restart.
        assertTrue(SyncSignals.dataGeneration.value > generation)
        assertEquals(listOf(other.queueId), store.queued().map { it.queueId })
        assertEquals("card", store.rememberedAccount(requireNotNull(ActiveBudgetStore(context).budgetId), "source:com.bank.app"))
        // A second tap finds nothing to import.
        assertEquals(CapturedImportOutcome.NotQueued, importCapturedTransaction(context, requireNotNull(tapped.queueId), "card"))
        assertEquals(1, rows().size)
    }

    @Test fun aMissingAccountLeavesTheRowQueued() = withBudget { rows ->
        val tapped = store.enqueue(coffee)

        val generation = SyncSignals.dataGeneration.value
        val outcome = importCapturedTransaction(context, requireNotNull(tapped.queueId), "gone")

        assertEquals(CapturedImportOutcome.Failed("That account is no longer open"), outcome)
        assertTrue(rows().isEmpty())
        assertEquals(listOf(tapped.queueId), store.queued().map { it.queueId })
        assertEquals(generation, SyncSignals.dataGeneration.value)
    }

    private fun withBudget(block: (rows: () -> List<Pair<String, Long>>) -> Unit) {
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Captured import ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        store.clearAll()
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) VALUES ('card', 'Card', 0, 0, 0, 1, 'credit')")
                db.execSQL("INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES ('to-card', '', 'card', 0)")
                db.execSQL("INSERT INTO payee_mapping (id, targetId) VALUES ('to-card', 'to-card')")
            }
            ActiveBudgetStore(context).budgetId = budget.id
            block {
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT acct, amount FROM transactions WHERE tombstone = 0 ORDER BY id", null).use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getLong(1)) }
                    }
                }
            }
        } finally {
            store.clearAll()
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
