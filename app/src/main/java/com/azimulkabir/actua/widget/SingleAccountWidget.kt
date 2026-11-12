package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository

/** The Single account widget: one chosen account and its balance. */
internal object SingleAccountWidget {
    fun preview(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_account, R.layout.widget_single_account_styled))
        val amount = sample(context, R.string.widget_sample_amount_7)
        val row = WidgetRow(sample(context, R.string.widget_sample_account_1), amount, 0, "", spokenAmount = amount)
        showSingleItem(context, views, row, open(context, WidgetActions.ACCOUNTS, 0, row.name))
        WidgetStyles.apply(context, views)
        return views
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_account, R.layout.widget_single_account_styled))
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) {
                showSingleMessage(views, context.getString(R.string.widget_no_budget), open(context, WidgetActions.BUDGET, widgetId))
            } else {
                val accounts = repository.accounts().filterNot { it.closed }
                val selected = WidgetPreferences(context).selected(WidgetKind.SingleAccount, widgetId).firstOrNull()
                when (val choice = resolveSingle(accounts, selected) { it.id }) {
                    is SingleChoice.Found -> showSingleItem(
                        context, views,
                        WidgetRow(
                            choice.item.name, money(context, choice.item.balanceCents), 0, choice.item.name,
                            spokenAmount = spoken(context, choice.item.balanceCents),
                        ),
                        open(context, WidgetActions.ACCOUNTS, widgetId, choice.item.name),
                    )
                    SingleChoice.NotFound -> showSingleMessage(
                        views, context.getString(R.string.widget_single_account_missing),
                        configure(context, WidgetConfigurationActivity::class.java, widgetId),
                    )
                    SingleChoice.Empty -> showSingleMessage(
                        views, context.getString(R.string.widget_no_accounts), open(context, WidgetActions.ACCOUNTS, widgetId),
                    )
                }
            }
            WidgetStyles.apply(context, views)
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }
}
