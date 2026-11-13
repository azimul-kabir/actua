package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.model.categoryKey

/** The Single category widget: one chosen category with its available amount and progress. */
internal object SingleCategoryWidget {
    fun preview(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_category, R.layout.widget_single_category_styled))
        val amount = sample(context, R.string.widget_sample_amount_1)
        val row = WidgetRow(
            sample(context, R.string.widget_sample_category_1), amount, 62, "",
            ProgressStatus.Normal, showProgress = true, spokenAmount = amount,
        )
        val status = showSingleItem(context, views, row, open(context, WidgetActions.CATEGORY, 0, row.name))
        WidgetStyles.apply(context, views, status)
        return views
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_category, R.layout.widget_single_category_styled))
        val repository = ActuaRepository(context)
        var progressStatus = ProgressStatus.Normal
        try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) {
                showSingleMessage(views, context.getString(R.string.widget_no_budget), open(context, WidgetActions.BUDGET, widgetId))
            } else {
                val budgetId = ActiveBudgetStore(context).budgetId ?: "no-budget"
                val favorites = FavoritePreferences(context).ids(budgetId, FavoritePreferences.Type.CATEGORY)
                // Skipping setup shows the first favorite, or the first category when there are none.
                val categories = repository.budgetGroups().asSequence()
                    .filterNot { it.hidden || it.isIncome }
                    .flatMap { it.categories.asSequence() }
                    .filterNot { it.hidden || it.id == null }
                    .sortedByDescending { it.id in favorites }
                    .toList()
                val selected = WidgetPreferences(context).selected(WidgetKind.SingleCategory, widgetId).firstOrNull()
                when (val choice = resolveSingle(categories, selected) { it.id.orEmpty() }) {
                    is SingleChoice.Found -> progressStatus = showSingleItem(
                        context, views, categoryRowOf(context, choice.item),
                        open(context, WidgetActions.CATEGORY, widgetId, choice.item.categoryKey),
                    )
                    SingleChoice.NotFound -> showSingleMessage(
                        views, context.getString(R.string.widget_single_category_missing),
                        configure(context, WidgetConfigurationActivity::class.java, widgetId),
                    )
                    SingleChoice.Empty -> showSingleMessage(
                        views, context.getString(R.string.widget_no_categories), open(context, WidgetActions.BUDGET, widgetId),
                    )
                }
            }
            WidgetStyles.apply(context, views, progressStatus)
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }
}
