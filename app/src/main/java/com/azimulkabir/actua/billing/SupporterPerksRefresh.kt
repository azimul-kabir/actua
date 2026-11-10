package com.azimulkabir.actua.billing

import android.content.Context
import com.azimulkabir.actua.data.preferences.AppIcons
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.SupporterPerks
import com.azimulkabir.actua.ui.theme.AccentPalette
import com.azimulkabir.actua.widget.WidgetStyle
import com.azimulkabir.actua.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Registers how each supporter extra returns to its default when support lapses. Call once at app
 * start, before anything records the supporter status. Does nothing on the fdroid build.
 */
fun registerSupporterExtras(context: Context) {
    if (!supporterPerksAvailable()) return
    val appContext = context.applicationContext
    SupporterPerks.register(AppIcons.RESET_ID) { AppIcons(appContext).resetToDefault() }
    SupporterPerks.register(WIDGET_STYLE_RESET_ID) {
        DisplayPreferences(appContext).widgetStyle = WidgetStyle.Default
        WidgetUpdater.requestAll(appContext)
    }
    SupporterPerks.register(AccentPalette.RESET_ID) { DisplayPreferences(appContext).accent = AccentPalette.Violet }
}

private const val WIDGET_STYLE_RESET_ID = "widget_style"
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
