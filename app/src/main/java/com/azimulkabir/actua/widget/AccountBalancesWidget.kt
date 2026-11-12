package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import java.util.Date

/** The Account balances widget: a scrolling list of account balances. */
internal object AccountBalancesWidget {
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    fun preview(context: Context): RemoteViews {
        fun row(name: Int, amount: Int) = WidgetRow(sample(context, name), sample(context, amount), 0, "", spokenAmount = sample(context, amount))
        return collectionPreview(
            context, R.layout.widget_account_balances, R.layout.widget_account_balances_styled,
            R.string.widget_account_balances, R.id.widget_item_list,
            listOf(
                row(R.string.widget_sample_account_1, R.string.widget_sample_amount_7),
                row(R.string.widget_sample_account_2, R.string.widget_sample_amount_8),
                row(R.string.widget_sample_account_3, R.string.widget_sample_amount_9),
            ).map { listRow(context, it) },
        )
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val usingBudget = try { repository.isUsingActualBudget } finally { repository.close() }
        val updatedAt = DateFormat.getTimeFormat(context).format(Date())
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, ListVariants) { layout ->
                val views = RemoteViews(
                    context.packageName,
                    when (layout) {
                        ListLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_account_balances_mini, R.layout.widget_account_balances_mini_styled)
                        ListLayout.Titled, ListLayout.Full -> WidgetStyles.layout(context, R.layout.widget_account_balances, R.layout.widget_account_balances_styled)
                    },
                )
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_account_balances))
                views.setTextViewText(
                    R.id.widget_empty,
                    context.getString(if (usingBudget) R.string.widget_no_accounts else R.string.widget_no_budget),
                )
                views.setOnClickPendingIntent(R.id.widget_empty, open(context, WidgetActions.ACCOUNTS, widgetId))
                bindFreshness(context, manager, views, widgetId, layout == ListLayout.Full, updatedAt)
                bindItemList(context, views, widgetId, WidgetKind.Accounts, WidgetActions.ACCOUNTS)
                views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.ACCOUNTS, widgetId))
                WidgetStyles.apply(context, views)
                views
            },
        )
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_item_list)
    }

    internal fun accountRows(context: Context, widgetId: Int): List<WidgetRow> {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            val all = repository.accounts().filterNot { it.closed }
            val selected = WidgetPreferences(context).selected(WidgetKind.Accounts, widgetId)
            val rows = if (selected.isEmpty()) all else all.filter { it.id in selected }
            return rows.take(MAX_WIDGET_ROWS).map {
                WidgetRow(it.name, money(context, it.balanceCents), 0, it.name, spokenAmount = spoken(context, it.balanceCents))
            }
        } finally {
            repository.close()
        }
    }
}
