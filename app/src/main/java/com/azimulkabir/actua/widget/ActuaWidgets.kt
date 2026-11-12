package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.withAppLanguage
import com.azimulkabir.actua.data.schedules.ScheduleWidgetPeriod
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
                BudgetSnapshotWidgetProvider::class.java to BudgetSnapshotWidget.preview(localized),
                FavouriteCategoriesWidgetProvider::class.java to FavouriteCategoriesWidget.preview(localized),
                QuickTransactionWidgetProvider::class.java to QuickTransactionWidget.bindQuick(localized, 0, QuickLayout.Row),
                AccountBalancesWidgetProvider::class.java to AccountBalancesWidget.preview(localized),
                ScheduledTransactionsWidgetProvider::class.java to ScheduledTransactionsWidget.preview(localized),
                SingleCategoryWidgetProvider::class.java to SingleCategoryWidget.preview(localized),
                SingleAccountWidgetProvider::class.java to SingleAccountWidget.preview(localized),
                SpendingTrendWidgetProvider::class.java to SpendingTrendWidget.preview(localized),
                IncomeSpendingWidgetProvider::class.java to IncomeSpendingWidget.preview(localized),
                ReportCardWidgetProvider::class.java to ReportCardWidget.preview(localized),
            )
            previews.forEach { (provider, views) ->
                manager.setWidgetPreview(ComponentName(localized, provider), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
            }
            state.edit().putString("signature", signature).apply()
        } catch (e: RuntimeException) {
            // Rate limited or refused: keep the static previews and try again at the next refresh.
        }
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
            is BudgetSnapshotWidgetProvider -> BudgetSnapshotWidget.update(context, manager, widgetId)
            is FavouriteCategoriesWidgetProvider -> FavouriteCategoriesWidget.update(context, manager, widgetId)
            is QuickTransactionWidgetProvider -> QuickTransactionWidget.update(context, manager, widgetId)
            is AccountBalancesWidgetProvider -> AccountBalancesWidget.update(context, manager, widgetId)
            is ScheduledTransactionsWidgetProvider -> ScheduledTransactionsWidget.update(context, manager, widgetId)
            is SingleCategoryWidgetProvider -> SingleCategoryWidget.update(context, manager, widgetId)
            is SingleAccountWidgetProvider -> SingleAccountWidget.update(context, manager, widgetId)
            is SpendingTrendWidgetProvider -> SpendingTrendWidget.update(context, manager, widgetId)
            is IncomeSpendingWidgetProvider -> IncomeSpendingWidget.update(context, manager, widgetId)
            is ReportCardWidgetProvider -> ReportCardWidget.update(context, manager, widgetId)
            else -> Unit
        }
    }
}
