package com.azimulkabir.actua.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.preferences.HomePreferences
import com.azimulkabir.actua.data.reports.ReportCards

/** The Report card widget: one chosen Reports summary card as a headline value. */
internal object ReportCardWidget {
    fun preview(context: Context): RemoteViews = bindReportCard(
        context, 0, ReportCardLayout.Small,
        ReportCardState.Card(
            sample(context, R.string.widget_sample_report_card),
            ReportCardText(sample(context, R.string.widget_sample_amount_8)),
        ),
    )

    /** What the Report card widget shows: a card's name and headline, or a message and where a tap on it leads. */
    private sealed interface ReportCardState {
        class Card(val name: String, val text: ReportCardText) : ReportCardState
        class Message(val text: String, val tap: PendingIntent) : ReportCardState
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val state = try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) {
                ReportCardState.Message(context.getString(R.string.widget_no_budget), open(context, WidgetActions.BUDGET, widgetId))
            } else {
                val missing = ReportCardState.Message(
                    context.getString(R.string.widget_report_card_missing),
                    configure(context, WidgetConfigurationActivity::class.java, widgetId),
                )
                val selected = WidgetPreferences(context).selected(WidgetKind.ReportCard, widgetId).firstOrNull()
                when (val choice = resolveSingle(repository.reportCardChoices(), selected) { it.id }) {
                    is SingleChoice.Found -> {
                        // Summary cards follow the month start day chosen on this device, as they do in Reports.
                        repository.reportMonthStart = HomePreferences(context)
                            .monthStartRule(ActiveBudgetStore(context).budgetId ?: "no-budget")
                        val headline = repository.reportCard(choice.item.id)?.let(ReportCards::headline)
                        if (headline == null) missing else ReportCardState.Card(
                            choice.item.name,
                            reportCardText(
                                headline, reportCardLabels(context), context.resources.configuration.locales[0],
                            ) { money(context, it) },
                        )
                    }
                    SingleChoice.NotFound -> missing
                    SingleChoice.Empty -> ReportCardState.Message(
                        context.getString(R.string.widget_no_report_cards), open(context, WidgetActions.REPORTS, widgetId),
                    )
                }
            }
        } finally {
            repository.close()
        }
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, ReportCardVariants) { bindReportCard(context, widgetId, it, state) },
        )
    }

    private fun reportCardLabels(context: Context) = object : ReportCardLabels {
        override fun days(days: Long) = context.resources.getQuantityString(
            R.plurals.widget_report_card_days, days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), days,
        )
        override fun years(years: String) = context.getString(R.string.widget_report_card_years, years)
        override val notReached get() = context.getString(R.string.widget_report_card_not_reached)
        override val unavailable get() = context.getString(R.string.widget_report_card_unavailable)
    }

    /** The Report card widget: the card's name over its value, and in the taller layout what it is compared with. */
    private fun bindReportCard(context: Context, widgetId: Int, layout: ReportCardLayout, state: ReportCardState): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            when (layout) {
                ReportCardLayout.Small -> WidgetStyles.layout(context, R.layout.widget_report_card_small, R.layout.widget_report_card_small_styled)
                ReportCardLayout.Tall -> WidgetStyles.layout(context, R.layout.widget_report_card, R.layout.widget_report_card_styled)
            },
        )
        when (state) {
            is ReportCardState.Message -> {
                showSingleMessage(views, state.text, state.tap)
                views.setViewVisibility(R.id.widget_report_comparison, View.GONE)
            }
            is ReportCardState.Card -> {
                val text = state.text
                views.setViewVisibility(R.id.widget_single_message, View.GONE)
                views.setViewVisibility(R.id.widget_single_name, View.VISIBLE)
                views.setViewVisibility(R.id.widget_single_amount, View.VISIBLE)
                views.setViewVisibility(R.id.widget_report_comparison, if (text.comparison != null) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_single_name, state.name)
                views.setTextViewText(R.id.widget_single_amount, text.value)
                text.comparison?.let {
                    views.setTextViewText(R.id.widget_report_comparison, context.getString(R.string.widget_report_card_comparison, it))
                }
                // One sentence for the whole widget. Money is not read out when balances are hidden.
                val hidden = text.isMoney && balancesHidden(context)
                val value = if (hidden) context.getString(R.string.widget_cd_hidden) else text.value
                views.setContentDescription(
                    R.id.widget_root,
                    if (text.comparison == null || hidden) context.getString(R.string.widget_cd_report_card, state.name, value)
                    else context.getString(R.string.widget_cd_report_card_compared, state.name, value, text.comparison),
                )
                views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.REPORTS, widgetId))
            }
        }
        WidgetStyles.apply(context, views)
        return views
    }
}
