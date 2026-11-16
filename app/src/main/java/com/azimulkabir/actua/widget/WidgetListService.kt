package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Supplies the scrolling list of the Favourite categories and Account balances widgets. */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = WidgetListRows(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        runCatching { WidgetKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrDefault(WidgetKind.Categories),
    )

    companion object {
        const val EXTRA_KIND = "widget_kind"
    }
}

internal class WidgetListRows(
    private val context: Context,
    private val widgetId: Int,
    private val kind: WidgetKind,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<WidgetRow> = emptyList()
    private var style = WidgetRowStyle.Highlighted

    override fun onCreate() = Unit

    // Called on a binder thread, so reading the budget here doesn't block the main thread.
    override fun onDataSetChanged() {
        style = WidgetPreferences(context).rowStyle(kind, widgetId)
        rows = WidgetListSnapshots.rows(context, widgetId) ?: when (kind) {
            WidgetKind.Categories -> FavouriteCategoriesWidget.categoryRows(context)
            WidgetKind.Accounts -> AccountBalancesWidget.accountRows(context, widgetId)
            // The single-item widgets have no list.
            WidgetKind.SingleCategory, WidgetKind.SingleAccount, WidgetKind.ReportCard -> emptyList()
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? =
        rows.getOrNull(position)?.let { listRow(context, it, style) }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = WidgetRowStyle.entries.size

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
