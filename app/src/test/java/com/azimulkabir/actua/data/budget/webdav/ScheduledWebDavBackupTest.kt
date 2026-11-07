package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.backup.BackupRestoreSink
import com.azimulkabir.actua.data.budget.backup.EncryptedBackupPipeline
import com.azimulkabir.actua.data.budget.webdav.ScheduledWebDavBackup.Outcome
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.TimeZone

class ScheduledWebDavBackupTest {
    @get:Rule val temporary = TemporaryFolder()

    private var configured = true
    private var passphraseSet = true
    private var archive: File? = null
    private var failure: Exception? = null
    private val uploads = mutableListOf<Pair<String, File>>()
    private val errors = mutableListOf<String?>()
    private var savedZone: TimeZone? = null

    @Before fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        archive = temporary.newFile("2026-11-04_13-00-00.zip").also { it.writeBytes(ByteArray(64) { b -> b.toByte() }) }
    }

    @After fun tearDown() {
        savedZone?.let(TimeZone::setDefault)
    }

    private fun scheduled() = ScheduledWebDavBackup(
        isConfigured = { configured },
        hasPassphrase = { passphraseSet },
        latestArchive = { archive },
        upload = { id, file ->
            failure?.let { throw it }
            uploads += id to file
        },
        recordError = { errors += it },
        describe = { "described: ${it.message}" },
        passphraseMissingMessage = "no password",
    )

    @Test fun aSuccessfulRunUploadsTheNewestLocalBackup() {
        assertEquals(Outcome.SUCCESS, scheduled().run("budget-1", attempt = 0))

        assertEquals(listOf("budget-1" to archive), uploads.map { it.first to it.second })
        assertTrue(errors.isEmpty())
    }

    @Test fun aDisconnectedWebDavDoesNothingAndRecordsNothing() {
        configured = false

        assertEquals(Outcome.SUCCESS, scheduled().run("budget-1", attempt = 0))

        assertTrue(uploads.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test fun aMissingBackupPasswordFailsWithoutRetryAndIsRecorded() {
        passphraseSet = false

        assertEquals(Outcome.FAILURE, scheduled().run("budget-1", attempt = 0))

        assertTrue(uploads.isEmpty())
        assertEquals(listOf<String?>("no password"), errors)
    }

    @Test fun noLocalBackupYetIsNotAnError() {
        archive = null

        assertEquals(Outcome.SUCCESS, scheduled().run("budget-1", attempt = 0))

        assertTrue(uploads.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test fun aTransientFailureRetriesWithBackoffAndIsRecordedOnlyWhenItKeepsFailing() {
        failure = WebDavException.Network(IOException("offline"))
        val runner = scheduled()

        // Attempts 0, 1 and 2 ask WorkManager to retry; nothing is shown to the user yet.
        (0 until ScheduledWebDavBackup.MAX_RETRIES).forEach { attempt ->
            assertEquals(Outcome.RETRY, runner.run("budget-1", attempt))
        }
        assertTrue(errors.isEmpty())

        assertEquals(Outcome.FAILURE, runner.run("budget-1", ScheduledWebDavBackup.MAX_RETRIES))
        assertEquals(listOf<String?>("described: offline"), errors)
    }

    @Test fun aRecoveryAfterRetriesSucceedsAndLeavesNoError() {
        failure = WebDavException.Server(503, "Service Unavailable")
        val runner = scheduled()
        assertEquals(Outcome.RETRY, runner.run("budget-1", attempt = 0))

        failure = null

        assertEquals(Outcome.SUCCESS, runner.run("budget-1", attempt = 1))
        assertEquals(1, uploads.size)
        assertTrue(errors.isEmpty())
    }

    @Test fun failuresThatAnotherAttemptCannotFixAreRecordedAtOnce() {
        listOf<Exception>(
            WebDavException.Unauthorized(401),
            WebDavException.NotFound(),
            WebDavException.InsufficientStorage(),
            WebDavException.Tls(IOException("cert")),
            WebDavException.Redirect("https://elsewhere/"),
            WebDavException.MoveUnsupported(),
            WebDavException.InvalidSettings(WebDavException.InvalidSettings.Reason.INSECURE_HTTP),
            WebDavException.Server(403, "Forbidden"),
        ).forEach { error ->
            errors.clear()
            failure = error

            assertEquals(error::class.simpleName, Outcome.FAILURE, scheduled().run("budget-1", attempt = 0))

            assertEquals(1, errors.size)
        }
    }

    @Test fun serverErrorsAndShortUploadsAreWorthRetrying() {
        assertTrue(ScheduledWebDavBackup.isRetryable(WebDavException.Server(500, "")))
        assertTrue(ScheduledWebDavBackup.isRetryable(WebDavException.Server(503, "")))
        assertTrue(ScheduledWebDavBackup.isRetryable(WebDavException.Server(429, "")))
        assertTrue(ScheduledWebDavBackup.isRetryable(WebDavException.Server(408, "")))
        assertTrue(ScheduledWebDavBackup.isRetryable(WebDavException.UploadIncomplete(10, 7)))
        assertTrue(ScheduledWebDavBackup.isRetryable(IllegalStateException("disk")))
        assertFalse(ScheduledWebDavBackup.isRetryable(WebDavException.Server(400, "")))
        assertFalse(ScheduledWebDavBackup.isRetryable(WebDavException.MoveRejected(502)))
        assertFalse(ScheduledWebDavBackup.isRetryable(WebDavException.NotTls(javax.net.ssl.SSLException("x"))))
    }

    // --- Against a (fake) server -----------------------------------------------------------------------------

    private class EndToEnd(temporary: TemporaryFolder, val fake: FakeWebDavServer) {
        val server = MockWebServer().apply { dispatcher = fake; start() }
        val sink = object : BackupRestoreSink {
            override fun restoreInto(budgetId: String, archive: InputStream) =
                BudgetMetadata("restored", "Restored", null, null, null, null, null)

            override fun importAsNew(archive: InputStream) =
                BudgetMetadata("restored", "Restored", null, null, null, null, null)
        }
        val pipeline = EncryptedBackupPipeline(
            transport = WebDavBackupTransport(
                WebDavClient(OkHttpClient(), "alice", "app-pass"),
                server.url("/dav/files/alice/"),
                listOf("Actua Backups"),
            ),
            passphraseProvider = { "correct-horse-battery-staple-42".toCharArray() },
            budgetNameOf = { "Household" },
            restoreSink = sink,
            workDir = temporary.newFolder("work"),
            clock = { Instant.parse("2026-11-04T18:00:00Z") },
        )
    }

    private fun endToEnd(fake: FakeWebDavServer = FakeWebDavServer()) = EndToEnd(temporary, fake)

    private fun scheduledOver(env: EndToEnd, recorded: MutableList<String?>) = ScheduledWebDavBackup(
        isConfigured = { true },
        hasPassphrase = { true },
        latestArchive = { archive },
        upload = { id, file -> env.pipeline.upload(id, file) },
        recordError = { recorded += it },
        describe = { it::class.simpleName.orEmpty() },
        passphraseMissingMessage = "no password",
    )

    @Test fun aScheduledRunStoresAnEncryptedBackupOnTheServer() {
        val env = endToEnd()
        val recorded = mutableListOf<String?>()
        try {
            assertEquals(Outcome.SUCCESS, scheduledOver(env, recorded).run("budget-1", attempt = 0))

            val stored = env.fake.files.getValue("/dav/files/alice/Actua Backups/budget-1/2026-11-04_13-00-00.actuabkp")
            assertEquals("ACTUABKP", String(stored.copyOfRange(0, 8), Charsets.US_ASCII))
            assertTrue(recorded.isEmpty())
        } finally {
            env.server.shutdown()
        }
    }

    @Test fun aFullServerIsRecordedAndNothingIsListedAsABackup() {
        val env = endToEnd(FakeWebDavServer().apply { storageFull = true })
        val recorded = mutableListOf<String?>()
        try {
            assertEquals(Outcome.FAILURE, scheduledOver(env, recorded).run("budget-1", attempt = 0))

            assertEquals(listOf<String?>("InsufficientStorage"), recorded)
            assertTrue(env.pipeline.listBackups("budget-1").isEmpty())
        } finally {
            env.server.shutdown()
        }
    }

    @Test fun aServerThatIsDownRetriesThenFails() {
        val env = endToEnd()
        val recorded = mutableListOf<String?>()
        env.server.shutdown()

        val runner = scheduledOver(env, recorded)
        assertEquals(Outcome.RETRY, runner.run("budget-1", attempt = 0))
        assertTrue(recorded.isEmpty())
        assertEquals(Outcome.FAILURE, runner.run("budget-1", attempt = ScheduledWebDavBackup.MAX_RETRIES))
        assertEquals(listOf<String?>("Network"), recorded)
    }
}
