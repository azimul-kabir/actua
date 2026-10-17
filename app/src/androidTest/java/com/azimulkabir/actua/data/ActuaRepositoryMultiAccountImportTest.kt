package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.ImportGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

/** Queued rows for several accounts are imported together in one batch (#1037). */
class ActuaRepositoryMultiAccountImportTest {
    @Test
    fun groupsForDifferentAccountsAreImportedTogether() = withRepository { repository, rows ->
        val imported = repository.importTransactions(listOf(
            ImportGroup("card", listOf(candidate(1, -1_250, "Coffee"), candidate(2, -4_000, "Fuel"))),
            ImportGroup("checking", listOf(candidate(3, 50_000, "Salary"))),
        ))

        assertEquals(3, imported)
        assertEquals(
            listOf("card" to -4_000L, "card" to -1_250L, "checking" to 50_000L),
            rows().map { it.account to it.amount }.sortedWith(compareBy({ it.first }, { it.second })),
        )
    }

    @Test
    fun anUnavailableAccountImportsNothing() = withRepository { repository, rows ->
        assertThrows(IllegalArgumentException::class.java) {
            repository.importTransactions(listOf(
                ImportGroup("card", listOf(candidate(1, -1_250, "Coffee"))),
                ImportGroup("missing", listOf(candidate(2, -4_000, "Fuel"))),
            ))
        }
        assertEquals(emptyList<Row>(), rows())
    }

    @Test
    fun importingWithFinancialIdPopulatesIdAndPreventsDuplicatesOnSubsequentImports() = withRepository { repository, _ ->
        val candidate = ImportCandidate(1, 20261012, "Coffee Shop", "", -450L, financialId = "FITID-12345")
        val imported = repository.importTransactions("card", listOf(candidate))
        assertEquals(1, imported)

        val matches = repository.importMatches("card", listOf(candidate))
        assertEquals(1, matches.size)
        org.junit.Assert.assertNotNull(matches[0])
        assertEquals(20261012, matches[0]?.date)
    }

    private fun candidate(row: Int, cents: Long, payee: String) = ImportCandidate(row, 20261012, payee, "", cents)

    private data class Row(val id: String, val account: String, val amount: Long)

    private fun withRepository(block: (ActuaRepository, () -> List<Row>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Multi-account import ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    """
                        INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type)
                        VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking'),
                               ('card', 'Card', 0, 0, 0, 2, 'credit')
                    """.trimIndent(),
                )
                db.execSQL("INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES ('to-checking', '', 'checking', 0), ('to-card', '', 'card', 0)")
                db.execSQL("INSERT INTO payee_mapping (id, targetId) VALUES ('to-checking', 'to-checking'), ('to-card', 'to-card')")
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
