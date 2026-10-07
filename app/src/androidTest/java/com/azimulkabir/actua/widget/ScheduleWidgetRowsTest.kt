package com.azimulkabir.actua.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatusCalculator
import com.azimulkabir.actua.data.schedules.ScheduleWidgetProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleWidgetRowsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = DayDate(2026, 9, 5)

    private fun item(id: String, date: DayDate) = ScheduleListItem(
        ActualScheduleSummary(id, id, null, date, null, null, null, null, null,
            ScheduleAmountOp.APPROXIMATE, null, null, false, false, null, null,
            false, null, null, null),
        ScheduleStatusCalculator.status(date, false, false, null, today),
        null, null,
    )

    @Test fun listsEveryScheduleInThePeriodNotOnlyFour() {
        val entries = ScheduleWidgetProjection.upcoming(
            (0 until 20).map { item("schedule-$it", today.addingDays(it % 14)) }, today, 14,
        )
        val rows = ScheduleWidgetRows(context, widgetId = 1, load = { _, _ -> entries })

        rows.onDataSetChanged()

        assertEquals(20, rows.count)
        (0 until rows.count).forEach { assertNotNull(rows.getViewAt(it)) }
    }
}
