package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BudgetTargetGoalDefRoundTripTest {
    @Test fun scheduleTargetRoundTripsScheduleIdThroughGoalDef() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "sched-123")
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals(BudgetTarget.Type.SCHEDULE, parsed!!.type)
        assertEquals("sched-123", parsed.scheduleId)
        assertEquals(null, parsed.scheduleName)
    }

    @Test fun scheduleTargetRoundTripsScheduleNameWhenIdIsMissing() {
        val target = BudgetTarget(BudgetTarget.Type.SCHEDULE, priority = 1, scheduleName = "Car insurance")
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals("Car insurance", parsed!!.scheduleName)
    }

    @Test fun automationDocumentRoundTripsAnEditedScheduleLink() {
        val original = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"schedule","priority":1,"scheduleId":"old-id"}]""",
            "ui",
        )
        assertEquals(1, original.supported.size)
        assertEquals("old-id", original.supported.single().scheduleId)

        val relinked = listOf(original.supported.single().copy(scheduleId = "new-id", scheduleName = "Rent"))
        val encoded = BudgetAutomationDocument.encode(relinked)
        val redecoded = BudgetAutomationDocument.decode(encoded, "ui")

        assertEquals(false, redecoded.hasUnsupported)
        assertEquals("new-id", redecoded.supported.single().scheduleId)
        assertEquals("Rent", redecoded.supported.single().scheduleName)
    }

    @Test fun scheduleAdjustmentRoundTripsThroughGoalDef() {
        val target = BudgetTarget(
            BudgetTarget.Type.SCHEDULE, priority = 1, scheduleId = "sched-1",
            adjustmentType = BudgetTarget.AdjustmentType.PERCENT, adjustmentPercent = -10.0,
        )
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals(BudgetTarget.AdjustmentType.PERCENT, parsed!!.adjustmentType)
        assertEquals(-10.0, parsed.adjustmentPercent!!, 0.0001)
        assertEquals(null, parsed.adjustmentAmountCents)
    }

    @Test fun averageAdjustmentRoundTripsThroughGoalDef() {
        val target = BudgetTarget(
            BudgetTarget.Type.HISTORICAL, priority = 1, historicalMode = BudgetTarget.HistoricalMode.AVERAGE,
            adjustmentType = BudgetTarget.AdjustmentType.FIXED, adjustmentAmountCents = 2_500,
        )
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals(BudgetTarget.AdjustmentType.FIXED, parsed!!.adjustmentType)
        assertEquals(2_500L, parsed.adjustmentAmountCents)
        assertEquals(null, parsed.adjustmentPercent)
    }

    @Test fun copyModeDropsAnyAdjustmentUpstreamDoesNotSupport() {
        val target = BudgetTarget(
            BudgetTarget.Type.HISTORICAL, priority = 1, historicalMode = BudgetTarget.HistoricalMode.COPY,
            adjustmentType = BudgetTarget.AdjustmentType.PERCENT, adjustmentPercent = 5.0,
        )
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui")
        assertNotNull(parsed)
        assertEquals(null, parsed!!.adjustmentType)
    }

    @Test fun percentageTargetRoundTripsACustomIncomeCategorySource() {
        val target = BudgetTarget(
            BudgetTarget.Type.PERCENTAGE, priority = 1, percentage = 25, percentageSource = "Freelance income",
        )
        val percentageSources = setOf("available funds", "all income", "Freelance income")
        val parsed = BudgetTarget.fromGoalDef(target.toGoalDef(), "ui", percentageSources)
        assertNotNull(parsed)
        assertEquals("Freelance income", parsed!!.percentageSource)
    }

    @Test fun automationDocumentRoundTripsAClearedScheduleLinkAsRemoval() {
        val original = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"schedule","priority":1,"scheduleId":"old-id"}]""",
            "ui",
        )
        val encoded = BudgetAutomationDocument.encode(emptyList())
        assertEquals(null, encoded)
        assertEquals(1, original.supported.size)
    }

    // #1511: amounts that truncate to one cent less when multiplied by 100 in floating point.
    private val centSensitiveAmounts = listOf("19.99" to 1_999L, "4.35" to 435L, "9.95" to 995L, "0.29" to 29L, "2.01" to 201L)

    /** One goal_def row per template type that carries an amount, and how to read that amount back. */
    private fun templates(amount: String): List<Triple<String, String, (BudgetTarget) -> Long?>> = listOf(
        Triple(
            "periodic",
            """[{"directive":"template","type":"periodic","priority":1,"amount":$amount,"period":{"period":"month","amount":1},"starting":null}]""",
            { it.amountCents },
        ),
        Triple("by", """[{"directive":"template","type":"by","priority":1,"amount":$amount,"month":"2026-12"}]""", { it.amountCents }),
        Triple(
            "spend",
            """[{"directive":"template","type":"spend","priority":1,"amount":$amount,"month":"2026-12","from":"2026-06"}]""",
            { it.amountCents },
        ),
        Triple(
            "limit",
            """[{"directive":"template","type":"limit","priority":null,"amount":$amount,"period":"monthly","hold":false}]""",
            { it.amountCents },
        ),
        Triple("goal", """[{"directive":"goal","type":"goal","amount":$amount}]""", { it.amountCents }),
        Triple(
            "remainder limit",
            """[{"directive":"template","type":"remainder","priority":null,"weight":1,"limit":{"amount":$amount,"period":"monthly","hold":false}}]""",
            { it.limitAmountCents },
        ),
        Triple("simple", """[{"directive":"template","type":"simple","priority":1,"monthly":$amount,"limit":null}]""", { it.amountCents }),
        Triple(
            "simple limit",
            """[{"directive":"template","type":"simple","priority":1,"monthly":null,"limit":{"amount":$amount,"hold":false,"period":"monthly","start":null}}]""",
            { it.limitAmountCents },
        ),
        Triple(
            "schedule adjustment",
            """[{"directive":"template","type":"schedule","priority":1,"scheduleId":"s","adjustmentType":"fixed","adjustment":$amount}]""",
            { it.adjustmentAmountCents },
        ),
        Triple(
            "average adjustment",
            """[{"directive":"template","type":"average","priority":1,"numMonths":3,"adjustmentType":"fixed","adjustment":$amount}]""",
            { it.adjustmentAmountCents },
        ),
    )

    @Test fun everyTemplateAmountParsesToTheRoundedCent() {
        for ((amount, cents) in centSensitiveAmounts) {
            for ((name, goalDef, read) in templates(amount)) {
                val parsed = BudgetTarget.fromGoalDef(goalDef, "ui")
                assertNotNull("$name $amount", parsed)
                assertEquals("$name $amount", cents, read(parsed!!))
            }
        }
    }

    @Test fun decreaseAdjustmentsRoundLikeActual() {
        for ((amount, cents) in centSensitiveAmounts) {
            val parsed = BudgetTarget.fromGoalDef(
                """[{"directive":"template","type":"average","priority":1,"numMonths":3,"adjustmentType":"fixed","adjustment":-$amount}]""",
                "ui",
            )
            assertEquals("-$amount", -cents, parsed!!.adjustmentAmountCents)
        }
    }

    @Test fun parsingThenSavingKeepsEveryTemplateAmount() {
        for ((amount, _) in centSensitiveAmounts) {
            for ((name, goalDef, _) in templates(amount)) {
                val saved = BudgetTarget.fromGoalDef(goalDef, "ui")!!.toGoalDef()
                assertEquals("$name $amount", amount.toDouble(), savedAmount(saved), 0.0)
            }
        }
    }

    @Test fun amountToCentsMatchesLootCoreAmountToInteger() {
        assertEquals(1_999L, BudgetTarget.amountToCents(19.99))
        assertEquals(29L, BudgetTarget.amountToCents(0.29))
        assertEquals(-435L, BudgetTarget.amountToCents(-4.35))
        // JavaScript Math.round sends halves toward positive infinity.
        assertEquals(1L, BudgetTarget.amountToCents(0.005))
        assertEquals(0L, BudgetTarget.amountToCents(-0.005))
    }

    /** The one currency amount a saved row holds, wherever its template type keeps it. */
    private fun savedAmount(goalDef: String): Double {
        val row = org.json.JSONArray(goalDef).getJSONObject(0)
        return when {
            row.has("adjustment") -> row.getDouble("adjustment")
            row.optJSONObject("limit") != null && (!row.has("amount") && (row.isNull("monthly") || !row.has("monthly"))) ->
                row.getJSONObject("limit").getDouble("amount")
            row.has("monthly") && !row.isNull("monthly") -> row.getDouble("monthly")
            else -> row.getDouble("amount")
        }
    }
}
