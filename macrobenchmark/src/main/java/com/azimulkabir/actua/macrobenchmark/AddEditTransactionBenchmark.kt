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
 * Add Transaction's amount field is read-only and delegates entry to `CalculatorAmountSheet`'s
 * on-screen keypad (see AddTransactionScreen.kt / CalculatorAmountSheet.kt), so "keypad
 * interactions" from #321's journey list means tapping that digit pad, not the IME.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AddEditTransactionBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openAndClose() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget() },
    ) {
        click(By.desc("Add"))
        // Add opens with the amount keypad already showing; Back closes it and reveals Cancel.
        awaitVisible(By.text("✓"), "The amount keypad")
        navigateBack()
        click(By.desc("Cancel"))
    }

    @Test
    fun keypadEntry() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { launchToBudget(); click(By.desc("Add")) },
    ) {
        // The keypad opens with the screen. "✓" confirms the amount and moves on to the payee picker; Back returns
        // to Add and Cancel leaves it, so the next iteration starts from the Budget root.
        for (digit in listOf("1", "2", ".", "5", "0")) {
            click(By.text(digit))
        }
        click(By.text("✓"))
        awaitVisible(By.text("Find or add a payee"), "The payee picker")
        navigateBack()
        click(By.desc("Cancel"))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = seedDemoBudget()
    }
}
