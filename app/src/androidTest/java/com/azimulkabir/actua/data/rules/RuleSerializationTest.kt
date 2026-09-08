package com.azimulkabir.actua.data.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JSON uses Android's real org.json implementation, so this belongs in the device suite. */
class RuleSerializationTest {
    @Test fun ruleSerializationRoundTripsMappedFieldsValuesAndOptions() {
        val original = Rule(
            id = "round-trip",
            stage = Rule.Stage.POST,
            conditionsOp = Rule.ConditionsOp.OR,
            conditions = listOf(
                Rule.Condition("oneOf", "account", RuleValue.ListValue(listOf(
                    RuleValue.Text("checking"), RuleValue.Text("savings"),
                ))),
                Rule.Condition("isbetween", "amount", RuleValue.ObjectValue(mapOf(
                    "num1" to RuleValue.Number(100.0), "num2" to RuleValue.Number(500.5),
                )), mapOf("outflow" to RuleValue.Flag(true))),
            ),
            actions = listOf(
                Rule.Action("set", "payee", RuleValue.Text("coffee")),
                Rule.Action("set", "category", RuleValue.Null),
            ),
        )

        val decoded = Rule.parse(
            original.id, original.storedStage, original.conditionsOp.name.lowercase(),
            original.conditionsJson, original.actionsJson,
        )

        assertEquals(original, decoded)
        assertTrue(original.conditionsJson.contains("\"field\":\"acct\""))
        assertTrue(original.actionsJson.contains("\"field\":\"description\""))
    }
}
