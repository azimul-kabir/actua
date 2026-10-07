package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTemplatePlannerTest {
    @Test fun previewsOnlyChangedSupportedTargets() {
        val groups = listOf(BudgetGroup("Living", listOf(
            category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
                BudgetTarget.Type.FIXED, 100_000,
            )),
            category("food", "Food", assigned = 30_000, target = BudgetTarget(
                BudgetTarget.Type.FIXED, 30_000,
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
            BudgetTarget.Type.FIXED, 100_000,
        ))
        val first = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(original))), "2026-09", overwriteExisting = true,
        )
        val applied = category(
            "rent", "Rent", first.changes.single().proposedCents,
            BudgetTarget(BudgetTarget.Type.FIXED, 100_000),
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

    // #856: Actual's runBy rolls a passed repeating target forward by its period.
    @Test fun passedAnnualTargetRollsForwardInsteadOfAskingForTheWholeAmount() {
        val target = BudgetTarget(
            BudgetTarget.Type.BY_DATE, 120_000, targetMonth = "2026-06", repeats = true, repeatAnnual = true,
        )
        val category = category("trip", "Trip", assigned = 0, automations = listOf(target))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-08")

        // Rolled to 2027-06: 1,200.00 / 11 months.
        assertEquals(10_909L, preview.changes.single().proposedCents)
    }

    @Test fun repeatingSiblingDueLaterIsInterpolatedOverItsPeriod() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.BY_DATE, 30_000, targetMonth = "2026-10"),
            BudgetTarget(BudgetTarget.Type.BY_DATE, 120_000, targetMonth = "2027-03", repeats = true, repeatAnnual = true),
        )
        val category = category("bills", "Bills", assigned = 0, automations = targets)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        // Shortest window 1 month; the annual one needs 1,200.00 / 12 * (12 - 6 + 1) = 700.00.
        // (300.00 + 700.00) / 2 = 500.00.
        assertEquals(50_000L, preview.changes.single().proposedCents)
    }

    @Test fun passedNonRepeatingTargetIsReportedInsteadOfBudgeted() {
        val target = BudgetTarget(BudgetTarget.Type.BY_DATE, 60_000, targetMonth = "2026-06")
        val category = category("trip", "Trip", assigned = 0, automations = listOf(target))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-08")

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(listOf("Goals · Trip"), preview.unsupportedCategories)
    }

    @Test fun refillCapsOtherContributionsInTheSameCategory() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.FIXED, 80_000),
            BudgetTarget(BudgetTarget.Type.LIMIT, 100_000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY),
            BudgetTarget(BudgetTarget.Type.REFILL),
        )
        val category = category("buffer", "Buffer", assigned = 0, automations = targets, carryover = 25_000)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        assertEquals(75_000, preview.changes.single().proposedCents)
    }

    @Test fun refillTopsUpToTheWeeklyCapScaledToTheMonth() {
        // Regression for #858: August 2026 has five Mondays from the 2026-07-06 start.
        val category = category("coffee", "Coffee", assigned = 0, automations = weeklyRefill)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Fun", listOf(category))), "2026-08")

        assertEquals(12_500L, preview.changes.single().proposedCents)
    }

    @Test fun refillReleasesCarryoverAboveTheScaledWeeklyCap() {
        // September 2026 has four Mondays, so a 125.00 carryover is 25.00 over the cap.
        val category = category("coffee", "Coffee", assigned = 0, automations = weeklyRefill, carryover = 12_500)

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Fun", listOf(category))), "2026-09")

        assertEquals(-2_500L, preview.changes.single().proposedCents)
    }

    private val weeklyRefill = listOf(
        BudgetTarget(
            BudgetTarget.Type.LIMIT, 2_500,
            limitPeriod = BudgetTarget.LimitPeriod.WEEKLY, limitStartDate = "2026-07-06",
        ),
        BudgetTarget(BudgetTarget.Type.REFILL),
    )

    @Test fun percentageOfPreviousMonthUsesLastMonthsIncome() {
        // Regression for #859: `3% of previous Salary` budgeted nothing.
        val category = category("savings", "Savings", assigned = 0, automations = listOf(
            BudgetTarget(
                BudgetTarget.Type.PERCENTAGE, priority = 1, percentage = 3,
                percentageSource = "Salary", percentagePrevious = true,
            ),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category)), income), "2026-09")

        assertEquals(listOf(4_500L), preview.changes.map { it.proposedCents })
    }

    @Test fun percentageOfPreviousAllIncomeSumsLastMonthsIncomeCategories() {
        val category = category("savings", "Savings", assigned = 0, automations = listOf(
            BudgetTarget(
                BudgetTarget.Type.PERCENTAGE, priority = 1, percentage = 10,
                percentageSource = "all income", percentagePrevious = true,
            ),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category)), income), "2026-09")

        assertEquals(listOf(17_000L), preview.changes.map { it.proposedCents })
    }

    private val income = BudgetGroup(
        "Income",
        listOf(
            incomeCategory("salary", "Salary", previous = 150_000, current = 900_000),
            incomeCategory("bonus", "Bonus", previous = 20_000, current = 0),
        ),
        isIncome = true,
    )

    private fun incomeCategory(id: String, name: String, previous: Long, current: Long) = BudgetCategory(
        name = name, assigned = 0, spent = 0, actualAssignedCents = 0, id = id,
        availableCents = current, isIncome = true,
        history = listOf(BudgetHistory("2026-08", 0, previous)),
    )

    @Test fun hideFractionRoundsEachPriorityToWholeUnits() {
        // Regression for #861: Actual's synced `hideFraction` rounds template amounts.
        val up = category("up", "Up", assigned = 0, automations = listOf(BudgetTarget(BudgetTarget.Type.FIXED, 10_050)))
        val down = category("down", "Down", assigned = 0, automations = listOf(BudgetTarget(BudgetTarget.Type.FIXED, 10_049)))

        val rounded = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Bills", listOf(up, down))), "2026-09", hideFraction = true)
        val exact = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Bills", listOf(up, down))), "2026-09")

        assertEquals(listOf(10_100L, 10_000L), rounded.changes.map { it.proposedCents })
        assertEquals(listOf(10_050L, 10_049L), exact.changes.map { it.proposedCents })
    }

    @Test fun hideFractionGivesTheLastWholeUnitOfARemainderToTheFinalCategory() {
        val categories = (1..3).map { index ->
            category("r$index", "Remainder $index", assigned = 0, automations = listOf(
                BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1),
            ))
        }

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Fun", categories)), "2026-09", availableBudgetCents = 10_000, hideFraction = true,
        )

        assertEquals(listOf(3_300L, 3_300L, 3_400L), preview.changes.map { it.proposedCents })
    }

    @Test fun fundsHigherPrioritiesFirstAndReportsAvailableFundsClamp() {
        val first = category("rent", "Rent", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.FIXED, 80_000, priority = 1),
        ))
        val second = category("fun", "Fun", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.FIXED, 50_000, priority = 2),
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
            BudgetTarget(BudgetTarget.Type.FIXED, 50_000, priority = 1),
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
            BudgetTarget(BudgetTarget.Type.FIXED, 60_000, priority = 2),
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

    @Test fun scheduleFundingAppliesAPercentDecreaseAdjustment() {
        val schedule = category("schedule", "Scheduled bill", assigned = 0, automations = listOf(
            BudgetTarget(
                BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "bill-1",
                adjustmentType = BudgetTarget.AdjustmentType.PERCENT, adjustmentPercent = -10.0,
            ),
        ))

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(schedule))),
            "2026-09",
            schedules = listOf(BudgetScheduleFunding(
                id = "bill-1", name = "Rent", amountCents = 50_000,
                occurrencesInMonth = 1, monthsUntilNextOccurrence = 0,
            )),
        )

        assertEquals(listOf(45_000L), preview.changes.map { it.proposedCents })
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
            BudgetTarget.Type.FIXED, 100_000,
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Living", listOf(category))), "2026-09")

        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
        assertEquals(1, preview.skippedExistingCount)
        assertEquals(false, preview.overwriteExisting)
    }

    @Test fun overwriteReturnsExistingTemplateFundsBeforeRecalculation() {
        val category = category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
            BudgetTarget.Type.FIXED, 100_000,
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
        assertEquals(true, preview.goalChanges.single().longGoal)
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

    // #853: Actual writes this month's full request as the goal, with long_goal null, for every
    // template type except #goal (category-template-context.ts runGoal).
    @Test fun byDateGoalIsThisMonthsRequestNotTheFullTarget() {
        val category = category("trip", "Trip fund", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.BY_DATE, 1_200_00, targetMonth = "2027-09"),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Goals", listOf(category))), "2026-09")

        val goal = preview.goalChanges.single()
        assertEquals(preview.changes.single().proposedCents, goal.proposedCents)
        assertEquals(false, goal.longGoal)
    }

    @Test fun goalIsTheRequestBeforeTheAvailableFundsClamp() {
        val category = category("rent", "Rent", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.FIXED, 60_000),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Living", listOf(category))), "2026-09", 20_000)

        assertEquals(20_000L, preview.changes.single().proposedCents)
        assertEquals(60_000L, preview.goalChanges.single().proposedCents)
    }

    @Test fun remainderOnlyCategoriesGetNoGoal() {
        val category = category("fun", "Fun", assigned = 0, goal = 1_000, automations = listOf(
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 1),
        ))

        val preview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Living", listOf(category))), "2026-09", 5_000)

        assertEquals(null, preview.goalChanges.single().proposedCents)
    }

    @Test fun scheduleGoalIsThisMonthsRequest() {
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

        assertEquals(preview.changes.single().proposedCents, preview.goalChanges.single().proposedCents)
        assertEquals(false, preview.goalChanges.single().longGoal)
    }

    @Test fun distributesRemainingFundsByWeightAfterPriorities() {
        val fixed = category("rent", "Rent", assigned = 0, automations = listOf(
            BudgetTarget(BudgetTarget.Type.FIXED, 60_000),
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
            automations = listOf(
                BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.COPY, historicalMonths = 1),
            ),
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

    @Test fun scopingToASingleGroupLeavesOtherGroupsUntouched() {
        val living = BudgetGroup("Living", listOf(
            category("rent", "Rent", assigned = 80_000, target = BudgetTarget(
                BudgetTarget.Type.FIXED, 100_000,
            )),
        ))
        val bills = BudgetGroup("Bills", listOf(
            category("power", "Power", assigned = 0, target = BudgetTarget(
                BudgetTarget.Type.FIXED, 15_000,
            )),
        ))

        // The group-scoped "apply/overwrite templates" action passes only the long-pressed
        // group's list entry; the planner must not reach into the other group's categories.
        val preview = BudgetTemplatePlanner.preview(listOf(bills), "2026-09", overwriteExisting = true)

        assertEquals(1, preview.changes.size)
        assertEquals("power", preview.changes.single().categoryId)
        assertEquals("Bills", preview.changes.single().groupName)
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
