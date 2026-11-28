package com.azimulkabir.actua.data.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.sync.SyncSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** One-tap import of a captured row from its notification (#1046). */
@RunWith(AndroidJUnit4::class)
class CapturedImportTest {
    @get:Rule val notificationPermission = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
    )

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

    @Test fun rejectingARowRemovesOnlyThatRowFromTheQueue() {
        store.clearAll()
        val rejected = store.enqueue(coffee)
        val kept = store.enqueue(coffee.copy(payee = "Fuel"))

        CapturedTransactionNotifications.reject(context, store, requireNotNull(rejected.queueId))
        assertEquals(listOf(kept.queueId), store.queued().map { it.queueId })

        CapturedTransactionNotifications.reject(context, store, requireNotNull(kept.queueId))
        assertTrue(store.queued().isEmpty())
        store.clearAll()
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

    @Test fun importingFollowsTheRememberedMarkClearedChoice() = withBudget { _ ->
        val budgetId = requireNotNull(ActiveBudgetStore(context).budgetId)
        fun clearedFlags(): List<Int> = SQLiteDatabase.openDatabase(
            BudgetFileManager(context).databaseFile(budgetId).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery("SELECT cleared FROM transactions WHERE tombstone = 0 ORDER BY date, id", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
            }
        }

        // Turned off on the review screen (#1529): a notification import stays uncleared.
        store.setMarkCleared(budgetId, false)
        val off = store.enqueue(coffee)
        assertTrue(importCapturedTransaction(context, requireNotNull(off.queueId), "card") is CapturedImportOutcome.Imported)
        assertEquals(listOf(0), clearedFlags())

        // Back on, as it is by default: imports are cleared again.
        store.setMarkCleared(budgetId, true)
        val on = store.enqueue(coffee.copy(date = 20261013, payee = "Fuel", amountCents = -4_000))
        assertTrue(importCapturedTransaction(context, requireNotNull(on.queueId), "card") is CapturedImportOutcome.Imported)
        assertEquals(listOf(0, 1), clearedFlags())
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

    @Test fun notificationsOpenReviewUntilTheImportSucceeds() = withBudget { rows ->
        val manager = context.getSystemService(NotificationManager::class.java)
        val queueId = requireNotNull(store.enqueue(coffee).queueId)
        val failedQueueId = requireNotNull(store.enqueue(coffee.copy(payee = "Fuel")).queueId)
        val notificationsWereEnabled = store.confirmNotificationsEnabled
        try {
            store.confirmNotificationsEnabled = true
            runBlocking {
                TestListenableWorkerBuilder<CapturedTransactionNotifyWorker>(context)
                    .setInputData(workDataOf(CapturedTransactionNotifications.EXTRA_QUEUE_ID to queueId))
                    .build().doWork()
                assertNotificationDestination(manager, queueId, CapturedTransactionNotifications.reviewIntent(context, queueId))

                runImportWorker(queueId, "card")
                assertNotificationDestination(manager, queueId, CapturedTransactionNotifications.transactionsIntent(context, queueId))
                assertEquals(listOf("card" to -500L), rows())

                runImportWorker(failedQueueId, "missing")
                assertNotificationDestination(
                    manager, failedQueueId, CapturedTransactionNotifications.reviewIntent(context, failedQueueId),
                )
                assertEquals(listOf(failedQueueId), store.queued().map { it.queueId })
            }
        } finally {
            store.confirmNotificationsEnabled = notificationsWereEnabled
            CapturedTransactionNotifications.cancel(context, listOf(queueId, failedQueueId))
        }
    }

    private suspend fun runImportWorker(queueId: String, accountId: String) {
        TestListenableWorkerBuilder<CapturedImportWorker>(context)
            .setInputData(workDataOf(
                CapturedTransactionNotifications.EXTRA_QUEUE_ID to queueId,
                CapturedTransactionNotifications.EXTRA_ACCOUNT_ID to accountId,
            ))
            .build().doWork()
    }

    private suspend fun assertNotificationDestination(
        manager: NotificationManager,
        queueId: String,
        expected: PendingIntent,
    ) = withTimeout(5_000) {
        val id = CapturedTransactionNotifications.notificationId(queueId)
        while (manager.activeNotifications.firstOrNull { it.id == id }?.notification?.contentIntent != expected) {
            delay(50)
        }
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
