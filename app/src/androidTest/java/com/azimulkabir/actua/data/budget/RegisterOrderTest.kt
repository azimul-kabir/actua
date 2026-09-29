package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Actual's register (`v_transactions`) orders `date DESC, starting_balance_flag, sort_order DESC, id`,
 * so the opening balance sits below every other row from its day and its running balance counts first.
 */
class RegisterOrderTest {
    @Test fun startingBalanceSortsBelowSameDayRowsWhateverTheirSortOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "register-order-${UUID.randomUUID()}.sqlite").also(BlankBudgetFactory::create)
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    "INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) " +
                        "VALUES ('card', 'Card', 0, 0, 0, 1, 'credit')",
                )
                listOf(
                    arrayOf<Any>("opening", -20_000, 20260110, 10.0, 1),
                    arrayOf<Any>("card-buy", -1_500, 20260110, 1.0, 0),
                    arrayOf<Any>("card-buy2", -2_500, 20260111, 31.0, 0),
                ).forEach { row ->
                    db.execSQL(
                        "INSERT INTO transactions (id, acct, amount, date, sort_order, starting_balance_flag, " +
                            "tombstone, isParent, isChild, cleared, reconciled) VALUES (?, 'card', ?, ?, ?, ?, 0, 0, 0, 1, 0)",
                        row,
                    )
                }
            }
            ActualBudgetDatabase.open(file).use { database ->
                val register = database.fetchTransactions(accountId = "card")
                assertEquals(listOf("card-buy2", "card-buy", "opening"), register.map { it.id })
                assertEquals(true, register.last().startingBalance)

                // The running balance folds from the oldest row up, as Actual's `$sumOver` does.
                var balance = 0L
                val running = register.asReversed().associate { balance += it.amountCents; it.id to balance }
                assertEquals(mapOf("opening" to -20_000L, "card-buy" to -21_500L, "card-buy2" to -24_000L), running)
            }
        } finally {
            file.delete()
        }
    }
}
