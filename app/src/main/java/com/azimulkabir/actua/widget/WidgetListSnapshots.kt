package com.azimulkabir.actua.widget

import android.content.Context
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.schedules.ScheduleWidgetEntry
import java.util.concurrent.ConcurrentHashMap

/** Prepared during an update, before notifying the launcher's asynchronous row factories. */
internal object WidgetListSnapshots {
    private data class Snapshot(val budgetId: String?, val rows: List<WidgetRow>?, val schedules: List<ScheduleWidgetEntry>?)
    private val snapshots = ConcurrentHashMap<Int, Snapshot>()

    fun save(budgetId: String?, widgetId: Int, rows: List<WidgetRow>) {
        snapshots[widgetId] = Snapshot(budgetId, rows, null)
    }

    fun saveSchedules(budgetId: String?, widgetId: Int, rows: List<ScheduleWidgetEntry>) {
        snapshots[widgetId] = Snapshot(budgetId, null, rows)
    }

    fun rows(context: Context, widgetId: Int): List<WidgetRow>? = current(context, widgetId)?.rows
    fun schedules(context: Context, widgetId: Int): List<ScheduleWidgetEntry>? = current(context, widgetId)?.schedules

    private fun current(context: Context, widgetId: Int): Snapshot? =
        snapshots[widgetId]?.takeIf { it.budgetId == ActiveBudgetStore(context).budgetId }

    fun remove(widgetId: Int) { snapshots.remove(widgetId) }
}
