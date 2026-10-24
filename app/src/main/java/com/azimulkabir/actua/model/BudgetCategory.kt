package com.azimulkabir.actua.model

import androidx.annotation.StringRes
import com.azimulkabir.actua.R

data class BudgetCategory(
    val name: String,
    val assigned: Int,
    val spent: Int,
    private val actualAvailable: Int? = null,
    private val actualAssignedCents: Long? = null,
    val id: String? = null,
    val availableCents: Long? = null,
    val hidden: Boolean = false,
    val spentCents: Long = spent.toLong() * 100,
    val carryoverEnabled: Boolean = false,
    val note: String = "",
    val history: List<BudgetHistory> = emptyList(),
    val isIncome: Boolean = false,
    val target: BudgetTarget? = null,
    val hasUnsupportedTarget: Boolean = false,
    val automations: List<BudgetTarget> = target?.let(::listOf).orEmpty(),
    val unsupportedAutomationTypes: List<String> = emptyList(),
    val automationReadOnly: Boolean = false,
    val goalCents: Long? = null,
    val longGoal: Boolean = false,
    val cleanupTargets: List<CleanupTarget> = emptyList(),
    val cleanupInvalid: Boolean = false,
) {
    val available: Int get() = actualAvailable ?: assigned - spent
    val assignedCents: Long get() = actualAssignedCents ?: assigned.toLong() * 100
    val balanceCents: Long get() = availableCents ?: available.toLong() * 100
    val carryoverCents: Long get() = balanceCents - assignedCents + spentCents

    /**
     * Whether templates can run for this category. A notes-managed definition (read-only in the
     * editor) still runs when every directive is supported, as Actual applies note templates.
     */
    val automationsEvaluable: Boolean
        get() = !hasUnsupportedTarget || (automationReadOnly && unsupportedAutomationTypes.isEmpty())

    /**
     * The full balance this category is working toward, independent of this month's
     * installment. A locally-known "goal only", "by date" or "cover schedule" target wins
     * over Actual's server-synced [goalCents]: the server value can be a stale monthly
     * installment amount left over from before an automation was last (re-)applied, while the
     * target definition itself always resolves to the true end goal. [goalCents] is only used
     * as a fallback when no supported target is present locally (e.g. a target type Actua
     * doesn't model) or a schedule target can't yet be resolved against [schedules].
     */
    fun effectiveGoalCents(schedules: List<BudgetScheduleFunding> = emptyList()): Long? {
        val targets = automations.ifEmpty { target?.let(::listOf).orEmpty() }
        val hasBalanceTarget = targets.any {
            it.type == BudgetTarget.Type.GOAL ||
                it.type == BudgetTarget.Type.BY_DATE ||
                it.type == BudgetTarget.Type.SCHEDULE
        }
        if (hasBalanceTarget) {
            BudgetTemplatePlanner.targetBalanceGoal(targets, this, schedules)?.let { return it }
        }
        return goalCents?.takeIf { it > 0L }
    }

    /**
     * Whether the progress bar measures the balance against a full target instead of spending.
     * A long-term goal always does, matching Actual's `long_goal`. By-date and cover-schedule
     * targets only do when every other funding automation is also one of them: a category that
     * also budgets spending money (e.g. from history) keeps the spending bar, because spending
     * that money would otherwise read as falling behind the schedule. Synced [longGoal] is only
     * a fallback when no supported funding automation is known locally.
     */
    val usesGoalProgress: Boolean
        get() {
            val targets = automations.ifEmpty { target?.let(::listOf).orEmpty() }
            if (targets.any { it.type == BudgetTarget.Type.GOAL }) return true
            val funding = targets.filterNot { it.type.isOption }
            if (funding.isEmpty()) return longGoal
            return funding.all { it.type == BudgetTarget.Type.BY_DATE || it.type == BudgetTarget.Type.SCHEDULE }
        }

    val showsProgressBar: Boolean
        get() = usesGoalProgress || assignedCents != 0L || spentCents != 0L

    val progressState: BudgetProgressState
        get() = when {
            balanceCents < 0L -> BudgetProgressState.OVERSPENT
            balanceCents == 0L && spentCents != 0L -> BudgetProgressState.SPENT
            balanceCents == 0L && assignedCents == 0L && carryoverCents == 0L -> BudgetProgressState.UNASSIGNED
            spentCents != 0L -> BudgetProgressState.SPENDING
            else -> BudgetProgressState.FUNDED
        }

    /**
     * The state that colors this category's progress bar. Ordinary categories use
     * [progressState]; goal, by-date and cover-schedule targets measure the balance against the
     * full target instead, so they get their own customizable goal states (issue #491). A goal
     * only reads as reached once the whole target balance is funded, matching [progressFraction].
     */
    fun progressBarState(schedules: List<BudgetScheduleFunding> = emptyList()): BudgetProgressState {
        if (!usesGoalProgress) return progressState
        if (balanceCents < 0L) return BudgetProgressState.OVERSPENT
        val explicitGoal = effectiveGoalCents(schedules)?.takeIf { it > 0L }
        // Only a real goal can be overshot; the assigned-amount fallback below just reads as reached.
        if (explicitGoal != null && balanceCents > explicitGoal) return BudgetProgressState.GOAL_OVERFUNDED
        val goal = explicitGoal ?: assignedCents.takeIf { it > 0L }
        return if (goal != null && balanceCents >= goal) BudgetProgressState.GOAL_REACHED
        else BudgetProgressState.GOAL_IN_PROGRESS
    }

    /**
     * The state behind the status dot and the budgeted-amount pill. Goal categories
     * ([usesGoalProgress]) share [progressBarState] so the dot, pill and bar always agree
     * (issue #1286); every other category reads [progressState].
     */
    fun statusState(schedules: List<BudgetScheduleFunding> = emptyList()): BudgetProgressState =
        if (usesGoalProgress) progressBarState(schedules) else progressState

    /**
     * What the balance pill's target icon shows (issue #1279), or null for a category without a
     * target. It reuses the progress bar's own rules, so the icon and the bar always agree: only a
     * category whose bar measures the balance against a target ([usesGoalProgress]: a goal, or
     * save-by-date / cover-schedule alone) gets a funding state; any other target, and an
     * overspent category, keeps the plain icon.
     */
    fun targetFunding(schedules: List<BudgetScheduleFunding> = emptyList()): TargetFunding? {
        if (target == null) return null
        if (!usesGoalProgress) return TargetFunding.PLAIN
        return when (progressBarState(schedules)) {
            BudgetProgressState.GOAL_REACHED -> TargetFunding.REACHED
            BudgetProgressState.GOAL_OVERFUNDED -> TargetFunding.OVERFUNDED
            BudgetProgressState.OVERSPENT -> TargetFunding.PLAIN
            else -> if (progressFraction(schedules) > 0f) TargetFunding.PARTIAL else TargetFunding.NOT_STARTED
        }
    }

    fun progressFraction(schedules: List<BudgetScheduleFunding> = emptyList()): Float {
        // Preserve Actua's long-term targets, including unresolved schedule fallback.
        if (usesGoalProgress) {
            val goal = effectiveGoalCents(schedules)
            if (goal != null && goal > 0L) return (balanceCents.toFloat() / goal).coerceIn(0f, 1f)
            return if (assignedCents <= 0L) 0f else (balanceCents.toFloat() / assignedCents).coerceIn(0f, 1f)
        }
        // Actuali spending capacity includes carryover. Convert before abs/addition to
        // avoid Long overflow; these floating-point values are presentation-only.
        val spentAmount = kotlin.math.abs(spentCents.toDouble())
        val capacity = spentAmount + balanceCents.coerceAtLeast(0L).toDouble()
        return if (capacity > 0.0) (spentAmount / capacity).toFloat().coerceIn(0f, 1f) else 0f
    }
}

