package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.azimulkabir.actua.data.schedules.SchedulePrefill

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class ScheduleNavigationState(
    val editingScheduleId: MutableState<String?>,
    val scheduleReturnsToBills: MutableState<Boolean>,
    val scheduleReturnsToTransactions: MutableState<Boolean>,
    val scheduleReturnsToTransactionsTab: MutableState<Boolean>,
    val billsCalendarReturnsToSchedules: MutableState<Boolean>,
    val scheduleRuleId: MutableState<String?>,
) {
    fun returnFromRule(onDetailChange: (DetailDestination) -> Unit) {
        scheduleRuleId.value = null
        onDetailChange(DetailDestination.EditSchedule)
    }

    fun returnFromEditor(
        destination: MainDestination,
        onDestinationChange: (MainDestination) -> Unit,
        onDetailChange: (DetailDestination) -> Unit,
    ) {
        when {
            scheduleReturnsToTransactionsTab.value -> {
                onDestinationChange(MainDestination.Transactions)
                onDetailChange(DetailDestination.Main)
            }
            scheduleReturnsToTransactions.value -> onDetailChange(DetailDestination.Transactions)
            scheduleReturnsToBills.value -> onDetailChange(
                if (destination == MainDestination.Bills) DetailDestination.Main else DetailDestination.BillsCalendar,
            )
            else -> onDetailChange(DetailDestination.Schedules)
        }
        scheduleReturnsToBills.value = false
        scheduleReturnsToTransactions.value = false
        scheduleReturnsToTransactionsTab.value = false
    }
}

@Composable
internal fun rememberScheduleNavigationState(): ScheduleNavigationState =
    ScheduleNavigationState(
        editingScheduleId = rememberSaveable { mutableStateOf<String?>(null) },
        scheduleReturnsToBills = rememberSaveable { mutableStateOf(false) },
        scheduleReturnsToTransactions = rememberSaveable { mutableStateOf(false) },
        scheduleReturnsToTransactionsTab = rememberSaveable { mutableStateOf(false) },
        billsCalendarReturnsToSchedules = rememberSaveable { mutableStateOf(false) },
        scheduleRuleId = rememberSaveable { mutableStateOf<String?>(null) },
    )

/** Keeps the transaction-derived schedule draft across rotation and process death. */
internal val SchedulePrefillSaver = androidx.compose.runtime.saveable.listSaver<SchedulePrefill?, Any>(
    save = { prefill ->
        if (prefill == null) emptyList()
        else listOf(prefill.payeeName, prefill.accountName, prefill.amountCents, prefill.startDate.yyyymmdd)
    },
    restore = { saved ->
        if (saved.size != 4) null
        else com.azimulkabir.actua.data.schedules.DayDate.fromYyyymmdd(saved[3] as Int)?.let {
            SchedulePrefill(saved[0] as String, saved[1] as String, saved[2] as Long, it)
        }
    },
)
