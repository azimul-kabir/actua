package com.azimulkabir.actua.billing

import android.content.Context
import com.azimulkabir.actua.data.preferences.SupporterPerks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val PLAY_ANSWER_TIMEOUT_MILLIS = 30_000L

/**
 * App-start check of the cached supporter status, at most once a day, so a refund or a lapsed
 * subscription is noticed without the user visiting Settings. Does nothing on the fdroid build.
 * Call from the main thread: the billing manager must be started and stopped there.
 */
suspend fun refreshSupporterPerksIfDue(context: Context) {
    if (!supporterPerksAvailable()) return
    val perks = SupporterPerks(context)
    if (!perks.isCheckDue()) return
    val manager = createSupportBillingManager(context)
    manager.start()
    try {
        // No answer (offline, Play unavailable) leaves the cache as it was and tries again next start.
        val answered = withTimeoutOrNull(PLAY_ANSWER_TIMEOUT_MILLIS) { manager.state.first { it.purchasesRestored } }
        if (answered != null) perks.record(answered)
    } finally {
        manager.stop()
    }
}