/** How far a category's target is funded, shown by the balance pill's icon; see [BudgetCategory.targetFunding]. */
enum class TargetFunding { PLAIN, NOT_STARTED, PARTIAL, REACHED, OVERFUNDED }

/**
 * A category's progress-bar state. [label] keeps the English default for callers that have not
 * localized yet; composables resolve [labelRes] instead.
 */
enum class BudgetProgressState(val label: String, @StringRes val labelRes: Int) {
    UNASSIGNED("No money assigned", R.string.budget_progress_unassigned),
    FUNDED("Funded", R.string.budget_progress_funded),
    SPENDING("Partially spent", R.string.budget_progress_partially_spent),
    SPENT("Fully spent", R.string.budget_progress_fully_spent),
    OVERSPENT("Overspent", R.string.budget_progress_overspent),
    GOAL_IN_PROGRESS("Goal in progress", R.string.budget_progress_goal_in_progress),
    GOAL_REACHED("Goal reached", R.string.budget_progress_goal_reached),
    GOAL_OVERFUNDED("Funded past goal", R.string.budget_progress_goal_overfunded),
}

enum class BudgetCategoryView(val label: String, @StringRes val labelRes: Int) {
    ALL("All", R.string.budget_filter_all),
    OVERSPENT("Overspent", R.string.budget_filter_overspent),
    UNDERFUNDED("Underfunded", R.string.budget_filter_underfunded),
    OVERFUNDED("Overfunded", R.string.budget_filter_overfunded),
    MONEY_AVAILABLE("Money Available", R.string.budget_filter_money_available);

