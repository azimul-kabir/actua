package com.azimulkabir.actua.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.SizeF
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import android.widget.RemoteViews
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.preferences.BudgetDisplayFormats
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.data.preferences.HomePreferences
import com.azimulkabir.actua.data.preferences.withAppLanguage
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.reports.IncomeSpending
import com.azimulkabir.actua.data.reports.IncomeSpendingMonth
import com.azimulkabir.actua.data.reports.ReportCards
import com.azimulkabir.actua.data.reports.SpendingTrend
import com.azimulkabir.actua.data.reports.SpendingTrendMonth
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleWidgetEntry
import com.azimulkabir.actua.data.schedules.ScheduleWidgetPeriod
import com.azimulkabir.actua.data.schedules.ScheduleWidgetProjection
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.applyDisplayFormats
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.util.Date
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
    const val REPORTS = "com.azimulkabir.actua.widget.REPORTS"
    const val REFRESH = "com.azimulkabir.actua.widget.REFRESH"
    const val EXTRA_TARGET = "widget_target"
}

enum class WidgetKind { Categories, Accounts, SingleCategory, SingleAccount, ReportCard }

/** Whether the widget shows one chosen item, so its setup screen is a single-choice list. */
internal val WidgetKind.isSingle: Boolean
    get() = this == WidgetKind.SingleCategory || this == WidgetKind.SingleAccount || this == WidgetKind.ReportCard

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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetActions.REFRESH) {
            super.onReceive(context, intent)
            return
        }
        // The refresh target: re-read the local budget for this widget. No sync is started here.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        scheduleUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId)) {
            val localized = context.withAppLanguage()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(localized, R.string.widget_refreshed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
        onDone: () -> Unit = {},
    ) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                // Widget providers get no AppCompat locale handling, so resolve strings through
                // a configuration context for the app locale chosen in Settings.
                val localizedContext = context.withAppLanguage()
                widgetIds.forEach { WidgetUpdater.update(localizedContext, manager, it, this) }
                onDone()
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

class SpendingTrendWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null
}

class IncomeSpendingWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null
}

class SingleCategoryWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.SingleCategory
}

class SingleAccountWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.SingleAccount
}

class ReportCardWidgetProvider : ActuaWidgetProvider() {
    override val kind = WidgetKind.ReportCard
}

class ScheduledTransactionsWidgetProvider : ActuaWidgetProvider() {
    override val kind: WidgetKind? = null

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        super.onDeleted(context, widgetIds)
        ScheduleWidgetPreferences(context).remove(widgetIds)
    }
}

/** A bitmap handed to RemoteViews counts against the launcher's memory budget, so charts are kept small. */
private const val MAX_CHART_PX = 1400

object WidgetUpdater {
    private val providers = listOf(
        BudgetSnapshotWidgetProvider::class.java,
        FavouriteCategoriesWidgetProvider::class.java,
        QuickTransactionWidgetProvider::class.java,
        AccountBalancesWidgetProvider::class.java,
        ScheduledTransactionsWidgetProvider::class.java,
        SingleCategoryWidgetProvider::class.java,
        SingleAccountWidgetProvider::class.java,
        SpendingTrendWidgetProvider::class.java,
        IncomeSpendingWidgetProvider::class.java,
        ReportCardWidgetProvider::class.java,
    )

    private val previewExecutor = Executors.newSingleThreadExecutor()

