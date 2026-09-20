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
 * Bottom-navigation tab switching and back navigation (including predictive back on API 34+,
 * which the OS drives the same way as a normal back gesture from UiAutomator's perspective)
 * are cross-cutting flows that happen on almost every session — see #321/#327.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NavigationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun switchBottomNavTabs() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        navigateToTab("Accounts")
        navigateToTab("Transactions")
        navigateToTab("Home")
        navigateToTab("Manage")
        navigateToTab("Budget")
    }

    @Test
    fun backNavigationFromDetail() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        click(By.text("Groceries"))
        navigateBack()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = seedDemoBudget()
    }
}
