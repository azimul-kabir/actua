package com.azimulkabir.actua.ui.home

import java.time.LocalDate
import kotlin.random.Random

enum class GreetingPartOfDay {
    Morning, Afternoon, Evening;

    companion object {
        /** 05:00 to 11:59 is morning, 12:00 to 17:59 afternoon, anything else evening. */
        fun forHour(hour: Int): GreetingPartOfDay = when (hour) {
            in 5..11 -> Morning
            in 12..17 -> Afternoon
            else -> Evening
        }
    }
}

/** Trims a typed nickname to what the greeting stores: at most [MAX_NICKNAME_LENGTH] characters. */
object GreetingNickname {
    const val MAX_NICKNAME_LENGTH = 24

    fun clean(raw: String): String = raw.trim().take(MAX_NICKNAME_LENGTH).trim()
}

/** The once-a-day, randomly picked figures of the start-page strip. */
object GreetingStripPolicy {
    /** True when the strip has not yet been shown on [today] (device-local date). */
    fun shouldShow(lastShownDate: String?, today: LocalDate): Boolean = lastShownDate != today.toString()

    /**
     * [figures] in a shuffled order that depends only on [date] and [budgetId], so it differs from day to
     * day but is the same for the whole day, across recompositions and restarts.
     */
    fun dailyOrder(figures: List<GreetingFigure>, date: LocalDate, budgetId: String?): List<GreetingFigure> =
        figures.shuffled(Random("$date|${budgetId.orEmpty()}".hashCode()))
}
