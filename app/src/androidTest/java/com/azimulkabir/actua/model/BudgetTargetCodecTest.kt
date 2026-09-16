package com.azimulkabir.actua.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetTargetCodecTest {
    @Test fun supportedTargetsRoundTripThroughActualGoalDef() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.BY_DATE, 1_200_000, targetMonth = "2027-09"),
            BudgetTarget(
                BudgetTarget.Type.BY_DATE, 2_000_000, targetMonth = "2026-09",
                allowEarlySpending = true, spendFromMonth = "2026-09",
            ),
            BudgetTarget(BudgetTarget.Type.BY_DATE, 60_000, targetMonth = "2027-09", repeats = true, repeatEvery = 3, repeatAnnual = false),
            BudgetTarget(BudgetTarget.Type.FIXED, 100_000, startingDate = "2026-09-01"),
            BudgetTarget(BudgetTarget.Type.FIXED, 5_000, startingDate = "2026-09-01", period = BudgetTarget.Period.WEEK),
            BudgetTarget(BudgetTarget.Type.FIXED, 40_000, startingDate = "2026-09-01", period = BudgetTarget.Period.YEAR, everyCount = 2),
            BudgetTarget(BudgetTarget.Type.REFILL),
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 6),
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.COPY, historicalMonths = 2),
            BudgetTarget(BudgetTarget.Type.GOAL, 5_000_000),
            BudgetTarget(BudgetTarget.Type.REMAINDER, weight = 3),
            BudgetTarget(BudgetTarget.Type.PERCENTAGE, priority = 2, percentage = 25),
            BudgetTarget(BudgetTarget.Type.PERCENTAGE, priority = 2, percentage = 25, percentagePrevious = true),
            BudgetTarget(BudgetTarget.Type.LIMIT, 50_000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY),
            BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "s1", scheduleName = "Rent", scheduleFull = true),
            BudgetTarget(BudgetTarget.Type.FIXED, 100_000, startingDate = "2026-09-01", note = "For the roof fund"),
        )
        targets.forEach { target ->
            val encoded = target.toGoalDef()
            assertNotNull(JSONArray(encoded))
            assertEquals(target, BudgetTarget.fromGoalDef(encoded, "ui"))
        }
    }

    @Test fun standaloneBalanceCapRoundTripsWithoutARefillSibling() {
        val raw = """[{"directive":"template","type":"limit","amount":500,"period":"monthly","hold":false,"priority":null}]"""
        val document = BudgetAutomationDocument.decode(raw, "ui")

        assertEquals(emptyList<String>(), document.unsupportedTypes)
        assertEquals(true, document.editable)
        assertEquals(false, document.hasUnsupported)
        assertEquals(1, document.supported.size)
        val cap = document.supported.single()
        assertEquals(BudgetTarget.Type.LIMIT, cap.type)
        assertEquals(50_000L, cap.amountCents)
        assertEquals(BudgetTarget.LimitPeriod.MONTHLY, cap.limitPeriod)
        assertEquals(false, cap.limitHold)

        val encoded = requireNotNull(BudgetAutomationDocument.encode(document.supported))
        val rows = JSONArray(encoded)
        assertEquals(1, rows.length())
        assertEquals("limit", rows.getJSONObject(0).getString("type"))
        assertEquals(true, rows.getJSONObject(0).isNull("priority"))
        assertEquals("monthly", rows.getJSONObject(0).getString("period"))
        assertEquals(false, rows.getJSONObject(0).getBoolean("hold"))
    }

    @Test fun advancedBalanceCapsAreEditableAndRoundTripLosslessly() {
        val cases = listOf(
            BudgetTarget(BudgetTarget.Type.LIMIT, 12_345, limitPeriod = BudgetTarget.LimitPeriod.WEEKLY, limitStartDate = "2026-09-01", limitHold = false),
            BudgetTarget(BudgetTarget.Type.LIMIT, 12_345, limitPeriod = BudgetTarget.LimitPeriod.DAILY, limitHold = false),
            BudgetTarget(BudgetTarget.Type.LIMIT, 12_345, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY, limitHold = true),
        )

        cases.forEach { target ->
            val raw = target.toGoalDef()
            val document = BudgetAutomationDocument.decode(raw, "ui")
            assertEquals(listOf(target), document.supported)
            assertEquals(emptyList<String>(), document.unsupportedTypes)
            assertEquals(false, document.hasUnsupported)
            assertEquals(target, BudgetTarget.fromGoalDef(raw, "ui"))
        }
    }

    @Test fun malformedBalanceCapsRemainReadOnly() {
        listOf(
            """[{"directive":"template","type":"limit","amount":500,"period":"weekly","hold":false,"priority":null}]""",
            """[{"directive":"template","type":"limit","amount":500,"period":"quarterly","hold":false,"priority":null}]""",
            """[{"directive":"template","type":"limit","amount":500,"period":"monthly","start":"2026-09-01","hold":false,"priority":null}]""",
        ).forEach { raw ->
            val document = BudgetAutomationDocument.decode(raw, "ui")
            assertEquals(emptyList<BudgetTarget>(), document.supported)
            assertEquals(listOf("limit"), document.unsupportedTypes)
            assertEquals(true, document.hasUnsupported)
        }
    }

    @Test fun balanceCapDoesNotFundLikeRefillAndReleasesOnlyExcessCarryover() {
        val cap = BudgetTarget(BudgetTarget.Type.LIMIT, 50_000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY)
        val category = BudgetCategory(
            name = "Buffer",
            assigned = 0,
            spent = 0,
            actualAssignedCents = 0,
            id = "buffer",
            availableCents = 80_000,
            automations = listOf(cap),
        )

        assertEquals(0L, cap.suggestedBudget(category, "2026-09"))
        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(category))),
            "2026-09",
            overwriteExisting = true,
        )
        assertEquals(
            listOf(BudgetTemplateChange("Plan", "buffer", "Buffer", 0L, -30_000L)),
            preview.changes,
        )
    }

    @Test fun balanceCapHoldRetainsExistingFundsOverTheCap() {
        val cap = BudgetTarget(
            BudgetTarget.Type.LIMIT, 50_000,
            limitPeriod = BudgetTarget.LimitPeriod.MONTHLY,
            limitHold = true,
        )
        val category = BudgetCategory(
            name = "Buffer",
            assigned = 0,
            spent = 0,
            actualAssignedCents = 0,
            id = "buffer",
            availableCents = 80_000,
            automations = listOf(cap),
        )

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(category))),
            "2026-09",
            overwriteExisting = true,
        )
        assertEquals(emptyList<BudgetTemplateChange>(), preview.changes)
    }

    @Test fun dailyAndWeeklyBalanceCapsScaleForTheSelectedMonth() {
        val daily = BudgetTarget(BudgetTarget.Type.LIMIT, 1_000, limitPeriod = BudgetTarget.LimitPeriod.DAILY)
        val weekly = BudgetTarget(
            BudgetTarget.Type.LIMIT, 10_000,
            limitPeriod = BudgetTarget.LimitPeriod.WEEKLY,
            limitStartDate = "2026-09-01",
        )

        assertEquals(30_000L, daily.capForMonth("2026-09"))
        assertEquals(50_000L, weekly.capForMonth("2026-09"))
    }

    @Test fun refillRequiresASiblingBalanceCapToContribute() {
        val refillOnly = BudgetCategory(
            name = "Buffer", assigned = 0, spent = 0, actualAssignedCents = 0, id = "buffer",
            availableCents = 0, automations = listOf(BudgetTarget(BudgetTarget.Type.REFILL)),
        )
        val withCap = refillOnly.copy(
            availableCents = 10_000,
            automations = listOf(
                BudgetTarget(BudgetTarget.Type.REFILL),
                BudgetTarget(BudgetTarget.Type.LIMIT, 50_000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY),
            ),
        )

        val withoutCapPreview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Plan", listOf(refillOnly))), "2026-09")
        assertEquals(emptyList<BudgetTemplateChange>(), withoutCapPreview.changes)

        val withCapPreview = BudgetTemplatePlanner.preview(listOf(BudgetGroup("Plan", listOf(withCap))), "2026-09")
        assertEquals(40_000L, withCapPreview.changes.single().proposedCents)
    }

    @Test fun remainderWithAnEmbeddedLimitRoundTripsAsSupported() {
        val target = BudgetTarget(
            BudgetTarget.Type.REMAINDER,
            weight = 2,
            limitPeriod = BudgetTarget.LimitPeriod.MONTHLY,
            limitAmountCents = 100_000,
            limitHold = true,
        )
        val document = BudgetAutomationDocument.decode(target.toGoalDef(), "ui")

        assertEquals(listOf(target), document.supported)
        assertEquals(emptyList<String>(), document.unsupportedTypes)
        assertEquals(false, document.hasUnsupported)
    }

    @Test fun notesManagedHistoricalDefinitionsRemainEvaluableButReadOnly() {
        val target = BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.COPY, historicalMonths = 2)
        val document = BudgetAutomationDocument.decode(target.toGoalDef(), "notes")

        assertEquals(listOf(target), document.supported)
        assertEquals(emptyList<String>(), document.unsupportedTypes)
        assertEquals(false, document.editable)
        assertEquals(true, document.hasUnsupported)
    }

    @Test fun remainderLimitCodecPreservesAllPeriodsAndWeeklyStart() {
        listOf(
            BudgetTarget.LimitPeriod.DAILY to null,
            BudgetTarget.LimitPeriod.MONTHLY to null,
            BudgetTarget.LimitPeriod.WEEKLY to "2026-09-01",
        ).forEach { (period, start) ->
            val target = BudgetTarget(
                BudgetTarget.Type.REMAINDER,
                weight = 2,
                limitPeriod = period,
                limitAmountCents = 12_345,
                limitStartDate = start,
                limitHold = true,
            )
            assertEquals(target, BudgetTarget.fromGoalDef(target.toGoalDef(), "ui"))
        }
    }

    @Test fun malformedRemainderLimitRemainsReadOnly() {
        val document = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"remainder","priority":null,"weight":1,
                "limit":{"amount":10,"period":"quarterly","hold":false}}]""".replace("\n", ""),
            "ui",
        )

        assertEquals(emptyList<BudgetTarget>(), document.supported)
        assertEquals(true, document.hasUnsupported)
    }

    @Test fun remainderLimitCapsTheShareOfAvailableFunds() {
        val rent = BudgetCategory(
            name = "Rent",
            assigned = 0,
            spent = 0,
            actualAssignedCents = 0,
            id = "rent",
            automations = listOf(BudgetTarget(BudgetTarget.Type.FIXED, 50_000)),
        )
        val savings = BudgetCategory(
            name = "Savings",
            assigned = 0,
            spent = 0,
            actualAssignedCents = 0,
            id = "savings",
            automations = listOf(BudgetTarget(
                BudgetTarget.Type.REMAINDER,
                weight = 1,
                limitPeriod = BudgetTarget.LimitPeriod.MONTHLY,
                limitAmountCents = 30_000,
            )),
        )

        val preview = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Plan", listOf(rent, savings))),
            "2026-09",
            availableBudgetCents = 100_000,
        )

        assertEquals(listOf(50_000L, 30_000L), preview.changes.map { it.proposedCents })
        assertEquals(80_000L, preview.netBudgetChangeCents)
    }

    @Test fun supportedNotesTemplatesDecodeButRemainReadOnly() {
        val fixed = BudgetTarget(BudgetTarget.Type.FIXED, 10_000, startingDate = "2026-09-01")
        assertEquals(fixed, BudgetTarget.fromGoalDef(fixed.toGoalDef(), "notes"))
        assertEquals(null, BudgetTarget.fromGoalDef("[{\"type\":\"percentage\",\"directive\":\"template\"}]", "ui"))
        assertEquals(null, BudgetTarget.fromGoalDef(
            "[{\"type\":\"percentage\",\"directive\":\"template\",\"priority\":1,\"percent\":25,\"category\":\"Salary\"}]",
            "ui",
        ))
    }

    @Test fun multipleSupportedAutomationsRoundTripAsOneGoalDefinition() {
        val targets = listOf(
            BudgetTarget(BudgetTarget.Type.FIXED, 10_000, startingDate = "2026-09-01", priority = 2),
            BudgetTarget(BudgetTarget.Type.BY_DATE, 120_000, targetMonth = "2027-09", priority = 3),
            BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 3),
        )
        val encoded = requireNotNull(BudgetAutomationDocument.encode(targets))
        val decoded = BudgetAutomationDocument.decode(encoded, "ui")
        assertEquals(targets, decoded.supported)
        assertEquals(emptyList<String>(), decoded.unsupportedTypes)
        assertEquals(true, decoded.editable)
    }

    @Test fun advancedAndNotesManagedDocumentsStayLocked() {
        val advanced = BudgetAutomationDocument.decode(
            "[{\"type\":\"periodic\",\"directive\":\"template\",\"priority\":1,\"amount\":100,\"period\":{\"period\":\"month\",\"amount\":1}},{\"type\":\"percentage\",\"directive\":\"template\"}]",
            "ui",
        )
        assertEquals(1, advanced.supported.size)
        assertEquals(listOf("percentage"), advanced.unsupportedTypes)
        assertEquals(true, advanced.hasUnsupported)

        val notes = BudgetAutomationDocument.decode(
            BudgetTarget(BudgetTarget.Type.FIXED, 10_000).toGoalDef(), "notes",
        )
        assertEquals(false, notes.editable)
        assertEquals(true, notes.hasUnsupported)
    }
}
