package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.preferences.HomePreferences
import com.azimulkabir.actua.data.preferences.withAppLanguage
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleWidgetEntry
import com.azimulkabir.actua.data.schedules.ScheduleWidgetProjection
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import java.util.Date

/** The Scheduled transactions widget: the upcoming schedules in a chosen period. */
internal object ScheduledTransactionsWidget {
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    fun preview(context: Context): RemoteViews {
        fun row(title: Int, due: Int, amount: Int) = RemoteViews(context.packageName, R.layout.widget_schedule_item).apply {
            setTextViewText(R.id.widget_schedule_title, sample(context, title))
            setTextViewText(R.id.widget_schedule_due, sample(context, due))
            setTextViewText(R.id.widget_schedule_amount, sample(context, amount))
            WidgetStyles.apply(context, this)
        }
        return collectionPreview(
            context, R.layout.widget_scheduled_transactions, R.layout.widget_scheduled_transactions_styled,
            R.string.widget_upcoming_schedules, R.id.widget_schedule_list,
            listOf(
                row(R.string.widget_sample_schedule_1, R.string.widget_due_tomorrow, R.string.widget_sample_amount_10),
                row(R.string.widget_sample_schedule_2, R.string.widget_due_today, R.string.widget_sample_amount_11),
                row(R.string.widget_sample_schedule_3, R.string.widget_due_tomorrow, R.string.widget_sample_amount_12),
            ),
        )
    }

    // The service-backed adapter is deprecated on newer releases, but its replacement
    // (RemoteCollectionItems) needs API 31 and minSdk is 28.
    @Suppress("DEPRECATION")
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
                        ListLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_scheduled_transactions_mini, R.layout.widget_scheduled_transactions_mini_styled)
                        ListLayout.Titled, ListLayout.Full -> WidgetStyles.layout(context, R.layout.widget_scheduled_transactions, R.layout.widget_scheduled_transactions_styled)
                    },
                )
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_upcoming_schedules))
                views.setTextViewText(
                    R.id.widget_empty,
                    context.getString(if (usingBudget) R.string.widget_no_schedules else R.string.widget_no_budget),
                )
                // A scrolling list shows every schedule in the chosen period, however many there are.
                val rows = Intent(context, ScheduleWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    // Makes the intent unique per widget, so each widget gets its own list.
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setOnClickPendingIntent(R.id.widget_empty, open(context, WidgetActions.SCHEDULES, widgetId))
                bindFreshness(context, manager, views, widgetId, layout == ListLayout.Full, updatedAt)
                views.setRemoteAdapter(R.id.widget_schedule_list, rows)
                views.setEmptyView(R.id.widget_schedule_list, R.id.widget_empty)
                // Every row opens Scheduled; rows fill in an empty intent, so the template can stay immutable.
                views.setPendingIntentTemplate(R.id.widget_schedule_list, open(context, WidgetActions.SCHEDULES, widgetId * 10 + 1))
                views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.SCHEDULES, widgetId))
                WidgetStyles.apply(context, views)
                views
            },
        )
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_schedule_list)
    }

    internal fun scheduleEntries(context: Context, widgetId: Int): List<ScheduleWidgetEntry> {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) return emptyList()
            val today = DayDate.today()
            val periodDays = ScheduleWidgetPreferences(context).periodDays(widgetId)
            return ScheduleWidgetProjection.upcoming(
                repository.schedules(
                    today,
                    com.azimulkabir.actua.data.preferences.HomePreferences(context).monthStartRule(
                        com.azimulkabir.actua.data.budget.ActiveBudgetStore(context).budgetId ?: "no-budget",
                    ).nonWorkingDays,
                ),
                today, periodDays,
            )
        } finally {
            repository.close()
        }
    }

    internal fun scheduleRow(context: Context, entry: ScheduleWidgetEntry): RemoteViews {
        // Rows bind from the RemoteViewsService's application context, which does not follow
        // the app locale on API 28-32; resolve strings through the app-language context.
        val localized = context.withAppLanguage()
        return RemoteViews(localized.packageName, R.layout.widget_schedule_item).apply {
            setTextViewText(R.id.widget_schedule_title, entry.item.title)
            setTextViewText(R.id.widget_schedule_due, relativeDueLabel(localized, entry.dueDate))
            setTextViewText(R.id.widget_schedule_amount, scheduleAmount(localized, entry.item.schedule))
            val due = relativeDueLabel(localized, entry.dueDate)
            val amount = if (balancesHidden(localized)) localized.getString(R.string.widget_cd_hidden)
                else scheduleAmount(localized, entry.item.schedule)
            setContentDescription(
                R.id.widget_schedule_row,
                localized.getString(R.string.widget_cd_schedule_row, entry.item.title, due, amount),
            )
            setOnClickFillInIntent(R.id.widget_schedule_row, Intent())
            WidgetStyles.apply(localized, this)
        }
    }

    /** "Today", "Tomorrow", "In 5 days", "3 days overdue", in the app-selected language. */
    internal fun relativeDueLabel(
        context: Context,
        dueDate: DayDate,
        today: DayDate = DayDate.today(),
    ): String {
        val days = today.daysUntil(dueDate)
        return when {
            days == 0 -> context.getString(R.string.widget_due_today)
            days == 1 -> context.getString(R.string.widget_due_tomorrow)
            days > 1 -> context.resources.getQuantityString(R.plurals.widget_due_in_days, days, days)
            else -> context.resources.getQuantityString(R.plurals.widget_days_overdue, -days, -days)
        }
    }

    private fun scheduleAmount(context: Context, schedule: ActualScheduleSummary): String {
        val amount = schedule.amount
        return if (amount is ScheduledAmount.Range) {
            val low = minOf(amount.first, amount.second)
            val high = maxOf(amount.first, amount.second)
            "${money(context, low)} – ${money(context, high)}"
        } else {
            (if (schedule.amountOp == ScheduleAmountOp.APPROXIMATE) "~ " else "") + money(context, schedule.postAmount)
        }
    }
}
