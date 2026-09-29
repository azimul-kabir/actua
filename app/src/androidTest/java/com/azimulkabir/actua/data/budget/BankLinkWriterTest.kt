package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Bank links must only sync columns Actual has; Actual rejects others with `invalid-schema`. */
class BankLinkWriterTest {
    @Test fun everyWritableFieldExistsInActualsSchema() = withBudget { _, file ->
        val schema = columnsByTable(file)
        ActualEntityWriter.allowedFields.forEach { (dataset, fields) ->
            val columns = schema[dataset] ?: error("Actual has no $dataset table")
            assertEquals("$dataset fields missing from Actual's schema", emptySet<String>(), fields - columns)
        }
    }

    @Test fun goCardlessLinkUsesActualsBanksRowAndOnlyUpstreamColumns() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val first = writer.createAccount("Checking", offBudget = false, startingBalanceCents = 0)
        val second = writer.createAccount("Savings", offBudget = false, startingBalanceCents = 0)

        writer.linkBankAccount(first, "gc-account-1", "goCardless", "requisition-1")
        writer.linkBankAccount(second, "gc-account-2", "goCardless", "requisition-1")

        val synced = syncedCells(file)
        val schema = columnsByTable(file)
        synced.forEach { (dataset, column) ->
            assertTrue("$dataset.$column is not in Actual's schema", column in schema[dataset].orEmpty())
        }
        assertFalse(synced.any { it.second == "gocardless_requisition_id" })

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT id, bank_id FROM banks", null).use { cursor ->
                assertEquals("the same requisition reuses one banks row", 1, cursor.count)
                cursor.moveToFirst()
                assertEquals("requisition-1", cursor.getString(1))
                val bankId = cursor.getString(0)
                raw.rawQuery("SELECT bank FROM accounts WHERE id IN (?, ?)", arrayOf(first, second)).use { accounts ->
                    while (accounts.moveToNext()) assertEquals(bankId, accounts.getString(0))
                }
            }
        }
        val linked = database.fetchBankSyncAccounts().associateBy { it.id }
        assertEquals("requisition-1", linked.getValue(first).requisitionId)
        assertEquals("gc-account-1", linked.getValue(first).externalId)
        assertEquals("requisition-1", linked.getValue(second).requisitionId)

        writer.unlinkBankAccount(first)
        assertFalse(database.fetchBankSyncAccounts().any { it.id == first })
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT bank, account_id FROM accounts WHERE id = ?", arrayOf(first)).use {
                it.moveToFirst(); assertNull(it.getString(0)); assertNull(it.getString(1))
            }
        }
    }

    @Test fun otherProvidersDoNotCreateBanksRows() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val account = writer.createAccount("Card", offBudget = false, startingBalanceCents = 0)
        writer.linkBankAccount(account, "sf-1", "simpleFin")
        assertTrue(syncedCells(file).none { it.first == "banks" })
        assertNull(database.fetchBankSyncAccounts().single().requisitionId)
    }

    @Test fun legacyLocalRequisitionColumnIsStillReadButNeverCreated() {
        val file = blankBudget()
        try {
            ActualBudgetDatabase.open(file).close()
            assertFalse("gocardless_requisition_id" in columnsByTable(file).getValue("accounts"))

            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.execSQL("ALTER TABLE accounts ADD COLUMN gocardless_requisition_id TEXT DEFAULT NULL")
                raw.execSQL(
                    "INSERT INTO accounts (id, name, account_id, account_sync_source, gocardless_requisition_id) " +
                        "VALUES ('legacy', 'Legacy', 'gc-legacy', 'goCardless', 'requisition-legacy')",
                )
            }
            ActualBudgetDatabase.open(file).use { database ->
                assertEquals("requisition-legacy", database.fetchBankSyncAccounts().single().requisitionId)
            }
        } finally {
            file.delete()
        }
    }

    private fun withBudget(block: (ActualBudgetDatabase, File) -> Unit) {
        val file = blankBudget()
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun blankBudget(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "bank-link-${UUID.randomUUID()}.sqlite").also(BlankBudgetFactory::create)
    }

    private fun syncedCells(file: File): Set<Pair<String, String>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT DISTINCT dataset, `column` FROM messages_crdt", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
            }
        }

    private fun columnsByTable(file: File): Map<String, Set<String>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            val tables = raw.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            tables.associateWith { table ->
                raw.rawQuery("PRAGMA table_info(\"$table\")", null).use { cursor ->
                    val name = cursor.getColumnIndexOrThrow("name")
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
                }
            }
        }
}
