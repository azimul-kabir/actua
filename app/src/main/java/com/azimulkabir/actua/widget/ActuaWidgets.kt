package com.azimulkabir.actua.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.preferences.BudgetDisplayFormats
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.data.preferences.withAppLanguage
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleWidgetEntry
import com.azimulkabir.actua.data.schedules.ScheduleWidgetPeriod
import com.azimulkabir.actua.data.schedules.ScheduleWidgetProjection
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.applyDisplayFormats
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

object WidgetActions {
    const val BUDGET = "com.azimulkabir.actua.widget.BUDGET"
    const val ACCOUNTS = "com.azimulkabir.actua.widget.ACCOUNTS"
    const val CATEGORY = "com.azimulkabir.actua.widget.CATEGORY"
    const val ADD_EXPENSE = "com.azimulkabir.actua.widget.ADD_EXPENSE"
    const val ADD_INCOME = "com.azimulkabir.actua.widget.ADD_INCOME"
    const val ADD_TRANSFER = "com.azimulkabir.actua.widget.ADD_TRANSFER"
    const val SEARCH = "com.azimulkabir.actua.widget.SEARCH"
    const val SCHEDULES = "com.azimulkabir.actua.widget.SCHEDULES"
    const val EXTRA_TARGET = "widget_target"
}

enum class WidgetKind { Categories, Accounts }

internal class WidgetPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun selected(kind: WidgetKind, widgetId: Int): Set<String> =
        values.getStringSet(key(kind, widgetId), emptySet()).orEmpty()

    fun save(kind: WidgetKind, widgetId: Int, ids: Set<String>) {
        values.edit().putStringSet(key(kind, widgetId), ids).apply()
    }

    fun remove(kind: WidgetKind, widgetIds: IntArray) {
        values.edit().apply {
            widgetIds.forEach { remove(key(kind, it)) }
        }.apply()
    }

    private fun key(kind: WidgetKind, widgetId: Int) = "${kind.name.lowercase()}_$widgetId"

    private companion object { const val NAME = "home_widget_preferences" }
}

internal class ScheduleWidgetPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun periodDays(widgetId: Int): Int = values.getInt(key(widgetId), ScheduleWidgetPeriod.FOURTEEN.days)

    fun save(widgetId: Int, days: Int) {
        values.edit().putInt(key(widgetId), days).apply()
    }

    fun remove(widgetIds: IntArray) {
        values.edit().apply {
            widgetIds.forEach { remove(key(it)) }
        }.apply()
    }

    private fun key(widgetId: Int) = "period_$widgetId"

    private companion object { const val NAME = "schedule_widget_preferences" }
}

abstract class ActuaWidgetProvider : AppWidgetProvider() {
    abstract val kind: WidgetKind?

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        scheduleUpdate(context, manager, widgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        scheduleUpdate(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        kind?.let { WidgetPreferences(context).remove(it, widgetIds) }
    }

    private fun scheduleUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                // Widget providers get no AppCompat locale handling, so resolve strings through
                // a configuration context for the app locale chosen in Settings.
                val localizedContext = context.withAppLanguage()
                widgetIds.forEach { WidgetUpdater.update(localizedContext, manager, it, this) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}

class BudgetSnapshotWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null
}

class FavouriteCategoriesWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.Categories
}

class QuickTransactionWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null
}

class AccountBalancesWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.Accounts
}

class ScheduledTransactionsWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        super.onDeleted(context, widgetIds)
        ScheduleWidgetPreferences(context).remove(widgetIds)
    }
}

object WidgetUpdater {
    private val providers = listOf(
        BudgetSnapshotWidgetProvider::class.java,
        FavouriteCategoriesWidgetProvider::class.java,
        QuickTransactionWidgetProvider::class.java,
        AccountBalancesWidgetProvider::class.java,
        ScheduledTransactionsWidgetProvider::class.java,
    )

