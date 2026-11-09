package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ActuaRepositoryRecoveryTest {
    @Test
    fun repositoryFallsBackWhenSelectedBudgetIsUnreadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val valid = files.createBudget("Valid ${UUID.randomUUID()}")
        val invalid = files.createBudget("Invalid ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(invalid.id, "cloud-invalid-${UUID.randomUUID()}", "group-1")
            SQLiteDatabase.openDatabase(files.databaseFile(invalid.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                        INSERT INTO transactions
                            (id, acct, amount, date, sort_order, tombstone, cleared, pending, reconciled)
                        VALUES
                            ('bad-date', 'checking', -500, 20241340, 1, 0, 1, 0, 0)
                    """.trimIndent(),
                )
            }
            ActiveBudgetStore(context).budgetId = invalid.id

            val repository = ActuaRepository(context)
            try {
                assertTrue(repository.isUsingActualBudget)
                assertEquals(valid.id, ActiveBudgetStore(context).budgetId)
                assertFalse(repository.isServerBacked)
                assertEquals(valid.id, repository.activeMetadata?.id)
                assertTrue(
                    "An unreadable server-backed budget must be kept, not deleted",
                    files.databaseFile(invalid.id).exists(),
                )
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = null
            runCatching { files.deleteBudget(valid.id) }
            runCatching { files.deleteBudget(invalid.id) }
        }
    }

    @Test
    fun repositoryPreservesStandaloneBudgetWhenUnreadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val valid = files.createBudget("Valid Standalone ${UUID.randomUUID()}")
        val invalidStandalone = files.createBudget("Invalid Standalone ${UUID.randomUUID()}")
        try {
            assertTrue(invalidStandalone.isUserLocalOnly)
            SQLiteDatabase.openDatabase(files.databaseFile(invalidStandalone.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                        INSERT INTO transactions
                            (id, acct, amount, date, sort_order, tombstone, cleared, pending, reconciled)
                        VALUES
                            ('bad-date', 'checking', -500, 20241340, 1, 0, 1, 0, 0)
                    """.trimIndent(),
                )
            }
            ActiveBudgetStore(context).budgetId = invalidStandalone.id

            val repository = ActuaRepository(context)
            try {
                assertTrue(repository.isUsingActualBudget)
                assertEquals(valid.id, ActiveBudgetStore(context).budgetId)
                assertFalse(repository.isServerBacked)
                assertEquals(valid.id, repository.activeMetadata?.id)
                assertTrue("Unreadable standalone budget must be preserved to prevent data loss", files.budgetDirectory(invalidStandalone.id).exists())
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = null
            runCatching { files.deleteBudget(valid.id) }
            runCatching { files.deleteBudget(invalidStandalone.id) }
        }
    }

    @Test
    fun repositoryDistinguishesServerBackedFromStandaloneBudgets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val standalone = files.createBudget("Standalone ${UUID.randomUUID()}")
        val serverBacked = files.createBudget("Server ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(serverBacked.id, "cloud-${UUID.randomUUID()}", "group-1")

            ActiveBudgetStore(context).budgetId = standalone.id
            val repoStandalone = ActuaRepository(context)
            try {
                assertTrue(repoStandalone.isUsingActualBudget)
                assertFalse(repoStandalone.isServerBacked)
                assertEquals(standalone.id, repoStandalone.activeMetadata?.id)
                assertTrue(repoStandalone.activeMetadata?.isUserLocalOnly == true)
            } finally {
                repoStandalone.close()
            }

            ActiveBudgetStore(context).budgetId = serverBacked.id
            val repoServer = ActuaRepository(context)
            try {
                assertTrue(repoServer.isUsingActualBudget)
                assertTrue(repoServer.isServerBacked)
                assertEquals(serverBacked.id, repoServer.activeMetadata?.id)
                assertTrue(repoServer.activeMetadata?.isServerBacked == true)
            } finally {
                repoServer.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = null
            runCatching { files.deleteBudget(standalone.id) }
            runCatching { files.deleteBudget(serverBacked.id) }
        }
    }

    @Test
    fun repositoryKeepsUnreadableServerBudgetWithUnsyncedEditsAndBackups() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val valid = files.createBudget("Valid ${UUID.randomUUID()}")
        val invalid = files.createBudget("Server ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(invalid.id, "cloud-${UUID.randomUUID()}", "group-1")
            val backup = BackupService(context, files).makeBackup(invalid.id)
            SQLiteDatabase.openDatabase(files.databaseFile(invalid.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // An offline edit that only exists in the local message log.
                db.execSQL(
                    "INSERT INTO messages_crdt (timestamp, dataset, row, `column`, value) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any>(UNSYNCED_TIMESTAMP, "accounts", "checking", "name", "S:Checking"),
                )
                insertUnreadableTransaction(db)
            }
            ActiveBudgetStore(context).budgetId = invalid.id

            val repository = ActuaRepository(context)
            try {
                assertEquals(valid.id, repository.activeMetadata?.id)
                assertTrue(files.databaseFile(invalid.id).exists())
                assertTrue("Local backups must survive", backup.exists())
                SQLiteDatabase.openDatabase(files.databaseFile(invalid.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM messages_crdt WHERE timestamp = ?", arrayOf(UNSYNCED_TIMESTAMP)).use {
                        it.moveToFirst()
                        assertEquals("Unsynced edits must survive", 1, it.getInt(0))
                    }
                }
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = null
            runCatching { files.deleteBudget(valid.id) }
            runCatching { files.deleteBudget(invalid.id) }
        }
    }

    @Test
    fun repositoryKeepsServerBudgetThatIsLockedByAnotherConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val valid = files.createBudget("Valid ${UUID.randomUUID()}")
        val locked = files.createBudget("Locked ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(locked.id, "cloud-${UUID.randomUUID()}", "group-1")
            ActiveBudgetStore(context).budgetId = locked.id

            // Like the sync worker applying a large first sync: a write lock held past busy_timeout.
            val lockHeld = CountDownLatch(1)
            val release = CountDownLatch(1)
            val holder = thread {
                SQLiteDatabase.openDatabase(files.databaseFile(locked.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.enableWriteAheadLogging()
                    db.beginTransaction()
                    try {
                        db.execSQL("CREATE TABLE IF NOT EXISTS lock_probe (id INTEGER)")
                        lockHeld.countDown()
                        release.await(30, TimeUnit.SECONDS)
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            assertTrue(lockHeld.await(10, TimeUnit.SECONDS))
            try {
                val repository = ActuaRepository(context)
                try {
                    assertEquals(valid.id, repository.activeMetadata?.id)
                    assertTrue(
                        "A budget that is only locked must never be deleted",
                        files.databaseFile(locked.id).exists(),
                    )
                } finally {
                    repository.close()
                }
            } finally {
                release.countDown()
                holder.join()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = null
            runCatching { files.deleteBudget(valid.id) }
            runCatching { files.deleteBudget(locked.id) }
        }
    }

    private fun insertUnreadableTransaction(db: SQLiteDatabase) {
        db.execSQL(
            """
                INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')
            """.trimIndent(),
        )
        db.execSQL(
            """
                INSERT INTO transactions
                    (id, acct, amount, date, sort_order, tombstone, cleared, pending, reconciled)
                VALUES
                    ('bad-date', 'checking', -500, 20241340, 1, 0, 1, 0, 0)
            """.trimIndent(),
        )
    }

    private companion object {
        const val UNSYNCED_TIMESTAMP = "2026-11-08T10:00:00.000Z-0000-aaaaaaaaaaaaaaaa"
    }
}
