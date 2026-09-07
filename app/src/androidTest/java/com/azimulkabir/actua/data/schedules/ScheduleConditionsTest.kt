package com.azimulkabir.actua.data.schedules

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleConditionsTest {
    @Test fun buildAndMergePreserveCustomConditionsAndExistingDateOperator() {
        val existing = JSONArray("""[
          {"op":"contains","field":"notes","value":"rent"},
          {"op":"is","field":"description","value":"old-payee","extra":"keep"},
          {"op":"is","field":"acct","value":"old-account"},
          {"op":"is","field":"date","value":"2026-09-01"},
          {"op":"isapprox","field":"amount","value":1000}
        ]""")
        val owned = ScheduleConditions.build(ScheduleFormFields(payeeId="new-payee", accountId="new-account",
            amount=ScheduledAmount.Range(1100,1300), amountOp=ScheduleAmountOp.BETWEEN,
            date=ScheduleDateCondition.Fixed(DayDate(2026,10,1))), existing)
        val merged = ScheduleConditions.merge(existing, owned)
        assertEquals(5, merged.length())
        assertEquals("rent", merged.getJSONObject(0).getString("value"))
        assertEquals("keep", merged.getJSONObject(1).getString("extra"))
        assertEquals("is", merged.getJSONObject(3).getString("op"))
        assertEquals("2026-10-01", merged.getJSONObject(3).getString("value"))
        assertEquals("isbetween", merged.getJSONObject(4).getString("op"))
    }

    @Test fun actionSyncChangesPlainAmountOnly() {
        val conditions = JSONArray("""[{"op":"is","field":"amount","value":{"num1":-4,"num2":-3}}]""")
        val actions = JSONArray("""[
          {"op":"set","field":"amount","value":99},
          {"op":"set","field":"amount","value":88,"options":{"formula":"x"}},
          {"op":"set-split-amount","field":null,"value":77}
        ]""")
        val result = requireNotNull(ScheduleConditions.syncedActions(conditions, actions))
        assertEquals(-3, result.getJSONObject(0).getLong("value"))
        assertEquals(88, result.getJSONObject(1).getLong("value"))
        assertEquals(77, result.getJSONObject(2).getLong("value"))
    }

    @Test fun noPlainAmountChangeReturnsNull() {
        val conditions = JSONArray("""[{"op":"is","field":"amount","value":100}]""")
        val actions = JSONArray("""[{"op":"set","field":"amount","value":100}]""")
        assertNull(ScheduleConditions.syncedActions(conditions, actions))
    }
}
