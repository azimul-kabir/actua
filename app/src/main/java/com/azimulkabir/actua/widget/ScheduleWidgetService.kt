package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.azimulkabir.actua.data.schedules.ScheduleWidgetEntry

/** Supplies the Upcoming Schedules widget's scrolling list. */
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = ScheduleWidgetRows(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
    )
}

internal class ScheduleWidgetRows(
    private val context: Context,
    private val widgetId: Int,
    private val load: (Context, Int) -> List<ScheduleWidgetEntry> = WidgetUpdater::scheduleEntries,
) : RemoteViewsService.RemoteViewsFactory {
    private var entries: List<ScheduleWidgetEntry> = emptyList()

    override fun onCreate() = Unit

    // Called on a binder thread, so reading the budget here doesn't block the main thread.
    override fun onDataSetChanged() {
        entries = load(context, widgetId)
    }

    override fun onDestroy() {
        entries = emptyList()
    }

    override fun getCount(): Int = entries.size

    override fun getViewAt(position: Int): RemoteViews? =
        entries.getOrNull(position)?.let { WidgetUpdater.scheduleRow(context, it) }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
