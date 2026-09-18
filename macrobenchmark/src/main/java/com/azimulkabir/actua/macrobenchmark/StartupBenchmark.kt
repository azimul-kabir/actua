package com.azimulkabir.actua.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold/warm/hot startup timing (#321). `CompilationMode.Partial()` mirrors what a real install
 * looks like once the app's baseline profile (PR #319) has been applied by profileinstaller,
 * rather than the fully-JIT'd or fully-AOT'd extremes.
 *
 * `MainActivity`/`AppNavigation` calls `Activity.reportFullyDrawn()` once the Budget tab has
 * real data to show, so [StartupTimingMetric] reports both time-to-initial-display (first
 * frame) and time-to-full-display (usable Budget screen) — see the `reportedFullyDrawn`
 * `LaunchedEffect` in AppNavigation.kt.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = startup(StartupMode.COLD)

    @Test
    fun warmStartup() = startup(StartupMode.WARM)

    @Test
    fun hotStartup() = startup(StartupMode.HOT)

    private fun startup(startupMode: StartupMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = startupMode,
        compilationMode = CompilationMode.Partial(),
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = seedDemoBudget()
    }
}
