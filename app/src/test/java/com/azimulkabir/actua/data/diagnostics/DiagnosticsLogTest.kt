package com.azimulkabir.actua.data.diagnostics

import com.azimulkabir.actua.data.network.ActualServerException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsLogTest {
    private var now = 1_791_500_000_000L

    @Before fun setUp() {
        DiagnosticsLog.clear()
        DiagnosticsLog.clock = { now }
    }

    @After fun tearDown() {
        DiagnosticsLog.clear()
        DiagnosticsLog.clock = System::currentTimeMillis
    }

    @Test fun `paths keep their shape but lose ids, file names and query strings`() {
        assertEquals("/sync/sync", DiagnosticsLog.pathShape("/sync/sync"))
        assertEquals("/sync/download-user-file", DiagnosticsLog.pathShape("/sync/download-user-file?fileId=abc123"))
        assertEquals("/gocardless/:id", DiagnosticsLog.pathShape("/gocardless/0f6e4c2a-9a51-4d1b-8c1e-1f2d3c4b5a69"))
        assertEquals("/files/:id", DiagnosticsLog.pathShape("/files/My Budget.zip#frag"))
        assertEquals("/", DiagnosticsLog.pathShape(""))
    }

    @Test fun `error messages, server bodies and secrets never enter the log`() {
        val secret = "hunter2-token-abc"
        DiagnosticsLog.http("POST", "/account/login?password=$secret", 401, 120)
        DiagnosticsLog.http("POST", "/sync/sync", null, 30_000, ActualServerException.Http(500, "server said $secret"))
        DiagnosticsLog.syncFailed(IllegalStateException("budget 'Household' key=$secret"), 900)
        DiagnosticsLog.bankSync(2, 3, 1, 0, listOf("ok", "timed-out", "Chase Checking $secret"))

        val report = DiagnosticsReport.build(
            DiagnosticsLog.events(), nowMillis = now,
            appVersion = "1.0 (1, debug)", androidVersion = "15 (API 35)", device = "Test Device",
        )
        assertFalse(report, report.contains(secret))
        assertFalse(report, report.contains("Household"))
        assertFalse(report, report.contains("server said"))
        assertFalse(report, report.contains("password"))
        assertTrue(report, report.contains("HTTP POST /account/login 401 120ms"))
        assertTrue(report, report.contains("HTTP POST /sync/sync failed Http(500) 30000ms"))
        assertTrue(report, report.contains("SYNC failed IllegalStateException 900ms"))
        assertTrue(report, report.contains("statuses=ok=1,other=1,timed-out=1"))
    }

    @Test fun `failed actions and crashes keep the error type and app frames but not the message`() {
        val error = IllegalArgumentException("payee 'Corner Shop' is not valid")
        error.stackTrace = arrayOf(
            StackTraceElement("java.util.ArrayList", "get", "ArrayList.java", 10),
            StackTraceElement("com.azimulkabir.actua.data.ActuaRepository", "importTransactions", "ActuaRepository.kt", 1132),
            StackTraceElement("com.azimulkabir.actua.ui.navigation.AppNavigationKt\$mutate\$1", "invoke", "AppNavigation.kt", 600),
        )
        DiagnosticsLog.actionFailed("Importing transactions", error)
        DiagnosticsLog.crash(Thread.currentThread(), RuntimeException("budget Household", error))
        val lines = DiagnosticsLog.events().map { it.line() }

        assertTrue(lines.toString(), lines.none { "Corner Shop" in it || "Household" in it })
        assertTrue(lines[0], lines[0].endsWith(
            "ERROR Importing transactions failed IllegalArgumentException " +
                "at ActuaRepository.importTransactions:1132 < AppNavigationKt\$mutate\$1.invoke:600",
        ))
        assertTrue(lines[1], lines[1].contains("CRASH "))
        assertTrue(lines[1], lines[1].contains("RuntimeException<-IllegalArgumentException at ActuaRepository.importTransactions:1132"))
    }

    @Test fun `the log keeps only the most recent events`() {
        repeat(DiagnosticsLog.MAX_EVENTS + 25) { DiagnosticsLog.syncStarted("Background") }
        assertEquals(DiagnosticsLog.MAX_EVENTS, DiagnosticsLog.events().size)
    }

    @Test fun `events older than the retention window are dropped`() {
        DiagnosticsLog.syncStarted("Background")
        now += (DiagnosticsLog.RETENTION_DAYS + 1) * 24 * 60 * 60 * 1000
        DiagnosticsLog.syncStarted("App open")
        assertEquals(listOf("started (App open)"), DiagnosticsLog.events().map { it.detail })
    }

    @Test fun `clear empties the log`() {
        DiagnosticsLog.syncStarted("Background")
        DiagnosticsLog.clear()
        assertTrue(DiagnosticsLog.events().isEmpty())
    }
}
