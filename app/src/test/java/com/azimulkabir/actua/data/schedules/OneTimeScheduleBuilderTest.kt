package com.azimulkabir.actua.data.schedules

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual's `createSingleTimeScheduleFromTransaction` (`util/schedule-actions.ts` at 59fe126f). */
class OneTimeScheduleBuilderTest {
    private val date = DayDate(2026, 11, 5)

    private fun plan(source: OneTimeScheduleSource) =
        ScheduleWriteBuilder.createOneTime(source, "sched", "rule", "next", now = 1_700_000_000_123L)

    private fun fields(plan: ScheduleWritePlan, dataset: String) =
        plan.writes.single { it.dataset == dataset }.fields

    @Test
    fun conditionsAreDateAmountPayeeAccountAllUsingIs() {
        val plan = plan(OneTimeScheduleSource(date, -1_250, "payee-1", "acct-1", null, null))
        val conditions = JSONArray(fields(plan, "rules")["conditions"] as String)

        assertEquals(
            listOf(
                Triple("is", "date", "2026-11-05"), Triple("is", "amount", -1250), Triple("is", "payee", "payee-1"),
                Triple("is", "account", "acct-1"),
            ),
            (0 until conditions.length()).map {
                val c = conditions.getJSONObject(it); Triple(c.getString("op"), c.getString("field"), c.get("value"))
            },
        )
    }

    @Test
    fun aMissingPayeeLeavesOutThePayeeCondition() {
        val plan = plan(OneTimeScheduleSource(date, 500, null, "acct-1", null, null))
        val conditions = JSONArray(fields(plan, "rules")["conditions"] as String)

        assertEquals(listOf("date", "amount", "account"), (0 until conditions.length()).map { conditions.getJSONObject(it).getString("field") })
    }

    @Test
    fun scheduleRowsMirrorScheduleCreateAndStartAtTheTransactionDate() {
        val plan = plan(OneTimeScheduleSource(date, -1_250, "payee-1", "acct-1", "cat-1", "rent"))

        val schedule = fields(plan, "schedules")
        assertEquals("rule", schedule["rule"])
        assertEquals(1, schedule["posts_transaction"])
        assertEquals(0, schedule["completed"])
        assertEquals(0, schedule["tombstone"])
        assertEquals("Auto-created future transaction (Nov 05, 2026) - 1700000000123", schedule["name"])
        val next = fields(plan, "schedules_next_date")
        assertEquals("sched", next["schedule_id"])
        assertEquals(20261105, next["local_next_date"])
        assertEquals(20261105, next["base_next_date"])
        assertEquals(1_700_000_000_123L, next["base_next_date_ts"])
        val rule = fields(plan, "rules")
        assertEquals("and", rule["conditions_op"])
        assertNull(rule["stage"])
    }

    @Test
    fun plainTransactionCarriesCategoryThenNotesAfterTheLinkAction() {
        val plan = plan(OneTimeScheduleSource(date, -1_250, "payee-1", "acct-1", "cat-1", "rent"))
        val actions = JSONArray(fields(plan, "rules")["actions"] as String)

        assertEquals(3, actions.length())
        assertEquals("link-schedule", actions.getJSONObject(0).getString("op"))
        assertEquals("sched", actions.getJSONObject(0).getString("value"))
        assertEquals("category", actions.getJSONObject(1).getString("field"))
        assertEquals("cat-1", actions.getJSONObject(1).getString("value"))
        assertEquals("notes", actions.getJSONObject(2).getString("field"))
        assertEquals("rent", actions.getJSONObject(2).getString("value"))
    }

    @Test
    fun emptyCategoryAndNotesAddNoActions() {
        val plan = plan(OneTimeScheduleSource(date, 100, null, "acct-1", "", ""))
        assertEquals(1, JSONArray(fields(plan, "rules")["actions"] as String).length())
    }

    @Test
    fun splitParentWritesPerLineAmountCategoryAndNotesWithSplitIndex() {
        val plan = plan(
            OneTimeScheduleSource(
                date, -1_000, null, "acct-1", null, "shared",
                splits = listOf(
                    OneTimeScheduleSource.Split(-600, "cat-1", "first"),
                    OneTimeScheduleSource.Split(-400, null, null),
                ),
            ),
        )
        val actions = JSONArray(fields(plan, "rules")["actions"] as String)
        val list = (0 until actions.length()).map { actions.getJSONObject(it) }

        assertEquals("link-schedule", list[0].getString("op"))
        // Parent notes first, at splitIndex 0.
        assertEquals("notes", list[1].getString("field"))
        assertEquals(0, list[1].getJSONObject("options").getInt("splitIndex"))
        assertEquals("set-split-amount", list[2].getString("op"))
        assertEquals(-600L, list[2].getLong("value"))
        assertEquals(1, list[2].getJSONObject("options").getInt("splitIndex"))
        assertEquals("fixed-amount", list[2].getJSONObject("options").getString("method"))
        assertEquals("category", list[3].getString("field"))
        assertEquals(1, list[3].getJSONObject("options").getInt("splitIndex"))
        assertEquals("notes", list[4].getString("field"))
        assertEquals("first", list[4].getString("value"))
        assertEquals("set-split-amount", list[5].getString("op"))
        assertEquals(-400L, list[5].getLong("value"))
        assertEquals(2, list[5].getJSONObject("options").getInt("splitIndex"))
        // The second line has no category or notes, so nothing more is added.
        assertEquals(6, list.size)
        // Category and the split lines are not written as plain top-level actions.
        assertTrue(list.none { it.optString("field") == "category" && !it.has("options") })
    }
}
