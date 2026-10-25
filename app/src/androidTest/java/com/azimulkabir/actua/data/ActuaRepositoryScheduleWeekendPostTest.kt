package com.azimulkabir.actua.data

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleFormFields
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.util.UUID

/** #1296: a posted occurrence takes the due date shown for the user's weekend days, never moving next_date. */
class ActuaRepositoryScheduleWeekendPostTest {
    private val fridaySaturday = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    // 2030-01-25 is a Friday: Actual keeps it, a Friday-Saturday weekend shows Thursday the 24th.
    private fun salary(accountId: String) = ScheduleFormFields(
        name = "Salary",
        accountId = accountId,
        amount = ScheduledAmount.Fixed(500_000),
        date = ScheduleDateCondition.Recurring(
            RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2030, 1, 25), skipWeekend = true, weekendSolveMode = "before"),
        ),
    )

    @Test
    fun postingUsesTheShownDateAndLeavesTheStoredNextDateAlone() = withRepository { repository ->
        val schedule = createSalary(repository)
        assertEquals(DayDate(2030, 1, 25), schedule.schedule.nextDate)
        assertEquals(DayDate(2030, 1, 24), repository.schedules(weekendDays = fridaySaturday).single().dueDate)

        assertTrue(repository.postScheduleTransaction(schedule.schedule.id, today = false, weekendDays = fridaySaturday))

        assertEquals(listOf("20300124"), repository.transactions().filter { it.scheduleId != null }.map { it.date })
        val after = repository.schedules(weekendDays = fridaySaturday).single()
        assertEquals(DayDate(2030, 1, 25), after.schedule.nextDate)
        assertEquals(com.azimulkabir.actua.data.schedules.ScheduleStatus.PAID, after.status)
    }

    @Test
    fun postingWithTheDefaultWeekendKeepsActualsDate() = withRepository { repository ->
        val schedule = createSalary(repository)

        assertTrue(repository.postScheduleTransaction(schedule.schedule.id, today = false))

        assertEquals(listOf("20300125"), repository.transactions().filter { it.scheduleId != null }.map { it.date })
    }

    private fun createSalary(repository: ActuaRepository): com.azimulkabir.actua.data.schedules.ScheduleListItem {
        assertTrue(repository.createAccount("Checking", offBudget = false, startingBalance = "0"))
        assertTrue(repository.createSchedule(salary(repository.accounts().single().id), ""))
        return repository.schedules().single()
    }

    private fun withRepository(block: (ActuaRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Schedule weekend post ${UUID.randomUUID()}")
        val previousBudget = ActiveBudgetStore(context).budgetId
        try {
            ActiveBudgetStore(context).budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                block(repository)
            } finally {
                repository.close()
            }
        } finally {
            ActiveBudgetStore(context).budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }
}