    fun matches(category: BudgetCategory, schedules: List<BudgetScheduleFunding> = emptyList()): Boolean = when (this) {
        ALL -> true
        OVERSPENT -> category.balanceCents < 0L
        UNDERFUNDED -> {
            val goal = category.effectiveGoalCents(schedules)
            (goal ?: 0L) > 0L && category.balanceCents < goal!!
        }
        OVERFUNDED -> {
            val goal = category.effectiveGoalCents(schedules)
            (goal ?: 0L) > 0L && category.balanceCents > goal!!
        }
        MONEY_AVAILABLE -> category.balanceCents > 0L
    }

    companion object {
        fun fromLabel(label: String): BudgetCategoryView = entries.find { it.label == label } ?: ALL
    }
}

data class BudgetHistory(val month: String, val assignedCents: Long, val spentCents: Long, val goalCents: Long? = null)

data class BudgetOverview(
    val toBudgetCents: Long?,
    val budgetedCents: Long,
    val spentCents: Long,
    val availableCents: Long,
    val bufferedCents: Long = 0,
    /**
     * Tracking budgets only: Actual's `total-saved` (budgeted income − budgeted expenses) when
     * [projectedSavings], else `real-saved` (income received − spent).
     */
    val savedCents: Long? = null,
    val projectedSavings: Boolean = false,
) {
    /**
     * Which label the lead cell shows; [lead] resolves it to the historical English strings so
     * existing (non-localized) callers and tests keep working. Composables resolve it through
     * string resources, with the To Budget label supplied by the call site (the plan overview
     * says "Ready to Budget", the table overview "To budget").
     */
    enum class LeadKind { TO_BUDGET, PROJECTED_SAVINGS, OVERSPENT, SAVED }

    val leadKind: LeadKind
        get() = when {
            toBudgetCents != null || savedCents == null -> LeadKind.TO_BUDGET
            projectedSavings -> LeadKind.PROJECTED_SAVINGS
            savedCents < 0 -> LeadKind.OVERSPENT
            else -> LeadKind.SAVED
        }

    /** The amount the lead cell shows alongside [leadKind]'s label. */
    val leadValue: Long?
        get() = if (leadKind == LeadKind.TO_BUDGET) toBudgetCents else savedCents

    /**
     * The headline figure: To Budget for envelope budgets; for tracking budgets, the savings figure
     * Actual's mobile budget shows in its place ("Projected savings" for this month and later,
     * "Saved" or "Overspent" before).
     */
    fun lead(toBudgetLabel: String = "To budget"): Pair<String, Long?> = when (leadKind) {
        LeadKind.TO_BUDGET -> toBudgetLabel to leadValue
        LeadKind.PROJECTED_SAVINGS -> "Projected savings" to leadValue
        LeadKind.OVERSPENT -> "Overspent" to leadValue
        LeadKind.SAVED -> "Saved" to leadValue
    }
}

/**
 * The categories Actual totals for a group (`group-budget`, `group-sum-amount`, `group-leftover`):
 * every category in envelope budgets, hidden ones included; only visible ones in tracking budgets.
 * View filters don't change Actual's totals.
 */
fun budgetTotalCategories(group: BudgetGroup, tracking: Boolean): List<BudgetCategory> =
    if (tracking) group.categories.filterNot { it.hidden } else group.categories

data class BudgetGroup(
    val name: String,
    val categories: List<BudgetCategory>,
    val hidden: Boolean = false,
    val isIncome: Boolean = false,
)
