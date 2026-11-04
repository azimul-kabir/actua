package com.azimulkabir.actua.data.preferences

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RememberedEntryDateTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val earlier = LocalDate.of(2026, 11, 1)

    @Test fun starts_at_today_when_nothing_was_saved() {
        assertEquals(today, RememberedEntryDate().startDate(enabled = true, today = today))
    }

    @Test fun starts_at_today_when_off_even_after_a_save() {
        val remembered = RememberedEntryDate()
        remembered.saved(enabled = false, date = earlier)

        assertEquals(today, remembered.startDate(enabled = false, today = today))
    }

    @Test fun starts_at_the_last_saved_date_when_on() {
        val remembered = RememberedEntryDate()
        remembered.saved(enabled = true, date = earlier)

        assertEquals(earlier, remembered.startDate(enabled = true, today = today))
    }

    @Test fun follows_each_save_in_a_row() {
        val remembered = RememberedEntryDate()
        remembered.saved(enabled = true, date = earlier)
        remembered.saved(enabled = true, date = earlier.plusDays(1))

        assertEquals(earlier.plusDays(1), remembered.startDate(enabled = true, today = today))
    }

    @Test fun turning_the_setting_off_hides_the_remembered_date() {
        val remembered = RememberedEntryDate()
        remembered.saved(enabled = true, date = earlier)

        assertEquals(today, remembered.startDate(enabled = false, today = today))
    }

    @Test fun reset_returns_to_today() {
        val remembered = RememberedEntryDate()
        remembered.saved(enabled = true, date = earlier)
        remembered.reset()

        assertEquals(today, remembered.startDate(enabled = true, today = today))
    }
}
