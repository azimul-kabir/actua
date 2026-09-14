package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral coverage for Actua's month-end cleanup engine, faithful reference:
 * `packages/loot-core/src/server/budget/cleanup-template.ts` at commit `2fc69915`.
 */
class CleanupTemplatePlannerTest {
    private fun category(
        name: String,
        id: String,
        assignedCents: Long,
        balanceCents: Long,
        targets: List<CleanupTarget> = emptyList(),
        invalid: Boolean = false,
        carryover: Boolean = false,
    ) = BudgetCategory(
        name = name,
        assigned = 0,
        spent = 0,
        id = id,
        actualAssignedCents = assignedCents,
        availableCents = balanceCents,
        cleanupTargets = targets,
        cleanupInvalid = invalid,
        carryoverEnabled = carryover,
    )

    @Test fun groupScopedSourceFundsGroupScopedSink() {
        val source = category(
            "Paycheck", "src", assignedCents = 0, balanceCents = 10_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = "g1")),
        )
        val sink = category(
            "Vacation", "sink", assignedCents = 0, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source, sink))), mapOf("g1" to "Vacation Fund"), "2026-09", 0L,
        )

        assertEquals(
            setOf("src" to -10_000L, "sink" to 10_000L),
            preview.changes.map { it.categoryId to it.proposedCents }.toSet(),
        )
        assertTrue(preview.warnings.isEmpty())
    }

    @Test fun groupWithoutSinksOrOverspendWarnsAndLeavesSourceUntouched() {
        val source = category(
            "Paycheck", "src", assignedCents = 0, balanceCents = 10_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = "g1")),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source))), mapOf("g1" to "Orphan"), "2026-09", 0L,
        )

        assertEquals(emptyList<CleanupChange>(), preview.changes)
        assertTrue(preview.warnings.single().contains("Orphan"))
    }

    @Test fun groupOverspendIsFundedBeforeSinks() {
        val source = category(
            "Paycheck", "src", assignedCents = 0, balanceCents = 10_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = "g1")),
        )
        val overspent = category(
            "Gas", "over", assignedCents = 0, balanceCents = -3_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.OVERSPEND, groupId = "g1")),
        )
        val sink = category(
            "Vacation", "sink", assignedCents = 0, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source, overspent, sink))), mapOf("g1" to "Vacation Fund"), "2026-09", 0L,
        )

        val byId = preview.changes.associateBy { it.categoryId }
        assertEquals(3_000L, byId.getValue("over").proposedCents)
        assertEquals(7_000L, byId.getValue("sink").proposedCents)
    }

    @Test fun overspentCategoryWithCarryoverIsNotAutoFilled() {
        val source = category(
            "Paycheck", "src", assignedCents = 0, balanceCents = 10_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = "g1")),
        )
        val overspent = category(
            "Credit card", "over", assignedCents = 0, balanceCents = -3_000, carryover = true,
            targets = listOf(CleanupTarget(CleanupTarget.Role.OVERSPEND, groupId = "g1")),
        )
        val sink = category(
            "Vacation", "sink", assignedCents = 0, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source, overspent, sink))), mapOf("g1" to "Vacation Fund"), "2026-09", 0L,
        )

        assertEquals(null, preview.changes.find { it.categoryId == "over" })
        assertEquals(10_000L, preview.changes.single { it.categoryId == "sink" }.proposedCents)
    }

    @Test fun weightedSinksSplitByWeightAndPreserveEveryCent() {
        val source = category(
            "Paycheck", "src", assignedCents = 0, balanceCents = 10L,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = "g1")),
        )
        val sinkA = category(
            "A", "a", assignedCents = 0, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val sinkB = category(
            "B", "b", assignedCents = 0, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val sinkC = category(
            "C", "c", assignedCents = 0, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source, sinkA, sinkB, sinkC))), mapOf("g1" to "Split"), "2026-09", 0L,
        )

        val sinkTotal = preview.changes.filter { it.categoryId != "src" }.sumOf { it.proposedCents }
        assertEquals(10L, sinkTotal)
    }

    @Test fun globalSourceReturnsFundsToToBudgetAndRefreshesGoal() {
        val source = category(
            "Paycheck", "src", assignedCents = 5_000, balanceCents = 5_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = null)),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source))), emptyMap(), "2026-09", 0L,
        )

        assertEquals(0L, preview.changes.single { it.categoryId == "src" }.proposedCents)
        val goalChange = preview.goalChanges.single()
        assertEquals("src", goalChange.categoryId)
        assertEquals(0L, goalChange.proposedCents)
    }

    @Test fun generalOverspendAutoFillDrawsFromSharedPoolAcrossAllCategories() {
        val source = category(
            "Paycheck", "src", assignedCents = 0, balanceCents = 8_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = null)),
        )
        val overspent = category("Gas", "over", assignedCents = 0, balanceCents = -2_000)
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source, overspent))), emptyMap(), "2026-09", 0L,
        )

        assertEquals(2_000L, preview.changes.single { it.categoryId == "over" }.proposedCents)
    }

    @Test fun invalidCleanupDefinitionsAreDisclosedAndLeftUntouched() {
        val category = category("Weird", "weird", assignedCents = 0, balanceCents = 0, invalid = true)
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(category))), emptyMap(), "2026-09", 0L,
        )

        assertTrue(preview.invalidCategories.single().contains("Weird"))
        assertEquals(emptyList<CleanupChange>(), preview.changes)
    }

    @Test fun reapplyingAnUpToDatePreviewIsANoOp() {
        val source = category(
            "Paycheck", "src", assignedCents = -10_000, balanceCents = 0,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = "g1")),
        )
        val sink = category(
            "Vacation", "sink", assignedCents = 10_000, balanceCents = 10_000,
            targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "g1", weight = 1)),
        )
        val preview = CleanupTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(source, sink))), mapOf("g1" to "Vacation Fund"), "2026-09", 0L,
        )

        assertTrue(preview.isUpToDate)
    }
}