    /**
     * Android 15+: pushes a generated preview of each widget to the launcher's picker, so it shows the
     * real layout in the chosen style rather than an empty one. The data is synthetic sample text, never
     * from a budget. Earlier releases show the static preview layouts instead. The picker's previews are
     * rate limited, so they are only pushed again when something that changes their look has changed.
     */
    private fun publishPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val localized = context.withAppLanguage()
        val signature = listOf(
            BuildConfig.VERSION_CODE,
            localized.resources.configuration.locales[0].toLanguageTag(),
            WidgetStyles.lookSignature(localized),
        ).joinToString("|")
        val state = localized.getSharedPreferences("widget_preview_state", Context.MODE_PRIVATE)
        if (state.getString("signature", null) == signature) return
        try {
            val manager = AppWidgetManager.getInstance(localized)
            val previews = listOf(
                BudgetSnapshotWidgetProvider::class.java to budgetPreview(localized),
                FavouriteCategoriesWidgetProvider::class.java to categoriesPreview(localized),
                QuickTransactionWidgetProvider::class.java to bindQuick(localized, 0, QuickLayout.Row),
                AccountBalancesWidgetProvider::class.java to accountsPreview(localized),
                ScheduledTransactionsWidgetProvider::class.java to schedulesPreview(localized),
                SingleCategoryWidgetProvider::class.java to singleCategoryPreview(localized),
                SingleAccountWidgetProvider::class.java to singleAccountPreview(localized),
                SpendingTrendWidgetProvider::class.java to spendingTrendPreview(localized),
                IncomeSpendingWidgetProvider::class.java to incomeSpendingPreview(localized),
                ReportCardWidgetProvider::class.java to reportCardPreview(localized),
            )
            previews.forEach { (provider, views) ->
                manager.setWidgetPreview(ComponentName(localized, provider), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
            }
            state.edit().putString("signature", signature).apply()
        } catch (e: RuntimeException) {
            // Rate limited or refused: keep the static previews and try again at the next refresh.
        }
    }

    private fun sample(context: Context, id: Int) = context.getString(id)

    private fun budgetPreview(context: Context): RemoteViews {
        val month = YearMonth.now()
            .format(DateTimeFormatter.ofPattern("MMMM yyyy", context.resources.configuration.locales[0]))
        val ready = sample(context, R.string.widget_sample_amount_4)
        val budgeted = sample(context, R.string.widget_sample_amount_5)
        val balance = sample(context, R.string.widget_sample_amount_6)
        return bindBudget(
            context, null, 0, BudgetLayout.Hero,
            BudgetData(month, ready, budgeted, balance, spokenReady = ready, spokenBudgeted = budgeted, spokenBalance = balance),
            null,
        )
    }

    /** Six months of made-up spending, drawn by the real chart code in the chosen style. */
    private fun spendingTrendPreview(context: Context): RemoteViews {
        val today = java.time.LocalDate.now()
        val sample = listOf(41_200L, 38_500L, 52_000L, 46_100L, 39_900L, 31_000L)
        val trend = SpendingTrend(
            sample.mapIndexed { index, cents ->
                SpendingTrendMonth(java.time.YearMonth.from(today).minusMonths((sample.size - 1 - index).toLong()), cents, current = index == sample.lastIndex)
            },
        )
        return bindTrend(context, 0, TrendLayout.Chart, TrendData(true, trend), 250, 110)
    }

    /** Six months of made-up income and spending, drawn by the real chart code in the chosen style. */
    private fun incomeSpendingPreview(context: Context): RemoteViews {
        val today = java.time.LocalDate.now()
        val income = listOf(420_000L, 415_000L, 430_000L, 420_000L, 425_000L, 410_000L)
        val spent = listOf(371_200L, 388_500L, 352_000L, 396_100L, 369_900L, 301_000L)
        val series = IncomeSpending(
            income.indices.map { index ->
                IncomeSpendingMonth(
                    java.time.YearMonth.from(today).minusMonths((income.size - 1 - index).toLong()),
                    income[index], spent[index], current = index == income.lastIndex,
                )
            },
        )
        return bindIncomeSpending(context, 0, TrendLayout.Chart, IncomeSpendingData(true, series), 250, 110)
    }

    private fun reportCardPreview(context: Context): RemoteViews = bindReportCard(
        context, 0, ReportCardLayout.Small,
        ReportCardState.Card(
            sample(context, R.string.widget_sample_report_card),
            ReportCardText(sample(context, R.string.widget_sample_amount_8)),
        ),
    )

