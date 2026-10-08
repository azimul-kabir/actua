package com.azimulkabir.actua.data

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleFormFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** #938: like Actual's `updateSchedule` (`checkIfScheduleExists`), an edit can't take another schedule's name. */
class ActuaRepositoryScheduleNameTest {
    private fun fields(name: String) =
        ScheduleFormFields(name = name, date = ScheduleDateCondition.Fixed(DayDate(2030, 1, 15)))

    @Test
    fun updatingAScheduleToAnotherSchedulesNameIsRejected() = withRepository { repository ->
        assertTrue(repository.createSchedule(fields("Rent"), ""))
        assertTrue(repository.createSchedule(fields("Gym"), ""))
        val gym = repository.schedules().single { it.schedule.name == "Gym" }

        val error = assertThrows(IllegalArgumentException::class.java) {
            repository.updateSchedule(gym.schedule.id, fields(" Rent "), "")
        }
        assertEquals("A schedule named Rent already exists.", error.message)
        assertEquals(listOf("Gym", "Rent"), repository.schedules().mapNotNull { it.schedule.name }.sorted())
    }

    @Test
    fun updatingAScheduleKeepingItsOwnNameIsAllowed() = withRepository { repository ->
        assertTrue(repository.createSchedule(fields("Rent"), ""))
        val rent = repository.schedules().single()

        assertTrue(repository.updateSchedule(rent.schedule.id, fields("Rent").copy(customUpcomingLength = "2-week"), ""))
        assertTrue(repository.updateSchedule(rent.schedule.id, fields("Housing"), ""))
        assertEquals("Housing", repository.schedules().single().schedule.name)
    }

    private fun withRepository(block: (ActuaRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Schedule names ${UUID.randomUUID()}")
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
