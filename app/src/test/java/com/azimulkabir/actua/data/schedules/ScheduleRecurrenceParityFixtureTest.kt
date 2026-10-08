package com.azimulkabir.actua.data.schedules

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Replays the #670 schedule recurrence fixture through Actua's [ScheduleRecurrence]. The fixture is
 * produced by docs/tools/schedules-fixture/generate.mjs from Actual's own schedules code
 * (@actual-app/api 26.9.0) on a fixed "today" per case, so every expected value is what Actual does:
 * - `nextDate`: the next date `schedule/create` stores (`getNextDate`), which Actua computes with
 *   [ScheduleRecurrence.nextOccurrence];
 * - `skips`: the next dates after six `schedule/skip-next-date` calls, which Actua computes the way
 *   `ActuaRepository.skipScheduleNextDate` does;
 * - `upcoming`: the schedule editor's preview (`schedule/get-upcoming-dates`, 8 dates), which Actua's
 *   editor computes with [ScheduleRecurrence.upcomingDates].
 * A difference fails unless [KNOWN_DIVERGENCES] lists it with its issue; a listed difference that no
 * longer occurs also fails, so the list stays current. Never regenerate the fixture to pass.
 */
class ScheduleRecurrenceParityFixtureTest {
    private val fixture = JSONObject(
        requireNotNull(javaClass.getResource("/schedules-parity/upstream-26.9.0.json")) {
            "Missing fixture: run docs/tools/schedules-fixture/generate.mjs"
        }.readText(),
    )

    @Test
    fun actuaRecurrenceMatchesActualOnEveryFixtureCase() {
        val cases = fixture.getJSONArray("cases").let { array -> (0 until array.length()).map(array::getJSONObject) }
        assertTrue("The fixture has no cases", cases.isNotEmpty())
        val differences = linkedMapOf<String, String>()
        for (case in cases) {
            val name = case.getString("name")
            val today = requireNotNull(DayDate.fromIso(case.getString("today")))
            val config = requireNotNull(RecurConfig.parse(case.getJSONObject("config"))) { "$name: config didn't parse" }

            val next = ScheduleRecurrence.nextOccurrence(config, today)
            compare(differences, "$name nextDate", case.optNullable("nextDate"), next?.iso)

            val skips = case.getJSONArray("skips").strings()
            var current = next
            val actuaSkips = skips.indices.map {
                current = current?.let { date ->
                    // A skip that finds no later occurrence leaves the next date unchanged.
                    ScheduleRecurrence.nextOccurrence(config, ScheduleRecurrence.skipSearchStart(date, config))
                        ?.takeIf { it != date } ?: date
                }
                current?.iso
            }
            compare(differences, "$name skips", skips.joinToString(), actuaSkips.joinToString())

            val upcoming = case.getJSONArray("upcoming").strings()
            compare(
                differences, "$name upcoming", upcoming.joinToString(),
                ScheduleRecurrence.upcomingDates(config, UPCOMING_COUNT, today).joinToString { it.iso },
            )
        }
        val unexpected = differences.filterKeys { it !in KNOWN_DIVERGENCES }
        val stale = KNOWN_DIVERGENCES.keys.filterNot(differences::containsKey)
        if (unexpected.isNotEmpty() || stale.isNotEmpty()) {
            fail(buildString {
                appendLine("Schedule recurrence fixture differences (${cases.size} cases):")
                unexpected.forEach { (key, value) -> appendLine("  NEW  $key: $value") }
                stale.forEach { appendLine("  FIXED (remove from KNOWN_DIVERGENCES)  $it") }
            })
        }
    }

    private fun compare(differences: MutableMap<String, String>, key: String, want: String?, got: String?) {
        if (want != got) differences[key] = "Actual [$want], Actua [$got]"
    }

    private fun JSONObject.optNullable(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

    private companion object {
        /** The count the generator asks `schedule/get-upcoming-dates` for. */
        const val UPCOMING_COUNT = 8

        /** Fixture key → issue tracking the difference. */
        val KNOWN_DIVERGENCES: Map<String, String> = mapOf()
    }
}