    private fun singleCategoryPreview(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_category, R.layout.widget_single_category_styled))
        val amount = sample(context, R.string.widget_sample_amount_1)
        val row = WidgetRow(
            sample(context, R.string.widget_sample_category_1), amount, 62, "",
            ProgressStatus.Normal, showProgress = true, spokenAmount = amount,
        )
        showSingleItem(context, views, row, open(context, WidgetActions.CATEGORY, 0, row.name))
        WidgetStyles.apply(context, views)
        return views
    }

    private fun singleAccountPreview(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_account, R.layout.widget_single_account_styled))
        val amount = sample(context, R.string.widget_sample_amount_7)
        val row = WidgetRow(sample(context, R.string.widget_sample_account_1), amount, 0, "", spokenAmount = amount)
        showSingleItem(context, views, row, open(context, WidgetActions.ACCOUNTS, 0, row.name))
        WidgetStyles.apply(context, views)
        return views
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun categoriesPreview(context: Context): RemoteViews {
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun accountsPreview(context: Context): RemoteViews {
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun schedulesPreview(context: Context): RemoteViews {
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

    /** A list widget's real layout with fixed [rows] in place of its adapter, for a picker preview. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun collectionPreview(
        context: Context,
        plain: Int,
        styled: Int,
        title: Int,
        listId: Int,
        rows: List<RemoteViews>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, plain, styled))
        views.setTextViewText(R.id.widget_title, context.getString(title))
        views.setViewVisibility(R.id.widget_updated, View.GONE)
        val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false).setViewTypeCount(1)
        rows.forEachIndexed { index, row -> items.addItem(index.toLong(), row) }
        views.setRemoteAdapter(listId, items.build())
        WidgetStyles.apply(context, views)
        return views
    }

    private val requestExecutor = Executors.newSingleThreadExecutor()

    /** [requestAll] off the calling thread, for callers on the main thread such as configuration callbacks. */
    fun requestAllAsync(context: Context) {
        val app = context.applicationContext
        requestExecutor.execute { requestAll(app) }
    }

    fun requestAll(context: Context) {
        val app = context.applicationContext
        previewExecutor.execute { publishPreviews(app) }
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
            is SingleCategoryWidgetProvider -> updateSingleCategory(context, manager, widgetId)
            is SingleAccountWidgetProvider -> updateSingleAccount(context, manager, widgetId)
            is SpendingTrendWidgetProvider -> updateSpendingTrend(context, manager, widgetId)
            is IncomeSpendingWidgetProvider -> updateIncomeSpending(context, manager, widgetId)
            is ReportCardWidgetProvider -> updateReportCard(context, manager, widgetId)
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

    /** The figures as displayed, and as spoken (null when balances are hidden, so they are never read out). */
    private class BudgetData(
        val month: String,
        val ready: String,
        val budgeted: String,
        val balance: String,
        val hasBudget: Boolean = true,
        val spokenReady: String? = null,
        val spokenBudgeted: String? = null,
        val spokenBalance: String? = null,
    )

    private fun loadBudget(context: Context): BudgetData {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) {
                return BudgetData(context.getString(R.string.widget_no_budget), "—", "—", "—", hasBudget = false)
            }
            val overview = repository.budgetOverview()
            val month = YearMonth.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", context.resources.configuration.locales[0]))
            return BudgetData(
                month,
                overview.toBudgetCents?.let { money(context, it) } ?: "—",
                money(context, overview.budgetedCents),
                money(context, overview.availableCents),
                spokenReady = overview.toBudgetCents?.let { spoken(context, it) } ?: "—",
                spokenBudgeted = spoken(context, overview.budgetedCents),
                spokenBalance = spoken(context, overview.availableCents),
            )
        } finally {
            repository.close()
        }
    }

    private fun updateBudget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val data = loadBudget(context)
        val updatedAt = DateFormat.getTimeFormat(context).format(Date())
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, BudgetVariants) { bindBudget(context, manager, widgetId, it, data, updatedAt) },
        )
    }

    /** [manager] and [updatedAt] are null for a picker preview, which has nothing to refresh. */
    private fun bindBudget(
        context: Context,
        manager: AppWidgetManager?,
        widgetId: Int,
        layout: BudgetLayout,
        data: BudgetData,
        updatedAt: String?,
    ): RemoteViews {
        val layoutId = when (layout) {
            BudgetLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_mini, R.layout.widget_budget_snapshot_mini_styled)
            BudgetLayout.Small -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_small, R.layout.widget_budget_snapshot_small_styled)
            BudgetLayout.Strip -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_strip, R.layout.widget_budget_snapshot_strip_styled)
            BudgetLayout.Compact -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_compact, R.layout.widget_budget_snapshot_compact_styled)
            BudgetLayout.Hero, BudgetLayout.HeroFull -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot, R.layout.widget_budget_snapshot_styled)
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
        bindFreshness(context, manager, views, widgetId, layout == BudgetLayout.HeroFull && manager != null, updatedAt.orEmpty())
        // One announcement for the whole widget, whichever layout it is showing.
        val hidden = context.getString(R.string.widget_cd_hidden)
        views.setContentDescription(
            R.id.widget_root,
            if (!data.hasBudget) {
                "${context.getString(R.string.widget_budget_snapshot)}, ${data.month}"
            } else {
                context.getString(
                    R.string.widget_cd_budget_snapshot,
                    context.getString(R.string.widget_budget_snapshot), data.month,
                    context.getString(R.string.widget_ready), data.spokenReady ?: hidden,
                    context.getString(R.string.widget_budgeted), data.spokenBudgeted ?: hidden,
                    context.getString(R.string.widget_balance), data.spokenBalance ?: hidden,
                )
            },
        )
        WidgetStyles.apply(context, views)
        return views
    }

    private fun updateCategories(context: Context, manager: AppWidgetManager, widgetId: Int) {
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

    private fun updateQuickTransaction(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, QuickVariants) { bindQuick(context, widgetId, it) },
        )
    }

    private fun bindQuick(context: Context, widgetId: Int, layout: QuickLayout): RemoteViews {
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

    private fun updateAccounts(context: Context, manager: AppWidgetManager, widgetId: Int) {
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

    /** One row of the Favourite categories or Account balances list. [target] is what a tap opens. */
    internal data class WidgetRow(
        val name: String,
        val amount: String,
        val progress: Int,
        val target: String,
        val status: ProgressStatus = ProgressStatus.Normal,
        val showProgress: Boolean = false,
        /** The amount as spoken, or null when balances are hidden. */
        val spokenAmount: String? = null,
    )

    /** A category as a widget row: its available amount, and how much of what was budgeted is spent. */
    private fun categoryRowOf(context: Context, row: BudgetCategory): WidgetRow {
        val spent = row.spentCents.coerceAtLeast(0)
        val denominator = row.assignedCents.coerceAtLeast(1)
        val percent = ((spent * 100 / denominator).coerceIn(0, 100)).toInt()
        return WidgetRow(
            row.name, money(context, row.balanceCents), percent, row.name,
            ProgressStatus.of(percent, overspent = row.balanceCents < 0), showProgress = true,
            spokenAmount = spoken(context, row.balanceCents),
        )
    }

    /**
     * The widget's size in dp as it is shown now. A launcher reports the smallest size (portrait width, landscape
     * height) and the largest (portrait height, landscape width), so the one that matches the orientation is used;
     * the other would draw a chart that is then stretched to fit.
     */
    private fun widgetSizeDp(context: Context, manager: AppWidgetManager, widgetId: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(widgetId)
        fun option(key: String, fallback: Int) = options.getInt(key, 0).takeIf { it > 0 } ?: fallback
        val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (portrait) {
            option(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) to option(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        } else {
            option(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250) to option(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        }
    }

    /** What the Spending trend widget draws: nothing without a budget, or the trend (which may be empty). */
    private class TrendData(val usingBudget: Boolean, val trend: SpendingTrend?)

    private fun updateSpendingTrend(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val data = try {
            loadBudgetFormats(repository)
            val using = repository.isUsingActualBudget
            TrendData(using, if (using) repository.spendingTrend() else null)
        } finally {
            repository.close()
        }
        // The chart is a bitmap, so it is drawn for the widget's current size and redrawn when it is resized.
        val (widthDp, heightDp) = widgetSizeDp(context, manager, widgetId)
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, TrendVariants) { bindTrend(context, widgetId, it, data, widthDp, heightDp) },
        )
    }

    /**
     * The Spending trend widget: the headline, and a bar chart of the months drawn into a bitmap sized to
     * the widget ([widthDp] x [heightDp]). Smaller layouts show this month against the last.
     */
    private fun bindTrend(
        context: Context,
        widgetId: Int,
        layout: TrendLayout,
        data: TrendData,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val small = layout == TrendLayout.Small
        val views = RemoteViews(
            context.packageName,
            if (small) WidgetStyles.layout(context, R.layout.widget_spending_trend_small, R.layout.widget_spending_trend_small_styled)
            else WidgetStyles.layout(context, R.layout.widget_spending_trend, R.layout.widget_spending_trend_styled),
        )
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_spending_trend))
        val trend = data.trend
        val message = when {
            !data.usingBudget -> context.getString(R.string.widget_no_budget)
            trend == null || trend.isEmpty -> context.getString(R.string.widget_trend_no_spending)
            else -> null
        }
        val showContent = message == null
        views.setViewVisibility(R.id.widget_trend_message, if (showContent) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_trend_chart, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_trend_total, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_trend_period, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_trend_content, if (showContent) View.VISIBLE else View.GONE)
        if (small) views.setViewVisibility(R.id.widget_title, View.GONE)
        if (message != null || trend == null) {
            views.setTextViewText(R.id.widget_trend_message, message.orEmpty())
            views.setContentDescription(R.id.widget_root, "${context.getString(R.string.widget_spending_trend)}. $message")
            // With no budget there is nothing to open in Reports yet; opening Actua lets the person choose one.
            views.setOnClickPendingIntent(
                R.id.widget_root,
                open(context, if (data.usingBudget) WidgetActions.REPORTS else WidgetActions.BUDGET, widgetId),
            )
            WidgetStyles.apply(context, views)
            return views
        }

        val shown = if (small) SpendingTrend(trend.months.takeLast(2)) else trend
        val hidden = balancesHidden(context)
        val headline = if (small) shown.currentCents else shown.totalCents
        views.setTextViewText(R.id.widget_trend_total, money(context, headline))
        views.setTextViewText(
            R.id.widget_trend_period,
            context.getString(if (small) R.string.widget_trend_period_this_month else R.string.widget_trend_period_months),
        )

        val density = context.resources.displayMetrics.density
        // Leave room for the padding and, in the full layout, the title and period lines above the chart.
        val chartWidthDp = if (small) 44 else (widthDp - 32).coerceAtLeast(60)
        val chartHeightDp = if (small) (heightDp - 12).coerceAtLeast(24) else (heightDp - 32 - 41).coerceAtLeast(36)
        val bitmap = SpendingChart.render(
            shown,
            widthPx = (chartWidthDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            heightPx = (chartHeightDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            colors = WidgetStyles.chartColors(context),
            metrics = context.resources.displayMetrics,
            locale = context.resources.configuration.locales[0],
            // Amounts are left off the picture when balances are hidden, and in the small layout.
            amountLabel = if (small || hidden) null else money(context, shown.currentCents),
        )
        views.setImageViewBitmap(R.id.widget_trend_chart, bitmap)
        views.setContentDescription(R.id.widget_trend_chart, trendDescription(context, shown, hidden))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.REPORTS, widgetId))
        WidgetStyles.apply(context, views)
        return views
    }

    /** The chart in words for a screen reader: each month and its amount, and the total. Amounts are not read when hidden. */
    private fun trendDescription(context: Context, trend: SpendingTrend, hidden: Boolean): String {
        if (hidden) return context.getString(R.string.widget_cd_spending_trend_hidden)
        val locale = context.resources.configuration.locales[0]
        val monthName = DateTimeFormatter.ofPattern("MMMM", locale)
        fun describe(month: SpendingTrendMonth): String {
            val name = month.month.format(monthName).let {
                if (month.current) context.getString(R.string.widget_cd_trend_this_month, it) else it
            }
            return context.getString(R.string.widget_cd_trend_month, name, money(context, month.spentCents))
        }
        return context.getString(
            R.string.widget_cd_spending_trend,
            trend.months.size, trend.months.joinToString(", ", transform = ::describe), money(context, trend.totalCents),
        )
    }

    /** What the Income and spending widget draws: nothing without a budget, or the series (which may be empty). */
    private class IncomeSpendingData(val usingBudget: Boolean, val series: IncomeSpending?)

    private fun updateIncomeSpending(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val data = try {
            loadBudgetFormats(repository)
            val using = repository.isUsingActualBudget
            IncomeSpendingData(using, if (using) repository.incomeSpending() else null)
        } finally {
            repository.close()
        }
        val (widthDp, heightDp) = widgetSizeDp(context, manager, widgetId)
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, IncomeSpendingVariants) { bindIncomeSpending(context, widgetId, it, data, widthDp, heightDp) },
        )
    }

    /**
     * The Income and spending widget: what is left this month with its savings rate, this month's income and
     * spending, and paired bars for the months drawn into a bitmap sized to the widget ([widthDp] x
     * [heightDp]). The smallest layout shows this month's income against spending.
     */
    private fun bindIncomeSpending(
        context: Context,
        widgetId: Int,
        layout: TrendLayout,
        data: IncomeSpendingData,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val small = layout == TrendLayout.Small
        val views = RemoteViews(
            context.packageName,
            if (small) WidgetStyles.layout(context, R.layout.widget_income_spending_small, R.layout.widget_income_spending_small_styled)
            else WidgetStyles.layout(context, R.layout.widget_income_spending, R.layout.widget_income_spending_styled),
        )
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_income_spending))
        val series = data.series
        val message = when {
            !data.usingBudget -> context.getString(R.string.widget_no_budget)
            series == null || series.isEmpty -> context.getString(R.string.widget_is_empty)
            else -> null
        }
        val showContent = message == null
        views.setViewVisibility(R.id.widget_is_message, if (showContent) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_is_chart, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_is_left, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_is_period, if (showContent) View.VISIBLE else View.GONE)
        if (small) {
            views.setViewVisibility(R.id.widget_is_content, if (showContent) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_title, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_is_income, if (showContent) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_is_spent, if (showContent) View.VISIBLE else View.GONE)
        }
        if (message != null || series == null) {
            views.setTextViewText(R.id.widget_is_message, message.orEmpty())
            views.setContentDescription(R.id.widget_root, "${context.getString(R.string.widget_income_spending)}. $message")
            // With no budget there is nothing to open in Reports yet; opening Actua lets the person choose one.
            views.setOnClickPendingIntent(
                R.id.widget_root,
                open(context, if (data.usingBudget) WidgetActions.REPORTS else WidgetActions.BUDGET, widgetId),
            )
            WidgetStyles.apply(context, views)
            return views
        }

        val hidden = balancesHidden(context)
        val shown = if (small) IncomeSpending(series.months.takeLast(1)) else series
        val now = series.current ?: series.months.last()
        views.setTextViewText(R.id.widget_is_left, money(context, now.leftCents))
        // The savings rate is a share of income, so it is left off with the amounts when balances are hidden.
        val rate = now.savingsRatePercent.takeIf { !hidden }
        views.setTextViewText(
            R.id.widget_is_period,
            if (rate != null && !small) context.getString(R.string.widget_is_left_rate, rate)
            else context.getString(R.string.widget_is_left_label),
        )
        if (!small) {
            views.setTextViewText(R.id.widget_is_income, context.getString(R.string.widget_is_income, money(context, now.incomeCents)))
            views.setTextViewText(R.id.widget_is_spent, context.getString(R.string.widget_is_spent, money(context, now.spentCents)))
        }

        val density = context.resources.displayMetrics.density
        // Leave room for the padding and, in the full layout, the title, period and amount lines above the chart.
        val chartWidthDp = if (small) 44 else (widthDp - 32).coerceAtLeast(60)
        val chartHeightDp = if (small) (heightDp - 12).coerceAtLeast(24) else (heightDp - 32 - 56).coerceAtLeast(30)
        val bitmap = IncomeSpendingChart.render(
            shown,
            widthPx = (chartWidthDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            heightPx = (chartHeightDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            colors = WidgetStyles.chartColors(context),
            metrics = context.resources.displayMetrics,
            locale = context.resources.configuration.locales[0],
        )
        views.setImageViewBitmap(R.id.widget_is_chart, bitmap)
        views.setContentDescription(R.id.widget_is_chart, incomeSpendingDescription(context, shown, now, hidden))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.REPORTS, widgetId))
        WidgetStyles.apply(context, views)
        return views
    }

    /**
     * The chart in words for a screen reader: each month's income and spending, and this month's left over
     * and savings rate. Nothing is read when balances are hidden.
     */
    private fun incomeSpendingDescription(context: Context, series: IncomeSpending, now: IncomeSpendingMonth, hidden: Boolean): String {
        if (hidden) return context.getString(R.string.widget_cd_income_spending_hidden)
        val locale = context.resources.configuration.locales[0]
        val monthName = DateTimeFormatter.ofPattern("MMMM", locale)
        fun describe(month: IncomeSpendingMonth): String {
            val name = month.month.format(monthName).let {
                if (month.current) context.getString(R.string.widget_cd_is_this_month, it) else it
            }
            return context.getString(
                R.string.widget_cd_is_month, name, money(context, month.incomeCents), money(context, month.spentCents),
            )
        }
        val summary = context.getString(
            R.string.widget_cd_income_spending, series.months.size, series.months.joinToString(", ", transform = ::describe),
        )
        val left = context.getString(R.string.widget_cd_is_left, money(context, now.leftCents))
        val rate = now.savingsRatePercent?.let { context.getString(R.string.widget_cd_is_rate, it) }
        return listOfNotNull(summary, left, rate).joinToString(" ")
    }

    private fun updateSingleCategory(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, WidgetStyles.layout(context, R.layout.widget_single_category, R.layout.widget_single_category_styled))
        val repository = ActuaRepository(context)
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
                    is SingleChoice.Found -> showSingleItem(
                        context, views, categoryRowOf(context, choice.item),
                        open(context, WidgetActions.CATEGORY, widgetId, choice.item.name),
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
            WidgetStyles.apply(context, views)
            manager.updateAppWidget(widgetId, views)
        } finally {
            repository.close()
        }
    }

    private fun updateSingleAccount(context: Context, manager: AppWidgetManager, widgetId: Int) {
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

    /** One account or category in a single-item widget: its name and amount, and a progress bar for a category. */
    private fun showSingleItem(context: Context, views: RemoteViews, row: WidgetRow, tap: PendingIntent) {
        views.setViewVisibility(R.id.widget_single_message, View.GONE)
        views.setViewVisibility(R.id.widget_single_name, View.VISIBLE)
        views.setViewVisibility(R.id.widget_single_amount, View.VISIBLE)
        views.setTextViewText(R.id.widget_single_name, row.name)
        views.setTextViewText(R.id.widget_single_amount, row.amount)
        views.setViewVisibility(R.id.widget_single_progress, if (row.showProgress) View.VISIBLE else View.GONE)
        if (row.showProgress) {
            views.setProgressBar(R.id.widget_single_progress, 100, row.progress, false)
            WidgetStyles.applyProgressStatus(context, views, R.id.widget_single_progress, row.status)
        }
        val amount = row.spokenAmount ?: context.getString(R.string.widget_cd_hidden)
        views.setContentDescription(
            R.id.widget_root,
            if (row.showProgress) context.getString(R.string.widget_cd_category_row, row.name, amount, row.progress)
            else context.getString(R.string.widget_cd_account_row, row.name, amount),
        )
        views.setOnClickPendingIntent(R.id.widget_root, tap)
    }

    /** A state with nothing to show (no budget, item not found), saying what to do and opening the right screen. */
    private fun showSingleMessage(views: RemoteViews, text: String, tap: PendingIntent) {
        views.setViewVisibility(R.id.widget_single_name, View.GONE)
        views.setViewVisibility(R.id.widget_single_amount, View.GONE)
        views.setViewVisibility(R.id.widget_single_progress, View.GONE)
        views.setViewVisibility(R.id.widget_single_message, View.VISIBLE)
        views.setTextViewText(R.id.widget_single_message, text)
        views.setContentDescription(R.id.widget_root, text)
        views.setOnClickPendingIntent(R.id.widget_root, tap)
    }

    /** What the Report card widget shows: a card's name and headline, or a message and where a tap on it leads. */
    private sealed interface ReportCardState {
        class Card(val name: String, val text: ReportCardText) : ReportCardState
        class Message(val text: String, val tap: PendingIntent) : ReportCardState
    }

    private fun updateReportCard(context: Context, manager: AppWidgetManager, widgetId: Int) {
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
            // The whole row is one announcement: name, amount and, for a category, how much is spent.
            val amount = row.spokenAmount ?: localized.getString(R.string.widget_cd_hidden)
            setContentDescription(
                R.id.widget_item_row,
                if (row.showProgress) localized.getString(R.string.widget_cd_category_row, row.name, amount, row.progress)
                else localized.getString(R.string.widget_cd_account_row, row.name, amount),
            )
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

    /**
     * The "Updated 10:42" line, which is also the refresh target. Only the taller layouts have room for
     * its 48dp height; the others hide it.
     */
    private fun bindFreshness(
        context: Context,
        manager: AppWidgetManager?,
        views: RemoteViews,
        widgetId: Int,
        show: Boolean,
        updatedAt: String,
    ) {
        views.setViewVisibility(R.id.widget_updated, if (show) View.VISIBLE else View.GONE)
        if (!show) return
        views.setTextViewText(R.id.widget_updated, context.getString(R.string.widget_updated_at, updatedAt))
        views.setContentDescription(R.id.widget_updated, context.getString(R.string.widget_cd_refresh, updatedAt))
        val provider = manager?.getAppWidgetInfo(widgetId)?.provider ?: return
        val refresh = Intent(WidgetActions.REFRESH).apply {
            component = provider
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setOnClickPendingIntent(
            R.id.widget_updated,
            PendingIntent.getBroadcast(context, widgetId, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
    }

    /** Opens a widget's configuration screen, as the launcher does when it is reconfigured. */
    private fun configure(context: Context, activity: Class<*>, widgetId: Int): PendingIntent {
        val intent = Intent(context, activity).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(
            context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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

    private fun balancesHidden(context: Context): Boolean = DisplayPreferences(context).resolve(budgetFormats).hideBalances

    /** [cents] formatted for TalkBack, or null when balances are hidden: the dots on screen are never read out. */
    private fun spoken(context: Context, cents: Long): String? =
        if (balancesHidden(context)) null else money(context, cents)

    private fun money(context: Context, cents: Long): String {
        val formats = DisplayPreferences(context).resolve(budgetFormats)
        applyDisplayFormats(formats)
        return formatMoneyCents(cents, formats.hideDecimals,
            respectBalanceVisibility = false).let { if (formats.hideBalances) "••••" else it }
    }
}
