package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.asDuplicate
import com.azimulkabir.actua.model.asTransferDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Bulk actions save list rows; an incoming transfer leg must keep the transfer's direction (#757). */
class ActuaRepositoryIncomingTransferTest {
    @Test
    fun duplicatingAndEditingTheIncomingLegKeepsTheTransferDirection() = withRepository { repository, rows ->
        repository.saveTransaction(Transaction(
            id = "", date = "2026-09-10", payee = "", category = "", account = "Checking",
            amount = -25, cleared = false, amountCents = -2_500, type = Type.TRANSFER, transferAccount = "Savings",
        ))
        val incomingId = rows().single { it.account == "savings" }.id
        val incoming = repository.reportTransactions(listOf(incomingId)).single()
        assertEquals("Savings", incoming.account)
        assertEquals(2_500L, incoming.amountCents)

        // Label/Categorize/Move: an edit of the incoming row.
        repository.saveTransaction(incoming.copy(notes = "#moved").asTransferDraft())
        assertEquals(listOf("checking" to -2_500L, "savings" to 2_500L), rows().map { it.account to it.amount })
        assertEquals("savings", rows().single { it.id == incomingId }.account)

        // Duplicate: a new Checking → Savings transfer, not Savings → Checking.
        repository.saveTransaction(incoming.asDuplicate().asTransferDraft())
        val balances = rows().groupBy { it.account }.mapValues { (_, legs) -> legs.sumOf { it.amount } }
        assertEquals(mapOf("checking" to -5_000L, "savings" to 5_000L), balances)
    }

    private data class Row(val id: String, val account: String, val amount: Long)

    private fun withRepository(block: (ActuaRepository, () -> List<Row>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Incoming transfer ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking'),
                               ('savings', 'Savings', 0, 0, 0, 2, 'savings')
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES ('to-checking', '', 'checking', 0), ('to-savings', '', 'savings', 0)")
                db.execSQL("INSERT INTO payee_mapping (id, targetId) VALUES ('to-checking', 'to-checking'), ('to-savings', 'to-savings')")
            }
            ActiveBudgetStore(context).budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                block(repository) {
                    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery(
                            "SELECT id, acct, amount FROM transactions WHERE tombstone = 0 ORDER BY acct, date, id", null,
                        ).use { cursor ->
                            buildList { while (cursor.moveToNext()) add(Row(cursor.getString(0), cursor.getString(1), cursor.getLong(2))) }
                        }
                    }
                }
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
