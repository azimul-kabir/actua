package com.azimulkabir.actua.data.schedules

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleRecurrenceParserTest {
    @Test fun parsesAndRunsActualStoredJsonShape() {
        val config = RecurConfig.parse(JSONObject("""{
            "frequency":"monthly","interval":1,"start":"2026-01-01",
            "patterns":[{"type":"day","value":-1},{"type":"MO","value":2}],
            "skipWeekend":true,"weekendSolveMode":"before",
            "endMode":"after_n_occurrences","endOccurrences":6
        }"""))
        assertNotNull(config)
        assertEquals("2026-02-09", ScheduleRecurrence.nextOccurrence(requireNotNull(config), DayDate(2026,2,1))?.iso)
    }

    @Test fun rejectsMalformedStoredShapes() {
        assertNull(RecurConfig.parse(JSONObject("""{"frequency":"hourly","start":"2026-01-01"}""")))
        assertNull(RecurConfig.parse(JSONObject("""{"frequency":"monthly","start":"2026-01-01","interval":"2"}""")))
        assertNull(RecurConfig.parse(JSONObject("""{"frequency":"monthly","start":"2026-01-01","patterns":[{"type":"MO","value":0}]}""")))
        assertNull(RecurConfig.parse(JSONObject("""{"frequency":"monthly","start":"2026-01-01","endMode":"on_date"}""")))
    }
}
