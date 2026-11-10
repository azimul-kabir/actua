package com.azimulkabir.actua.ui.celebrations

/** The pure rules for when a celebration may start. They only read results that are already computed. */
object CelebrationPolicy {
    /**
     * Ready to budget just reached exactly zero from a non-zero amount, in the same month. A change of
     * month, the first value seen after a screen opens, and budgets that track instead of envelope
     * (no Ready to budget at all, a null amount) never celebrate.
     */
    fun readyToBudgetReachedZero(previousMonth: String?, previous: Long?, month: String, current: Long?): Boolean =
        previousMonth == month && previous != null && previous != 0L && current == 0L

    /** Whether to actually play: a supporter on the Play build who left Celebrations on, with animations enabled. */
    fun shouldPlay(available: Boolean, isSupporter: Boolean, enabled: Boolean, animationScale: Float): Boolean =
        available && isSupporter && enabled && animationScale > 0f
}
