package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTemplatePlannerTest {
    @Test fun previewsOnlyChangedSupportedTargets() {
        val groups = listOf(BudgetGroup("Living", listOf(
            category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
                BudgetTarget.Type.MONTHLY_SAVINGS, 100_000,
            )),
            category("food", "Food", assigned = 30_000, target = BudgetTarget(
                BudgetTarget.Type.MONTHLY_SAVINGS, 30_000,
            )),
            category("advanced", "Advanced", assigned = 0, unsupported = true),
        )))

        val preview = BudgetTemplatePlanner.preview(groups, "2026-09", overwriteExisting = true)

        assertEquals(1, preview.changes.size)
        assertEquals("rent", preview.changes.single().categoryId)
        assertEquals(20_000, preview.netBudgetChangeCents)
        assertEquals(1, preview.unchangedCount)
        assertEquals(listOf("Living · Advanced"), preview.unsupportedCategories)
    }

    @Test fun repeatedApplicationProducesAnEmptyPreview() {
        val original = category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
            BudgetTarget.Type.MONTHLY_SAVINGS, 100_000,
        ))
        val first = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(original))), "2026-09", overwriteExisting = true,
        )
        val applied = category(
            "rent", "Rent", first.changes.single().proposedCents,
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 100_000),
        )

        val second = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(applied))), "2026-09", overwriteExisting = true,
        )

        assertEquals(emptyList<BudgetTemplateChange>(), second.changes)
        assertEquals(1, second.unchangedCount)
        assertEquals(0, second.netBudgetChangeCents)
    }

    @Test fun combinesSupportedAutomationsAndDeductsCarryoverOnceForByDateTargets() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.BY_DATE, 60_000, targetMonth = "2026-10"),
            BudgetTarget(BudgetTarget.Type.BY_DATE, 90_000, targetMonth = "2026-11"),
        )
        val category = category("trip", "Trip", assigned = 0, automations = targets, carryover = 30_000)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        // Actual batches sibling `by` rows. The shorter window is two months:
        // (60,000 + 60,000 interpolated - 30,000 carryover) / 2 = 45,000.
        assertEquals(45_000, preview.changes.single().proposedCents)
        assertEquals(emptyList<String>(), preview.unsupportedCategories)
    }

    @Test fun refillCapsOtherContributionsInTheSameCategory() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 80_000),
            BudgetTarget(BudgetTarget.Type.REFILL, 100_000),
        )
        val category = category("buffer", "Buffer", assigned = 0, automations = targets, carryover = 25_000)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        assertEquals(75_000, preview.changes.single().proposedCents)
    }

    @Test fun fundsHigherPrioritiesFirstAndReportsAvailableFundsClamp() {
        val first = category("rent", "Rent", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 80_000, priority = 1),
        ))
        val second = category("fun", "Fun", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 50_000, priority = 2),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(first, second))),
            "2026-09",
            availableBudgetCents = 100_000,
        )

        assertEquals(listOf(80_000L, 20_000L), preview.changes.map { it.proposedCents })
        assertEquals(listOf("Living · Fun"), preview.limitedCategories)
    }

    @Test fun percentageUsesFundsAvailableAtPriorityStart() {
        val percentage = category("percent", "Percent", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.PERCENTAGE, priority = 1, percentage = 25),
        ))
        val fixed = category("fixed", "Fixed", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 50_000, priority = 1),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(percentage, fixed))),
            "2026-09",
            availableBudgetCents = 100_000,
        )

        assertEquals(listOf(25_000L, 50_000L), preview.changes.map { it.proposedCents })
    }

    @Test fun scheduleFundingUsesResolvedScheduleAndParticipatesInPriorityClamp() {
        val schedule = category("schedule", "Scheduled bill", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "bill-1"),
        ))
        val fixed = category("fixed", "Fixed", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 60_000, priority = 2),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(schedule, fixed))),
            "2026-09",
            availableBudgetCents = 100_000,
            schedules = listOf(BudgetScheduleFunding(
                id = "bill-1", name = "Rent", amountCents = 50_000,
                occurrencesInMonth = 1, monthsUntilNextOccurrence = 0,
            )),
        )

        assertEquals(listOf(50_000L, 50_000L), preview.changes.map { it.proposedCents })
        assertEquals(listOf("Plan · Fixed"), preview.limitedCategories)
    }

    @Test fun scheduleFundingSupportsCrossYearMonthDistance() {
        val funding = BudgetScheduleFunding(
            id = "annual", name = "Annual bill", amountCents = 120_000,
            occurrencesInMonth = 0, monthsUntilNextOccurrence = 4,
        )

        assertEquals(30_000L, funding.requestedBudget(0))
    }

    @Test fun unresolvedScheduleRemainsReadOnlyInMixedDocument() {
        val schedule = category("schedule", "Scheduled bill", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleName = "Missing"),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(schedule))),
            "2026-09",
            availableBudgetCents = 100_000,
        )

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(listOf("Plan · Scheduled bill"), preview.unsupportedCategories)
    }

    @Test fun percentageUsesExactIncomeCategorySource() {
        val income = BudgetCategory(
            name = "Salary", assigned = 0, spent = 0, id = "income-1",
            availableCents = 400_000, isIncome = true, spentCents = 0,
        )
        val expense = category("giving", "Giving", assigned = 0, automations = listOf(
            BudgetTarget(
                BudgetTarget.Type.PERCENTAGE, priority = 1, percentage = 10,
                percentageSource = "income-1",
            ),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Income", listOf(income), isIncome = true),
                BudgetGroup("Plan", listOf(expense))),
            "2026-09",
            availableBudgetCents = 100_000,
        )

        assertEquals(40_000L, preview.changes.single().proposedCents)
    }

    @Test fun normalApplyLeavesExistingBudgetAmountsUntouched() {
        val category = category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
            BudgetTarget.Type.MONTHLY_SAVINGS, 100_000,
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Living", listOf(category))), "2026-09")

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(1, preview.skippedExistingCount)
        assertEquals(false, preview.overwriteExisting)
    }

    @Test fun overwriteReturnsExistingTemplateFundsBeforeRecalculation() {
        val category = category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
            BudgetTarget.Type.MONTHLY_SAVINGS, 100_000,
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(category))),
            "2026-09",
            availableBudgetCents = 20_000,
            overwriteExisting = true,
        )

        assertEquals(100_000, preview.changes.single().proposedCents)
        assertEquals(emptyList<String>(), preview.limitedCategories)
        assertEquals(true, preview.overwriteExisting)
    }

    @Test fun goalOnlyAutomationChangesGoalWithoutBudgetingFunds() {
        val category = category("home", "Home", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.GOAL, 5_000_000),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(5_000_000L, preview.goalChanges.single().proposedCents)
    }

    @Test fun clearsAnOrphanedGoalAfterItsDefinitionIsRemoved() {
        val category = category("home", "Home", assigned = 0, goal = 5_000_000, longGoal = true)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        assertEquals(null, preview.goalChanges.single().proposedCents)
    }

    @Test fun repeatedGoalApplicationIsANoOp() {
        val category = category(
            "home", "Home", assigned = 0,
            automations = listOf(BudgetTarget(BudgetTarget.Type.GOAL, 5_000_000)),
            goal = 5_000_000, longGoal = true,
        )

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        assertEquals(emptyList<BudgetGoalChange>(), preview.goalChanges)
    }

    @Test fun byDateGoalTracksTheFullTargetNotTheMonthlyInstallment() {
        val category = category("trip", "Trip fund", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.BY_DATE, 1_200_00, targetMonth = "2027-09"),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        assertEquals(1_200_00L, preview.goalChanges.single().proposedCents)
    }

    @Test fun scheduleGoalTracksTheResolvedScheduleAmount() {
        val category = category("gift", "Gift", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "gift-1"),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Goals", listOf(category))),
            "2026-09",
            schedules = listOf(BudgetScheduleFunding(
                id = "gift-1", name = "Birthday gift", amountCents = 30_000,
                occurrencesInMonth = 0, monthsUntilNextOccurrence = 3,
            )),
        )

        assertEquals(30_000L, preview.goalChanges.single().proposedCents)
    }

    @Test fun distributesRemainingFundsByWeightAfterPriorities() {
        val fixed = category("rent", "Rent", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, 60_000),
        ))
        val first = category("fun", "Fun", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1),
        ))
        val second = category("saving", "Saving", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 3),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(fixed, first, second))),
            "2026-09",
            availableBudgetCents = 100_000,
        )

        assertEquals(listOf(60_000L, 10_000L, 30_000L), preview.changes.map { it.proposedCents })
        assertEquals(100_000L, preview.changes.sumOf { it.proposedCents })
    }

    @Test fun remainderRoundingAllocatesEveryAvailableCent() {
        val categories = (1..3).map { index ->
            category("c$index", "Category $index", assigned = 0, automations = listOf(
                BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1),
            ))
        }

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", categories)), "2026-09", availableBudgetCents = 100,
        )

        assertEquals(listOf(33L, 33L, 34L), preview.changes.map { it.proposedCents })
        assertEquals(100L, preview.netBudgetChangeCents)
    }

    @Test fun repeatedRemainderOverwriteIsANoOp() {
        val category = category("saving", "Saving", assigned = 100_000, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(category))), "2026-09",
            availableBudgetCents = 0, overwriteExisting = true,
        )

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(1, preview.unchangedCount)
    }

    @Test fun copyTemplatePlansTheAssignedBudgetFromHistory() {
        val category = category(
            "copy", "Copy", assigned = 0,
            automations = listOf(BudgetTarget(BudgetTarget.Type.COPY, lookBackMonths = 1)),
        ).copy(history = listOf(BudgetHistory("2026-08", 42_500, -12_000)))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(category))), "2026-09",
        )

        assertEquals(42_500L, preview.changes.single().proposedCents)
    }

    @Test fun dailyAndWeeklyRemainderLimitsUseCalendarOccurrences() {
        val daily = category("daily", "Daily", 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1,
                limitPeriod = BudgetTarget.LimitPeriod.DAILY, limitAmountCents = 100),
        ))
        val weekly = category("weekly", "Weekly", 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1,
                limitPeriod = BudgetTarget.LimitPeriod.WEEKLY, limitAmountCents = 1_000,
                limitStartDate = "2026-09-01"),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(daily, weekly))),
            "2026-09",
            availableBudgetCents = 10_000,
        )

        assertEquals(listOf(3_000L, 5_000L), preview.changes.map { it.proposedCents })
        assertEquals(listOf("Plan · Daily", "Plan · Weekly"), preview.cappedCategories)
    }

    @Test fun excessCarryoverIsReleasedOnlyWhenHoldIsFalse() {
        val release = category("release", "Release", 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1,
                limitPeriod = BudgetTarget.LimitPeriod.MONTHLY, limitAmountCents = 10_000),
        ), carryover = 20_000)
        val hold = category("hold", "Hold", 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1,
                limitPeriod = BudgetTarget.LimitPeriod.MONTHLY, limitAmountCents = 10_000,
                limitHold = true),
        ), carryover = 20_000)

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(release, hold))),
            "2026-09",
            availableBudgetCents = 0,
        )

        assertEquals(-10_000L, preview.changes.single { it.categoryId == "release" }.proposedCents)
        assertEquals(1, preview.unchangedCount)
    }

    private fun category(
        id: String,
        name: String,
        assigned: Long,
        target: BudgetTarget? = null,
        unsupported: Boolean = false,
        automations: List<BudgetTarget> = target?.let(::listOf).orEmpty(),
        carryover: Long = 0,
        goal: Long? = null,
        longGoal: Boolean = false,
    ) = BudgetCategory(
        name = name,
        assigned = (assigned / 100).toInt(),
        spent = 0,
        actualAssignedCents = assigned,
        id = id,
        target = target,
        hasUnsupportedTarget = unsupported,
        automations = automations,
        availableCents = carryover,
        goalCents = goal,
        longGoal = longGoal,
    )
}
