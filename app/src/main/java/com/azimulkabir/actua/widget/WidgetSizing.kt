package com.azimulkabir.actua.widget

/** The most rows the Favourite categories and Account balances widgets list; more scroll within the widget. */
internal const val MAX_WIDGET_ROWS = 8

/**
 * A layout that suits a widget at least [minWidthDp] wide and [minHeightDp] tall. On Android 12+ these
 * become the sizes of `RemoteViews(Map<SizeF, RemoteViews>)`, so the launcher picks the layout itself and
 * re-lays out on resize; below that, [WidgetSizing.pick] makes the same choice from the widget's options.
 */
internal data class WidgetVariant<T>(val minWidthDp: Int, val minHeightDp: Int, val layout: T) {
    val areaDp: Long get() = minWidthDp.toLong() * minHeightDp
}

internal object WidgetSizing {
    /**
     * The variant for a widget of [widthDp] x [heightDp], matching how the framework resolves a size map:
     * the largest variant that fits, or the smallest one when none does.
     */
    fun <T> pick(variants: List<WidgetVariant<T>>, widthDp: Int, heightDp: Int): WidgetVariant<T> {
        require(variants.isNotEmpty()) { "A widget needs at least one layout" }
        val fitting = variants.filter { it.minWidthDp <= widthDp && it.minHeightDp <= heightDp }
        return if (fitting.isNotEmpty()) {
            fitting.maxWith(compareBy({ it.areaDp }, { it.minWidthDp }))
        } else {
            variants.minWith(compareBy({ it.areaDp }, { it.minWidthDp }))
        }
    }
}

/*
 * The layouts of each widget and the size each one starts at. Sizes are the dp a launcher reports for a
 * grid of n cells: about 40, 110, 180 and 250 for one to four cells across or down.
 */

internal enum class BudgetLayout { Mini, Small, Strip, Compact, Hero }

/** Budget snapshot: one figure at 2x1, plus a title at 2x2, a one-row strip at 4x1, three tiles from 3x2, the full card from 2x3. */
internal val BudgetVariants = listOf(
    WidgetVariant(40, 40, BudgetLayout.Mini),
    WidgetVariant(40, 100, BudgetLayout.Small),
    WidgetVariant(240, 40, BudgetLayout.Strip),
    WidgetVariant(180, 100, BudgetLayout.Compact),
    WidgetVariant(40, 170, BudgetLayout.Hero),
    WidgetVariant(180, 170, BudgetLayout.Hero),
)

internal enum class QuickLayout { Single, Stacked, Row }

/**
 * Quick transaction: every button keeps a 48dp touch target, so Expense stands alone until there is room
 * for three: a row of three from 4x1 and three stacked buttons from 2x3.
 */
internal val QuickVariants = listOf(
    WidgetVariant(40, 40, QuickLayout.Single),
    WidgetVariant(240, 40, QuickLayout.Row),
    WidgetVariant(40, 180, QuickLayout.Stacked),
    WidgetVariant(240, 180, QuickLayout.Stacked),
)

internal enum class ListLayout { Mini, Titled }

/** The list widgets (categories, accounts, schedules): the list alone while short, with a title from 2x2. */
internal val ListVariants = listOf(
    WidgetVariant(40, 40, ListLayout.Mini),
    WidgetVariant(40, 100, ListLayout.Titled),
)
