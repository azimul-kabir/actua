package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.*
import org.junit.Test

class ScheduleNavigationStateTest {
    private fun state() = ScheduleNavigationState(
        mutableStateOf(null), mutableStateOf(false), mutableStateOf(false),
        mutableStateOf(false), mutableStateOf(false), mutableStateOf(null),
    )

    @Test fun editor_returns_to_each_origin_and_consumes_flags() {
        for (origin in listOf("tab", "register", "billsTab", "calendar", "schedules")) {
            val state = state()
            state.scheduleReturnsToTransactionsTab.value = origin == "tab"
            state.scheduleReturnsToTransactions.value = origin == "register"
            state.scheduleReturnsToBills.value = origin in setOf("billsTab", "calendar")
            var destination = if (origin == "billsTab") MainDestination.Bills else MainDestination.Manage
            var detail = DetailDestination.EditSchedule
            state.returnFromEditor(destination, { destination = it }, { detail = it })
            assertEquals(when (origin) {
                "tab", "billsTab" -> DetailDestination.Main
                "register" -> DetailDestination.Transactions
                "calendar" -> DetailDestination.BillsCalendar
                else -> DetailDestination.Schedules
            }, detail)
            if (origin == "tab") assertEquals(MainDestination.Transactions, destination)
            assertFalse(state.scheduleReturnsToTransactionsTab.value)
            assertFalse(state.scheduleReturnsToTransactions.value)
            assertFalse(state.scheduleReturnsToBills.value)
        }
    }

    @Test fun rule_return_clears_rule_before_reopening_schedule() {
        val state = state()
        state.scheduleRuleId.value = "synthetic-rule"
        state.editingScheduleId.value = "synthetic-schedule"
        state.returnFromRule {
            assertNull(state.scheduleRuleId.value)
            assertEquals("synthetic-schedule", state.editingScheduleId.value)
            assertEquals(DetailDestination.EditSchedule, it)
        }
    }
}
