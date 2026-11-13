package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.azimulkabir.actua.billing.supporterPerksAvailable
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.preferences.SupporterPerks
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.ui.home.GreetingFigure
import com.azimulkabir.actua.ui.home.GreetingProjection
import java.time.LocalDate

/**
 * What the supporter greeting needs: whether the user is a supporter, whether there is anything to greet them
 * about (a budget with transactions), and the figures worth saying (possibly none).
 */
internal data class GreetingState(val supporter: Boolean, val show: Boolean, val figures: List<GreetingFigure>) {
    companion object {
        val None = GreetingState(false, false, emptyList())
    }
}

/**
 * The greeting for the open budget. Supporter-only, and only on a build with Play perks. The transaction counts
 * are one aggregate query off the main thread; the rest comes from lists already loaded.
 */
@Composable
internal fun rememberGreetingState(
    repository: ActuaRepository,
    dataVersion: Int,
    budgetGroups: List<BudgetGroup>,
    schedules: List<ScheduleListItem>,
): GreetingState {
    val context = LocalContext.current
    val supporter = remember(dataVersion) { supporterPerksAvailable() && SupporterPerks(context).isSupporter }
    val stats = rememberLoaded<ActualBudgetDatabase.GreetingStats?>(
        repository, dataVersion, initial = null, enabled = supporter,
    ) {
        val today = LocalDate.now()
        repository.greetingStats(today.minusDays(GreetingProjection.WEEK_DAYS - 1L).toDayInt(), today.toDayInt())
    }
    return remember(supporter, stats, budgetGroups, schedules) {
        when {
            !supporter -> GreetingState.None
            stats == null || stats.count == 0 -> GreetingState(true, false, emptyList())
            else -> GreetingState(
                true, true,
                GreetingProjection.figures(
                    uncategorized = stats.uncategorized,
                    addedThisWeek = stats.added,
                    billsDueThisWeek = GreetingProjection.billsDueThisWeek(schedules),
                    budgetGroups = budgetGroups,
                ),
            )
        }
    }
}

private fun LocalDate.toDayInt(): Int = year * 10_000 + monthValue * 100 + dayOfMonth
