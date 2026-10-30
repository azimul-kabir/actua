package com.azimulkabir.actua.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.schedules.ActualScheduleWriter
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.OneTimeScheduleProblem
import com.azimulkabir.actua.data.schedules.OneTimeScheduleSource
import com.azimulkabir.actua.data.schedules.ScheduleWriteBuilder
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.Transaction
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Actual's mobile "Schedule" action on a future transaction (`onSchedule` at 59fe126f), actua#1349. */
class ActuaRepositoryOneTimeScheduleTest {
    private val today = DayDate.today()

    private fun future(days: Int) = today.addingDays(days)

    private fun transaction(
        date: DayDate, cents: Long = -120_000, payee: String = "Landlord", notes: String = "monthly rent",
        splits: List<SplitLine> = emptyList(),
    ) = Transaction(
        id = "", date = date.iso, payee = payee, category = if (splits.isEmpty()) "General" else "", account = "Checking",
        amount = (cents / 100).toInt(), cleared = false, amountCents = cents, notes = notes, splits = splits,
    )

    @Test
    fun futureTransactionBecomesAOneTimeScheduleAndIsRemoved() = withRepository { repository, rows, _ ->
        val date = future(3)
        repository.saveTransaction(transaction(date))
        val id = rows().single().id
        assertNull(repository.oneTimeScheduleProblem(id))

        val scheduleId = repository.convertTransactionToOneTimeSchedule(id)

        assertEquals("the original row is removed", emptyList<String>(), rows().map { it.id })
        val schedule = repository.schedules().single().schedule
        assertEquals(scheduleId, schedule.id)
        assertTrue(schedule.postsTransaction)
        assertFalse(schedule.completed)
        assertFalse(schedule.isRecurring)
        assertEquals(date, schedule.nextDate)
        assertEquals(-120_000L, schedule.postAmount)
        assertTrue(schedule.name!!.startsWith("Auto-created future transaction ("))

        val conditions = JSONArray(schedule.conditionsJson)
        val fields = (0 until conditions.length()).map { conditions.getJSONObject(it).getString("field") }
        assertEquals(listOf("date", "amount", "payee", "account"), fields)
        (0 until conditions.length()).forEach { assertEquals("is", conditions.getJSONObject(it).getString("op")) }
        assertEquals(date.iso, conditions.getJSONObject(0).getString("value"))
        assertEquals(-120_000L, conditions.getJSONObject(1).getLong("value"))

        val actions = JSONArray(schedule.actionsJson)
        assertEquals("link-schedule", actions.getJSONObject(0).getString("op"))
        assertEquals(scheduleId, actions.getJSONObject(0).getString("value"))
        val sets = (1 until actions.length()).map { actions.getJSONObject(it) }
        assertEquals(listOf("category", "notes"), sets.map { it.getString("field") })
        assertEquals("monthly rent", sets.last().getString("value"))
    }

    @Test
    fun splitTransactionKeepsItsLinesInTheRulesActions() = withRepository { repository, rows, _ ->
        repository.saveTransaction(
            transaction(
                future(2), cents = -1_000, notes = "shared",
                splits = listOf(
                    SplitLine(category = "General", amountCents = -600, notes = "first", payee = "Landlord"),
                    SplitLine(category = "General", amountCents = -400, notes = "", payee = "Landlord"),
                ),
            ),
        )
        val parent = rows().single { it.isParent }

        repository.convertTransactionToOneTimeSchedule(parent.id)

        assertEquals("the parent and its lines are removed", emptyList<String>(), rows().map { it.id })
        val actions = JSONArray(repository.schedules().single().schedule.actionsJson)
        val set = (1 until actions.length()).map { actions.getJSONObject(it) }
        val splitAmounts = set.filter { it.getString("op") == "set-split-amount" }
        assertEquals(listOf(-600L, -400L), splitAmounts.map { it.getLong("value") })
        assertEquals(listOf(1, 2), splitAmounts.map { it.getJSONObject("options").getInt("splitIndex") })
        assertTrue(splitAmounts.all { it.getJSONObject("options").getString("method") == "fixed-amount" })
        val notes = set.filter { it.optString("field") == "notes" }
        assertEquals(listOf("shared", "first"), notes.map { it.getString("value") })
        assertEquals(listOf(0, 1), notes.map { it.getJSONObject("options").getInt("splitIndex") })
    }

