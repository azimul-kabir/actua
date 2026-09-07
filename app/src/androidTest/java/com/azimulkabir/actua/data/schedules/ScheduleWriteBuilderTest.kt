package com.azimulkabir.actua.data.schedules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWriteBuilderTest {
    private val fields = ScheduleFormFields("  Rent  ", "payee", "account",
        ScheduledAmount.Fixed(-125_000), ScheduleAmountOp.APPROXIMATE,
        ScheduleDateCondition.Fixed(DayDate(2026,9,1)), true)

    @Test fun createWritesExactActualRows() {
        val plan = ScheduleWriteBuilder.create(fields, "s1", "r1", "nd1", 1_760_000_000_000, DayDate(2026,8,13))
        assertEquals(listOf("rules", "schedules_next_date", "schedules"), plan.writes.map { it.dataset })
        assertTrue(plan.writes[0].fields["actions"].toString().contains("link-schedule"))
        assertEquals(20260901, plan.writes[1].fields["local_next_date"])
        assertEquals(20260901, plan.writes[1].fields["base_next_date"])
        assertEquals("Rent", plan.writes[2].fields["name"])
    }

    @Test fun updatePreservesCustomRuleAndDoesNotResetUnchangedDate() {
        val summary = summary()
        val plan = ScheduleWriteBuilder.update(summary, fields.copy(name="Rent 2"), 200, DayDate(2026,8,13),
            newNextDateRowId={"nd-new"}, newRuleId={"r-new"})
        assertEquals(listOf("rules", "schedules"), plan.writes.map { it.dataset })
        assertTrue(plan.writes[0].fields["conditions"].toString().contains("custom"))
        assertFalse(plan.writes[1].fields.containsKey("rule"))
    }

    @Test fun dateChangeResetsBaseAndDeleteTombstonesRule() {
        val summary = summary()
        val moved = ScheduleWriteBuilder.update(summary,
            fields.copy(date=ScheduleDateCondition.Fixed(DayDate(2026,10,1))), 300, DayDate(2026,8,13),
            newNextDateRowId={"nd-new"}, newRuleId={"r-new"})
        val next = moved.writes.single { it.dataset == "schedules_next_date" }
        assertEquals(20261001, next.fields["base_next_date"])
        assertFalse(next.fields.containsKey("local_next_date"))
        val deleted = ScheduleWriteBuilder.delete(summary)
        assertEquals(listOf("schedules", "rules"), deleted.writes.map { it.dataset })
        assertTrue(deleted.writes.all { it.fields["tombstone"] == 1 })
    }

    @Test fun localSkipPinsCurrentBaseTimestamp() {
        val plan = requireNotNull(ScheduleWriteBuilder.nextDate(summary(), DayDate(2026,11,1), false, 999))
        assertEquals(20261101, plan.writes.single().fields["local_next_date"])
        assertEquals(100L, plan.writes.single().fields["local_next_date_ts"])
        assertNull(plan.writes.single().fields["base_next_date"])
    }

    private fun summary() = ActualScheduleSummary("s1","Rent","r1",DayDate(2026,9,1),"nd1",100,
        "account","payee",ScheduledAmount.Fixed(-125_000),ScheduleAmountOp.APPROXIMATE,"isapprox",
        ScheduleDateCondition.Fixed(DayDate(2026,9,1)),true,false,null,null,true,
        """[{"op":"contains","field":"notes","value":"custom"},{"op":"is","field":"account","value":"account"},{"op":"isapprox","field":"date","value":"2026-09-01"},{"op":"isapprox","field":"amount","value":-125000}]""",
        """[{"op":"link-schedule","value":"s1"}]""",null)
}
