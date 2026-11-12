package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.azimulkabir.actua.R

/** The Quick transaction widget: buttons that open the add expense, income and transfer screens. */
internal object QuickTransactionWidget {
    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, QuickVariants) { bindQuick(context, widgetId, it) },
        )
    }

    fun bindQuick(context: Context, widgetId: Int, layout: QuickLayout): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            when (layout) {
                QuickLayout.Single -> WidgetStyles.layout(context, R.layout.widget_quick_transaction_single, R.layout.widget_quick_transaction_single_styled)
                QuickLayout.Stacked -> WidgetStyles.layout(context, R.layout.widget_quick_transaction, R.layout.widget_quick_transaction_styled)
                QuickLayout.Row -> WidgetStyles.layout(context, R.layout.widget_quick_transaction_row, R.layout.widget_quick_transaction_row_styled)
            },
        )
        views.setTextViewText(R.id.widget_expense, context.getString(R.string.widget_expense))
        views.setTextViewText(R.id.widget_income, context.getString(R.string.widget_income))
        views.setTextViewText(R.id.widget_transfer, context.getString(R.string.widget_transfer))
        views.setContentDescription(R.id.widget_expense, context.getString(R.string.widget_cd_add_expense))
        views.setContentDescription(R.id.widget_income, context.getString(R.string.widget_cd_add_income))
        views.setContentDescription(R.id.widget_transfer, context.getString(R.string.widget_cd_add_transfer))
        views.setOnClickPendingIntent(R.id.widget_expense, open(context, WidgetActions.ADD_EXPENSE, widgetId))
        views.setOnClickPendingIntent(R.id.widget_income, open(context, WidgetActions.ADD_INCOME, widgetId))
        views.setOnClickPendingIntent(R.id.widget_transfer, open(context, WidgetActions.ADD_TRANSFER, widgetId))
        WidgetStyles.apply(context, views)
        return views
    }
}