    fun requestAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        providers.forEach { provider ->
            val component = ComponentName(app, provider)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                app.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    this.component = component
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }
    }

    internal fun update(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        provider: ActuaWidgetProvider,
    ) {
        when (provider) {
            is BudgetSnapshotWidgetProvider -> updateBudget(context, manager, widgetId)
            is FavouriteCategoriesWidgetProvider -> updateCategories(context, manager, widgetId)
            is QuickTransactionWidgetProvider -> updateQuickTransaction(context, manager, widgetId)
            is AccountBalancesWidgetProvider -> updateAccounts(context, manager, widgetId)
            is ScheduledTransactionsWidgetProvider -> updateSchedules(context, manager, widgetId)
            else -> Unit
        }
    }

    /**
     * The widget for the launcher, with one layout per entry of [variants]. On Android 12+ every size is
     * sent and the launcher picks the best fit itself, and lays out again on a resize without an update.
     * Before that the layout for the widget's current size is chosen here, with the same breakpoints.
     */
    private fun <T> responsive(
        manager: AppWidgetManager,
        widgetId: Int,
        variants: List<WidgetVariant<T>>,
        build: (T) -> RemoteViews,
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = LinkedHashMap<SizeF, RemoteViews>()
            variants.forEach { sizes[SizeF(it.minWidthDp.toFloat(), it.minHeightDp.toFloat())] = build(it.layout) }
            return RemoteViews(sizes)
        }
        val options = manager.getAppWidgetOptions(widgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Int.MAX_VALUE)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Int.MAX_VALUE)
        return build(WidgetSizing.pick(variants, width, height).layout)
    }

    private class BudgetData(val month: String, val ready: String, val budgeted: String, val balance: String)

    private fun loadBudget(context: Context): BudgetData {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) {
                return BudgetData(context.getString(R.string.widget_no_budget), "—", "—", "—")
            }
            val overview = repository.budgetOverview()
            val month = YearMonth.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", context.resources.configuration.locales[0]))
            return BudgetData(
                month,
                overview.toBudgetCents?.let { money(context, it) } ?: "—",
                money(context, overview.budgetedCents),
                money(context, overview.availableCents),
            )
        } finally {
            repository.close()
        }
    }

    private fun updateBudget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val data = loadBudget(context)
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, BudgetVariants) { bindBudget(context, widgetId, it, data) },
        )
    }

    private fun bindBudget(context: Context, widgetId: Int, layout: BudgetLayout, data: BudgetData): RemoteViews {
        val layoutId = when (layout) {
            BudgetLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_mini, R.layout.widget_budget_snapshot_mini_styled)
            BudgetLayout.Small -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_small, R.layout.widget_budget_snapshot_small_styled)
            BudgetLayout.Strip -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_strip, R.layout.widget_budget_snapshot_strip_styled)
            BudgetLayout.Compact -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_compact, R.layout.widget_budget_snapshot_compact_styled)
            BudgetLayout.Hero -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot, R.layout.widget_budget_snapshot_styled)
        }
        val views = RemoteViews(context.packageName, layoutId)
        // RemoteViews inflate in the launcher's locale, so rebind every catalog label through
        // the app-language context (needed on API 28-32; API 33+ handles it in the framework).
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_budget_snapshot))
        views.setTextViewText(R.id.widget_ready_label, context.getString(R.string.widget_ready))
        views.setTextViewText(R.id.widget_budgeted_label, context.getString(R.string.widget_budgeted))
        views.setTextViewText(R.id.widget_balance_label, context.getString(R.string.widget_balance))
        views.setTextViewText(R.id.widget_month, data.month)
        views.setViewVisibility(R.id.widget_month, View.VISIBLE)
        views.setTextViewText(R.id.widget_ready_value, data.ready)
        views.setTextViewText(R.id.widget_budgeted_value, data.budgeted)
        views.setTextViewText(R.id.widget_balance_value, data.balance)
        views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.BUDGET, widgetId))
        WidgetStyles.apply(context, views)
        return views
    }

    private fun updateCategories(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, ListVariants) { layout ->
                val views = RemoteViews(
                    context.packageName,
                    when (layout) {
                        ListLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_favourite_categories_mini, R.layout.widget_favourite_categories_mini_styled)
                        ListLayout.Titled -> WidgetStyles.layout(context, R.layout.widget_favourite_categories, R.layout.widget_favourite_categories_styled)
                    },
                )
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_favourite_categories))
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_categories))
                bindItemList(context, views, widgetId, WidgetKind.Categories, WidgetActions.CATEGORY)
                views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.BUDGET, widgetId))
                WidgetStyles.apply(context, views)
                views
            },
        )
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_item_list)
    }

    private fun updateQuickTransaction(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, QuickVariants) { layout ->
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
                views.setOnClickPendingIntent(R.id.widget_expense, open(context, WidgetActions.ADD_EXPENSE, widgetId))
                views.setOnClickPendingIntent(R.id.widget_income, open(context, WidgetActions.ADD_INCOME, widgetId))
                views.setOnClickPendingIntent(R.id.widget_transfer, open(context, WidgetActions.ADD_TRANSFER, widgetId))
                WidgetStyles.apply(context, views)
                views
            },
        )
    }

    private fun updateAccounts(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, ListVariants) { layout ->
                val views = RemoteViews(
                    context.packageName,
                    when (layout) {
                        ListLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_account_balances_mini, R.layout.widget_account_balances_mini_styled)
                        ListLayout.Titled -> WidgetStyles.layout(context, R.layout.widget_account_balances, R.layout.widget_account_balances_styled)
                    },
                )
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_account_balances))
                views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_accounts))
                bindItemList(context, views, widgetId, WidgetKind.Accounts, WidgetActions.ACCOUNTS)
                views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.ACCOUNTS, widgetId))
                WidgetStyles.apply(context, views)
                views
            },
        )
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_item_list)
    }

    // The service-backed adapter is deprecated on newer releases, but its replacement
    // (RemoteCollectionItems) needs API 31 and minSdk is 28.
    @Suppress("DEPRECATION")
    private fun bindItemList(context: Context, views: RemoteViews, widgetId: Int, kind: WidgetKind, action: String) {
        // A scrolling list shows every row (up to MAX_WIDGET_ROWS) however much room the widget has.
        val rows = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetListService.EXTRA_KIND, kind.name)
            // Makes the intent unique per widget, so each widget gets its own list.
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_item_list, rows)
        views.setEmptyView(R.id.widget_item_list, R.id.widget_empty)
        // Rows fill in the item they open, which needs a mutable template. The intent is explicit, so
        // a fill-in cannot redirect it; a negative request code keeps it apart from the widget's own.
        views.setPendingIntentTemplate(R.id.widget_item_list, openTemplate(context, action, -widgetId))
    }

    // The service-backed adapter is deprecated on newer releases, but its replacement
    // (RemoteCollectionItems) needs API 31 and minSdk is 28.
    @Suppress("DEPRECATION")
    private fun updateSchedules(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val usingBudget = try { repository.isUsingActualBudget } finally { repository.close() }
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, ListVariants) { layout ->
                val views = RemoteViews(
                    context.packageName,
                    when (layout) {
                        ListLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_scheduled_transactions_mini, R.layout.widget_scheduled_transactions_mini_styled)
                        ListLayout.Titled -> WidgetStyles.layout(context, R.layout.widget_scheduled_transactions, R.layout.widget_scheduled_transactions_styled)
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

    /** One row of the Favourite categories or Account balances list. [target] is what a tap opens. */
    internal data class WidgetRow(
        val name: String,
        val amount: String,
        val progress: Int,
        val target: String,
        val status: ProgressStatus = ProgressStatus.Normal,
        val showProgress: Boolean = false,
    )

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
            return all.filter { it.id in favorites }.take(MAX_WIDGET_ROWS).map { row ->
                val spent = row.spentCents.coerceAtLeast(0)
                val denominator = row.assignedCents.coerceAtLeast(1)
                val percent = ((spent * 100 / denominator).coerceIn(0, 100)).toInt()
                WidgetRow(
                    row.name, money(context, row.balanceCents), percent, row.name,
                    ProgressStatus.of(percent, overspent = row.balanceCents < 0), showProgress = true,
                )
            }
        } finally {
            repository.close()
        }
    }

    internal fun accountRows(context: Context, widgetId: Int): List<WidgetRow> {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            val all = repository.accounts().filterNot { it.closed }
            val selected = WidgetPreferences(context).selected(WidgetKind.Accounts, widgetId)
            val rows = if (selected.isEmpty()) all else all.filter { it.id in selected }
            return rows.take(MAX_WIDGET_ROWS).map { WidgetRow(it.name, money(context, it.balanceCents), 0, it.name) }
        } finally {
            repository.close()
        }
    }

    internal fun listRow(context: Context, row: WidgetRow): RemoteViews {
        // Rows bind from the RemoteViewsService's application context, which does not follow
        // the app locale on API 28-32; resolve strings through the app-language context.
        val localized = context.withAppLanguage()
        return RemoteViews(localized.packageName, R.layout.widget_list_item).apply {
            setTextViewText(R.id.widget_item_name, row.name)
            setTextViewText(R.id.widget_item_amount, row.amount)
            setViewVisibility(R.id.widget_item_progress, if (row.showProgress) View.VISIBLE else View.GONE)
            if (row.showProgress) {
                setProgressBar(R.id.widget_item_progress, 100, row.progress, false)
                WidgetStyles.applyProgressStatus(localized, this, R.id.widget_item_progress, row.status)
            }
            setOnClickFillInIntent(R.id.widget_item_row, Intent().putExtra(WidgetActions.EXTRA_TARGET, row.target))
            WidgetStyles.apply(localized, this)
        }
    }

    private fun open(context: Context, action: String, requestCode: Int, target: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            target?.let { putExtra(WidgetActions.EXTRA_TARGET, it) }
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openTemplate(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    // The open budget's synced formats, read whenever a widget opens the budget; schedule rows are
    // bound later, after their repository is closed.
    @Volatile private var budgetFormats = BudgetDisplayFormats()

    private fun loadBudgetFormats(repository: ActuaRepository) {
        CurrencyDisplay.decimalPlaces = repository.budgetDecimalPlaces()
        budgetFormats = repository.budgetDisplayFormats()
    }

    private fun money(context: Context, cents: Long): String {
        val formats = DisplayPreferences(context).resolve(budgetFormats)
        applyDisplayFormats(formats)
        return formatMoneyCents(cents, formats.hideDecimals,
            respectBalanceVisibility = false).let { if (formats.hideBalances) "••••" else it }
    }
}
