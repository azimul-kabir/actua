package com.azimulkabir.actua.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.preferences.BudgetDisplayFormats
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.withAppLanguage
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.applyDisplayFormats
import com.azimulkabir.actua.ui.components.formatMoneyCents

// Helpers shared by the widget files: layout selection, list and single-item rows, pending intents,
// and the budget's display formats. Each widget's own bind code lives in its own file.

/** A bitmap handed to RemoteViews counts against the launcher's memory budget, so charts are kept small. */
internal const val MAX_CHART_PX = 1400

internal fun sample(context: Context, id: Int) = context.getString(id)

/** A list widget's real layout with fixed [rows] in place of its adapter, for a picker preview. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
internal fun collectionPreview(
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

/**
 * The widget for the launcher, with one layout per entry of [variants]. On Android 12+ every size is
 * sent and the launcher picks the best fit itself, and lays out again on a resize without an update.
 * Before that the layout for the widget's current size is chosen here, with the same breakpoints.
 */
internal fun <T> responsive(
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

// The service-backed adapter is deprecated on newer releases, but its replacement
// (RemoteCollectionItems) needs API 31 and minSdk is 28.
@Suppress("DEPRECATION")
internal fun bindItemList(context: Context, views: RemoteViews, widgetId: Int, kind: WidgetKind, action: String) {
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
internal fun categoryRowOf(context: Context, row: BudgetCategory): WidgetRow {
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
internal fun widgetSizeDp(context: Context, manager: AppWidgetManager, widgetId: Int): Pair<Int, Int> {
    val options = manager.getAppWidgetOptions(widgetId)
    fun option(key: String, fallback: Int) = options.getInt(key, 0).takeIf { it > 0 } ?: fallback
    val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
    return if (portrait) {
        option(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) to option(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
    } else {
        option(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250) to option(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
    }
}

/** One account or category in a single-item widget: its name and amount, and a progress bar for a category. */
internal fun showSingleItem(context: Context, views: RemoteViews, row: WidgetRow, tap: PendingIntent): ProgressStatus {
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
    return if (row.showProgress) row.status else ProgressStatus.Normal
}

/** A state with nothing to show (no budget, item not found), saying what to do and opening the right screen. */
internal fun showSingleMessage(views: RemoteViews, text: String, tap: PendingIntent) {
    views.setViewVisibility(R.id.widget_single_name, View.GONE)
    views.setViewVisibility(R.id.widget_single_amount, View.GONE)
    views.setViewVisibility(R.id.widget_single_progress, View.GONE)
    views.setViewVisibility(R.id.widget_single_message, View.VISIBLE)
    views.setTextViewText(R.id.widget_single_message, text)
    views.setContentDescription(R.id.widget_root, text)
    views.setOnClickPendingIntent(R.id.widget_root, tap)
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
        WidgetStyles.apply(localized, this, if (row.showProgress) row.status else ProgressStatus.Normal)
    }
}

internal fun open(context: Context, action: String, requestCode: Int, target: String? = null): PendingIntent {
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
internal fun bindFreshness(
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
internal fun configure(context: Context, activity: Class<*>, widgetId: Int): PendingIntent {
    val intent = Intent(context, activity).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return PendingIntent.getActivity(
        context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun openTemplate(context: Context, action: String, requestCode: Int): PendingIntent {
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

internal fun loadBudgetFormats(repository: ActuaRepository) {
    CurrencyDisplay.decimalPlaces = repository.budgetDecimalPlaces()
    budgetFormats = repository.budgetDisplayFormats()
}

internal fun balancesHidden(context: Context): Boolean = DisplayPreferences(context).resolve(budgetFormats).hideBalances

/** [cents] formatted for TalkBack, or null when balances are hidden: the dots on screen are never read out. */
internal fun spoken(context: Context, cents: Long): String? =
    if (balancesHidden(context)) null else money(context, cents)

internal fun money(context: Context, cents: Long): String {
    val formats = DisplayPreferences(context).resolve(budgetFormats)
    applyDisplayFormats(formats)
    return formatMoneyCents(cents, formats.hideDecimals,
        respectBalanceVisibility = false).let { if (formats.hideBalances) "••••" else it }
}