    @Test
    fun pastTransactionIsRefusedAndNothingChanges() = withRepository { repository, rows, messageCount ->
        repository.saveTransaction(transaction(today.addingDays(-2)))
        val id = rows().single().id
        val before = messageCount()

        assertEquals(OneTimeScheduleProblem.NOT_FUTURE, repository.oneTimeScheduleProblem(id))
        assertThrows(IllegalArgumentException::class.java) { repository.convertTransactionToOneTimeSchedule(id) }

        assertEquals(listOf(id), rows().map { it.id })
        assertEquals(before, messageCount())
        assertTrue(repository.schedules().isEmpty())
    }

    @Test
    fun convertingTwiceIsRefusedBecauseTheRowIsGone() = withRepository { repository, rows, _ ->
        repository.saveTransaction(transaction(future(3)))
        val id = rows().single().id
        repository.convertTransactionToOneTimeSchedule(id)

        assertThrows(IllegalArgumentException::class.java) { repository.convertTransactionToOneTimeSchedule(id) }
        assertEquals(1, repository.schedules().size)
    }

    @Test
    fun windowFlagsADateBeyondTheUpcomingLength() = withRepository { repository, rows, _ ->
        repository.saveTransaction(transaction(future(3)))
        repository.saveTransaction(transaction(future(40), payee = "Other"))
        val soon = rows().first { it.date == future(3).yyyymmdd }.id
        val later = rows().first { it.date == future(40).yyyymmdd }.id

        // The default window is 7 days.
        assertFalse(repository.oneTimeScheduleWindow(soon)!!.beyondWindow)
        val window = repository.oneTimeScheduleWindow(later)!!
        assertTrue(window.beyondWindow)
        assertEquals(40, window.daysUntil)
        assertEquals(7, window.upcomingDays)
    }

    @Test
    fun scheduleAndRemovalCommitTogetherOrNotAtAll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("One time atomic ${UUID.randomUUID()}")
        try {
            ActualBudgetDatabase.open(files.databaseFile(budget.id)).use { database ->
                val plan = ScheduleWriteBuilder.createOneTime(
                    OneTimeScheduleSource(future(5), -500, null, "checking", null, null),
                    scheduleId = "s1", ruleId = "r1", nextDateRowId = "n1", now = 1_000L,
                )
                val before = database.getMessagesSince(HlcTimestamp.ZERO.toString()).size

                assertThrows(IllegalStateException::class.java) {
                    database.atomically {
                        ActualScheduleWriter(database).apply(plan)
                        error("the second write failed")
                    }
                }

                assertTrue(database.fetchSchedules(postableOnly = false).isEmpty())
                assertEquals(before, database.getMessagesSince(HlcTimestamp.ZERO.toString()).size)
            }
        } finally {
            runCatching { files.deleteBudget(budget.id) }
        }
    }

    private data class Row(val id: String, val date: Int, val isParent: Boolean)

    private fun withRepository(block: (ActuaRepository, () -> List<Row>, () -> Int) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("One time schedule ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        val path = files.databaseFile(budget.id).path
        try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    "INSERT INTO accounts (id, name, offbudget, closed, tombstone, sort_order, type) " +
                        "VALUES ('checking', 'Checking', 0, 0, 0, 1, 'checking')",
                )
            }
            ActiveBudgetStore(context).budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                block(
                    repository,
                    {
                        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                            db.rawQuery(
                                "SELECT id, date, isParent FROM transactions WHERE tombstone = 0 ORDER BY date, id", null,
                            ).use { cursor ->
                                buildList {
                                    while (cursor.moveToNext()) add(Row(cursor.getString(0), cursor.getInt(1), cursor.getInt(2) == 1))
                                }
                            }
                        }
                    },
                    {
                        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                            db.rawQuery("SELECT COUNT(*) FROM messages_crdt", null).use { it.moveToFirst(); it.getInt(0) }
                        }
                    },
                )
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
