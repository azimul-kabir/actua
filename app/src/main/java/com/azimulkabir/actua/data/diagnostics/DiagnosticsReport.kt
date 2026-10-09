package com.azimulkabir.actua.data.diagnostics

import android.os.Build
import com.azimulkabir.actua.BuildConfig

/** The text a user copies, saves or emails with a bug report: app/device versions and the event log. */
object DiagnosticsReport {
    const val SUPPORT_EMAIL = "actua.mobile@gmail.com"

    fun build(
        events: List<DiagnosticsLog.Event> = DiagnosticsLog.events(),
        nowMillis: Long = System.currentTimeMillis(),
        appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
        androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        device: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    ): String = buildString {
        appendLine("Actua diagnostics report")
        appendLine("Generated: ${DiagnosticsLog.timestamp(nowMillis)}")
        appendLine("App: $appVersion")
        appendLine("Android: $androidVersion")
        appendLine("Device: $device")
        appendLine()
        appendLine("This report lists app events, error types, code locations, request paths, status codes,")
        appendLine("timings and counts only.")
        appendLine("It contains no server address, credentials, keys or budget data.")
        appendLine()
        appendLine("Events (UTC, oldest first): ${events.size}")
        if (events.isEmpty()) appendLine("No events recorded yet.")
        events.forEach { appendLine(it.line()) }
    }

    /** A file name for the exported report, e.g. `actua-diagnostics-20261009-0154.txt`. */
    fun fileName(nowMillis: Long = System.currentTimeMillis()): String =
        "actua-diagnostics-" + DiagnosticsLog.timestamp(nowMillis).take(16)
            .replace("-", "").replace(":", "").replace("T", "-") + ".txt"
}
