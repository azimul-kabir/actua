package com.azimulkabir.actua.model

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
     * The state behind the status dot and the budgeted-amount pill: [progressState], except a goal
     * category funded past its target reads [BudgetProgressState.GOAL_OVERFUNDED].
     */
    fun statusState(schedules: List<BudgetScheduleFunding> = emptyList()): BudgetProgressState =
        if (usesGoalProgress && progressBarState(schedules) == BudgetProgressState.GOAL_OVERFUNDED)
            BudgetProgressState.GOAL_OVERFUNDED
        else progressState

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

enum class BudgetProgressState(val label: String) {
    UNASSIGNED("No money assigned"),
    FUNDED("Funded"),
    SPENDING("Partially spent"),
    SPENT("Fully spent"),
    OVERSPENT("Overspent"),
    GOAL_IN_PROGRESS("Goal in progress"),
    GOAL_REACHED("Goal reached"),
    GOAL_OVERFUNDED("Funded past goal"),
}

enum class BudgetCategoryView(val label: String) {
    ALL("All"),
    OVERSPENT("Overspent"),
    UNDERFUNDED("Underfunded"),
    OVERFUNDED("Overfunded"),
    MONEY_AVAILABLE("Money Available");

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
)

data class BudgetGroup(
    val name: String,
    val categories: List<BudgetCategory>,
    val hidden: Boolean = false,
    val isIncome: Boolean = false,
)
