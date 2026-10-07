package com.azimulkabir.actua.model

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual's `simple` template (#854): `#template 50`, `#template 50 up to 100`, `#template up to 100`. */
class SimpleTemplateTest {
    @Test fun notesParseSimpleTemplatesWithPriorityZeroWithoutAPrefix() {
        val parsed = BudgetNoteAutomationParser.parse(
            """
            #template 50
            """.trimIndent(),
        )
        assertTrue(parsed.valid)
        assertEquals(listOf(BudgetTarget(BudgetTarget.Type.FIXED, 5_000, priority = 0, simple = true)), parsed.targets)

        assertEquals(
            BudgetTarget(
                BudgetTarget.Type.FIXED, 5_000, priority = 3, simple = true,
                limitPeriod = BudgetTarget.LimitPeriod.MONTHLY, limitAmountCents = 10_000,
            ),
            BudgetNoteAutomationParser.parse("#template-3 50 up to 100").targets.single(),
        )
        assertEquals(
            BudgetTarget(
                BudgetTarget.Type.FIXED, 0, priority = 0, simple = true,
                limitPeriod = BudgetTarget.LimitPeriod.WEEKLY, limitAmountCents = 2_500,
                limitStartDate = "2026-07-06", limitHold = true,
            ),
            BudgetNoteAutomationParser.parse("#template up to 25 per week starting 2026-07-06 hold").targets.single(),
        )
        assertEquals(
            BudgetTarget.LimitPeriod.DAILY,
            BudgetNoteAutomationParser.parse("#template up to 10 per day").targets.single().limitPeriod,
        )
    }

    @Test fun goalDefSimpleRowsAreReadAndWrittenLosslessly() {
        val monthly = """[{"directive":"template","type":"simple","monthly":50,"priority":1}]"""
        val upTo = """[{"directive":"template","type":"simple","monthly":null,"priority":0,""" +
            """"limit":{"amount":25,"hold":true,"period":"weekly","start":"2026-07-06"}}]"""

        for (raw in listOf(monthly, upTo)) {
            val document = BudgetAutomationDocument.decode(raw, "ui")
            assertEquals(emptyList<String>(), document.unsupportedTypes)
            val encoded = JSONArray(requireNotNull(BudgetAutomationDocument.encode(document.supported))).getJSONObject(0)
            val original = JSONArray(raw).getJSONObject(0)
            assertEquals("simple", encoded.getString("type"))
            assertEquals(original.opt("monthly").toString(), encoded.opt("monthly").toString())
            assertEquals(original.optInt("priority"), encoded.optInt("priority"))
            val limit = original.optJSONObject("limit")
            if (limit == null) assertTrue(encoded.isNull("limit")) else {
                val written = encoded.getJSONObject("limit")
                assertEquals(limit.keys().asSequence().toSet(), written.keys().asSequence().toSet())
                limit.keys().forEach { key -> assertEquals(limit.get(key).toString(), written.get(key).toString()) }
            }
        }
    }

    @Test fun plannerBudgetsTheMonthlyAmountOrTopsUpToTheCap() {
        fun preview(target: BudgetTarget, carryover: Long) = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(category(target, carryover)))), "2026-09", overwriteExisting = true,
        ).changes.singleOrNull()?.proposedCents ?: 0L
        val simple = BudgetTarget(BudgetTarget.Type.FIXED, 5_000, priority = 1, simple = true)
        val capped = simple.copy(limitPeriod = BudgetTarget.LimitPeriod.MONTHLY, limitAmountCents = 6_000)
        val upTo = capped.copy(amountCents = 0, limitAmountCents = 10_000)

        assertEquals(5_000L, preview(simple, carryover = 0))
        assertEquals(3_000L, preview(capped, carryover = 3_000))
        assertEquals(7_000L, preview(upTo, carryover = 3_000))
    }

    @Test fun notesManagedSupportedTemplatesRunWhileUnsupportedOnesDoNot() {
        val notes = BudgetAutomationDocument.decode(
            """[{"directive":"template","type":"simple","monthly":20,"limit":null,"priority":0}]""", "notes",
        )
        fun preview(unsupportedTypes: List<String>) = BudgetTemplatePlanner.preview(
            listOf(BudgetGroup("Living", listOf(category(notes.supported.single(), carryover = 0).copy(
                hasUnsupportedTarget = notes.hasUnsupported,
                automationReadOnly = !notes.editable,
                unsupportedAutomationTypes = unsupportedTypes,
            )))), "2026-09", overwriteExisting = true,
        )

        assertEquals(2_000L, preview(emptyList()).changes.single().proposedCents)
        assertEquals(listOf("Living · Food"), preview(listOf("unknown")).unsupportedCategories)
    }

    private fun category(target: BudgetTarget, carryover: Long) = BudgetCategory(
        name = "Food",
        assigned = 0,
        spent = 0,
        actualAssignedCents = 0,
        id = "food",
        target = target,
        automations = listOf(target),
        availableCents = carryover,
    )
}
