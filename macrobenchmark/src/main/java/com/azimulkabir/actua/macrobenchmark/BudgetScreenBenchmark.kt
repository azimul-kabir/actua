package com.azimulkabir.actua.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Budget is the app's default landing tab (#316/#321): scrolling, category-group
 * expand/collapse, month switching, group reorder and opening Category Details are the flows
 * users hit most often, so frame timing here is the most representative single signal for
 * perceived smoothness.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BudgetScreenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scroll() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        scrollMainList()
    }

    @Test
    fun expandCollapseCategoryGroup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        // Toggles the same group's expand/collapse control back and forth; its content
        // description flips between "Expand Essentials" and "Collapse Essentials".
        repeat(6) { click(By.descContains("Essentials")) }
    }

    @Test
    fun switchMonth() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        click(By.desc("Choose month"))
        // Always different from the currently-selected month, regardless of what "today" is.
        val currentMonthNumber = YearMonth.now().monthValue
        val targetMonthNumber = if (currentMonthNumber == 1) 2 else 1
        val targetMonthName = Month.of(targetMonthNumber).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        click(By.text(targetMonthName))
    }

    @Test
    fun openCategoryDetails() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        click(By.text("Groceries"))
        navigateBack()
    }

    @Test
    fun reorderCategoryGroup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            launchToBudget()
            click(By.desc("Manage Categories"))
            click(By.desc("Reorder Groups"))
        },
    ) {
        // Exercises the same move-and-persist path as drag reordering (see
        // CategoryDragReorder/CategoryReorderPlanner) without a flaky simulated drag gesture —
        // this is the mutation PR #250 highlighted as persisting on every intermediate step
        // instead of once at drop (#316's stated anti-pattern to avoid).
        click(By.desc("Move Essentials group down"))
        click(By.desc("Move Essentials group up"))
        // Return to Budget so the next iteration's setupBlock starts from a known root instead
        // of stacking another Manage Categories/Reorder Groups pair on top.
        navigateBack()
        navigateBack()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = seedDemoBudget()
    }
}
