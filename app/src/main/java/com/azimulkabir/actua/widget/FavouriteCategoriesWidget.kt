package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import java.util.Date

/** The Favourite categories widget: a scrolling list of the favourite categories and their progress. */
internal object FavouriteCategoriesWidget {
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    fun preview(context: Context): RemoteViews {
        fun row(name: Int, amount: Int, percent: Int) = WidgetRow(
            sample(context, name), sample(context, amount), percent, "",
            ProgressStatus.of(percent, overspent = false), showProgress = true, spokenAmount = sample(context, amount),
        )
        return collectionPreview(
            context, R.layout.widget_favourite_categories, R.layout.widget_favourite_categories_styled,
            R.string.widget_favourite_categories, R.id.widget_item_list,
            listOf(
                row(R.string.widget_sample_category_1, R.string.widget_sample_amount_1, 62),
                row(R.string.widget_sample_category_2, R.string.widget_sample_amount_2, 91),
                row(R.string.widget_sample_category_3, R.string.widget_sample_amount_3, 35),
            ).map { listRow(context, it) },
        )
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val usingBudget = try { repository.isUsingActualBudget } finally { repository.close() }
        val budgetId = ActiveBudgetStore(context).budgetId ?: "no-budget"
        val noFavorites = usingBudget && FavoritePreferences(context).ids(budgetId, FavoritePreferences.Type.CATEGORY).isEmpty()
        val updatedAt = DateFormat.getTimeFormat(context).format(Date())
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, ListVariants) { layout ->
                val views = RemoteViews(
                    context.packageName,
                    when (layout) {
                        ListLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_favourite_categories_mini, R.layout.widget_favourite_categories_mini_styled)
                        ListLayout.Titled, ListLayout.Full -> WidgetStyles.layout(context, R.layout.widget_favourite_categories, R.layout.widget_favourite_categories_styled)
                    },
                )
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_favourite_categories))
                // Three distinct empty states, each with a tap that leads somewhere: no budget, no favorites, none left.
                views.setTextViewText(
                    R.id.widget_empty,
                    context.getString(when {
                        !usingBudget -> R.string.widget_no_budget
                        noFavorites -> R.string.widget_no_favorites
                        else -> R.string.widget_no_categories
                    }),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_empty,
                    if (noFavorites) configure(context, WidgetConfigurationActivity::class.java, widgetId)
                    else open(context, WidgetActions.BUDGET, widgetId),
                )
                bindFreshness(context, manager, views, widgetId, layout == ListLayout.Full, updatedAt)
                bindItemList(context, views, widgetId, WidgetKind.Categories, WidgetActions.CATEGORY)
                views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.BUDGET, widgetId))
                WidgetStyles.apply(context, views)
                views
            },
        )
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_item_list)
    }

    internal fun categoryRows(context: Context): List<WidgetRow> {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            val all = repository.budgetGroups().asSequence()
                .filterNot { it.hidden || it.isIncome }
                .flatMap { it.categories.asSequence() }
                .filterNot { it.hidden }
                .toList()
            val budgetId = ActiveBudgetStore(context).budgetId ?: "no-budget"
            val favorites = FavoritePreferences(context).ids(budgetId, FavoritePreferences.Type.CATEGORY)
            return all.filter { it.id in favorites }.take(MAX_WIDGET_ROWS).map { categoryRowOf(context, it) }
        } finally {
            repository.close()
        }
    }
}
