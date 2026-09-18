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

/**
 * The Transactions tab is the "All Transactions" list from #321's journey list; opening it from
 * a cold Budget landing, scrolling it and searching/filtering it are separately measured here.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TransactionsScreenBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openAndScroll() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        navigateToTab("Transactions")
        scrollMainList()
    }

    @Test
    fun search() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget(); navigateToTab("Transactions") },
    ) {
        click(By.desc("Search transactions"))
        device.executeShellCommand("input text groceries")
        device.waitForIdle()
        navigateBack()
    }

    @Test
    fun toggleClearedFilter() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget(); navigateToTab("Transactions") },
    ) {
        click(By.text("Cleared"))
        click(By.text("Uncleared"))
        click(By.text("All"))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = seedDemoBudget()
    }
}
