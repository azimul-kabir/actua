package com.azimulkabir.actua.data.preferences

import java.time.LocalDate

/**
 * The date a new transaction starts with when "Keep the last transaction date" is on: the date of
 * the last transaction saved from the add form. It lives only in memory, so closing the app (the
 * process ending) resets it to today, and it is never stored or synced.
 */
class RememberedEntryDate {
    @Volatile
    private var lastSaved: LocalDate? = null

    /** The date a new transaction starts with: the remembered one when [enabled], otherwise [today]. */
    fun startDate(enabled: Boolean, today: LocalDate = LocalDate.now()): LocalDate =
        if (enabled) lastSaved ?: today else today

    /** Records the date of a transaction just saved from the add form. A no-op when [enabled] is false. */
    fun saved(enabled: Boolean, date: LocalDate) {
        if (enabled) lastSaved = date
    }

    fun reset() {
        lastSaved = null
    }

    companion object {
        /** Shared for the life of the process, so it survives screen rotation but not closing the app. */
        val shared = RememberedEntryDate()
    }
}
