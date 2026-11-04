package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.budget.backup.BackupRestoreSink
import com.azimulkabir.actua.data.budget.backup.BackupUpload
import com.azimulkabir.actua.data.budget.backup.EncryptedBackupPipeline
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.TimeZone

class WebDavBackupTransportTest {
    @get:Rule val temporary = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var fake: FakeWebDavServer
    private lateinit var transport: WebDavBackupTransport
    private val folder = "Actua Backups"
    private val root = "/dav/files/alice/$folder"
    private var savedZone: TimeZone? = null

    @Before fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        fake = FakeWebDavServer()
        server = MockWebServer().apply { dispatcher = fake; start() }
        transport = WebDavBackupTransport(
            client = WebDavClient(OkHttpClient.Builder().followSslRedirects(false).build(), "alice", "app-pass"),
            baseUrl = server.url("/dav/files/alice/"),
            folderSegments = listOf(folder),
        )
    }

    @After fun tearDown() {
        server.shutdown()
        savedZone?.let(TimeZone::setDefault)
    }

    private fun upload(name: String, content: ByteArray, budgetId: String = "budget-1") = transport.upload(
        BackupUpload(
            name = name,
            budgetId = budgetId,
            budgetName = "Household",
            archiveName = name.removeSuffix(".actuabkp") + ".zip",
            createdAt = "2026-11-04T13:00:00Z",
            contentLength = content.size.toLong(),
        ),
        ByteArrayInputStream(content),
    )

    @Test fun uploadCreatesTheFoldersUploadsViaAPartFileAndRenames() {
        val content = ByteArray(5_000) { it.toByte() }

        val uploaded = upload("2026-11-04_13-00-00.actuabkp", content)

        val file = "$root/budget-1/2026-11-04_13-00-00.actuabkp"
        assertArrayEquals(content, fake.files.getValue(file))
        assertFalse(fake.files.keys.any { it.endsWith(".part") })
        assertEquals(listOf("PUT", "MOVE"), fake.requests.map { it.method }.filter { it == "PUT" || it == "MOVE" })
        assertTrue(fake.requestsOf("PUT").single().path.orEmpty().endsWith(".actuabkp.part"))
        assertEquals("2026-11-04_13-00-00.actuabkp", uploaded.name)
        assertEquals(5_000L, uploaded.sizeBytes)
        assertTrue(uploaded.isEncrypted)
        assertEquals(listOf(uploaded.id), transport.list("budget-1").map { it.id })
    }

    @Test fun aServerThatCannotRenameLeavesNothingBehind() {
        fake.failMove = true

        assertThrows(WebDavException.MoveUnsupported::class.java) { upload("a.actuabkp", ByteArray(10)) }

        assertTrue(fake.files.isEmpty())
        assertTrue(transport.list("budget-1").isEmpty())
    }

    @Test fun aTruncatedUploadIsRemovedAndReported() {
        fake.truncateBy = 3

        val error = assertThrows(WebDavException.UploadIncomplete::class.java) { upload("a.actuabkp", ByteArray(10)) }

        assertEquals(10L, error.expected)
        assertEquals(7L, error.actual)
        assertTrue(fake.files.isEmpty())
        assertTrue(transport.list("budget-1").isEmpty())
    }

    @Test fun aFullServerFailsTheUploadAndLeavesNothingListed() {
        fake.storageFull = true

        assertThrows(WebDavException.InsufficientStorage::class.java) { upload("a.actuabkp", ByteArray(10)) }

        assertTrue(transport.list("budget-1").isEmpty())
    }

    @Test fun listingOnlyShowsBackupFilesNotPartsFoldersOrOtherFiles() {
        fake.seed("$root/budget-1/2026-11-04_10-00-00.actuabkp", ByteArray(1))
        fake.seed("$root/budget-1/2026-11-04_11-00-00.actuabkp.part", ByteArray(1))
        fake.seed("$root/budget-1/notes.txt", ByteArray(1))
        fake.collections += "$root/budget-1/sub"

        assertEquals(listOf("2026-11-04_10-00-00.actuabkp"), transport.list("budget-1").map { it.name })
    }

    @Test fun listingABudgetThatHasNoFolderIsEmpty() {
        assertTrue(transport.list("budget-1").isEmpty())
        assertTrue(transport.listAll().isEmpty())
    }

    @Test fun listAllReturnsEveryBudgetsBackupsAndSkipsStrayFiles() {
        fake.seed("$root/budget-1/2026-11-04_10-00-00.actuabkp", ByteArray(1))
        fake.seed("$root/budget-2/2026-11-03_10-00-00.actuabkp", ByteArray(1))
        fake.seed("$root/readme.txt", ByteArray(1))

        val all = transport.listAll()

        assertEquals(setOf("budget-1" to "2026-11-04_10-00-00.actuabkp", "budget-2" to "2026-11-03_10-00-00.actuabkp"),
            all.map { it.budgetId to it.name }.toSet())
    }

    @Test fun downloadAndDeleteWorkOnAListedBackup() {
        val content = byteArrayOf(9, 8, 7)
        fake.seed("$root/budget-1/a.actuabkp", content)
        val item = transport.list("budget-1").single()

        val output = ByteArrayOutputStream()
        transport.download(item.id, output)
        transport.delete(item.id)

        assertArrayEquals(content, output.toByteArray())
        assertTrue(fake.files.isEmpty())
    }

    @Test fun downloadAndDeleteRefuseAnythingOutsideTheConfiguredFolder() {
        fake.seed("/dav/files/alice/Documents/secret.actuabkp", byteArrayOf(1))
        fake.seed("$root/budget-1/notes.txt", byteArrayOf(1))
        val before = fake.requests.size

        val outside = server.url("/dav/files/alice/Documents/secret.actuabkp").toString()
        val notBackup = server.url("$root/budget-1/notes.txt").toString()
        val otherHost = "http://example.invalid$root/budget-1/a.actuabkp"
        val folderItself = server.url("$root/").toString()
        listOf(outside, notBackup, otherHost, folderItself, "not a url").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { transport.delete(id) }
            assertThrows(IllegalArgumentException::class.java) { transport.download(id, ByteArrayOutputStream()) }
        }

        assertEquals(before, fake.requests.size)
        assertEquals(2, fake.files.size)
    }

    @Test fun budgetIdsThatCouldEscapeTheFolderAreRefused() {
        listOf("../other", "a/b", "..", "", "a b").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { upload("a.actuabkp", ByteArray(1), budgetId = id) }
            assertThrows(IllegalArgumentException::class.java) { transport.list(id) }
        }
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun anEncryptedBackupRoundTripsThroughThePipelineAndOnlyCiphertextIsStored() {
        val passphrase = "correct-horse-battery-staple-42".toCharArray()
        val plaintext = "PK-synthetic-archive-bytes-for-the-webdav-test".toByteArray()
        val sink = RecordingSink()
        val work = temporary.newFolder("work")
        val pipeline = EncryptedBackupPipeline(
            transport = transport,
            passphraseProvider = { passphrase },
            budgetNameOf = { "Household" },
            restoreSink = sink,
            workDir = work,
            clock = { Instant.parse("2026-11-04T18:00:00Z") },
        )
        val archive = File(temporary.root, "2026-11-04_13-00-00.zip").also { it.writeBytes(plaintext) }

        val uploaded = pipeline.upload("budget-1", archive)

        val stored = fake.files.getValue("$root/budget-1/2026-11-04_13-00-00.actuabkp")
        assertEquals("ACTUABKP", String(stored.copyOfRange(0, 8), Charsets.US_ASCII))
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("synthetic-archive-bytes"))

        pipeline.restore(uploaded.id)

        assertArrayEquals(plaintext, sink.importedAsNew)
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test fun retentionDeletesOnlyOldBackupFilesInTheFolder() {
        listOf("08", "09", "10", "11").forEach { hour ->
            fake.seed("$root/budget-1/2026-11-04_$hour-00-00.actuabkp", ByteArray(1))
        }
        fake.seed("$root/budget-1/notes.txt", ByteArray(1))
        fake.seed("$root/budget-1/2026-11-04_07-00-00.actuabkp.part", ByteArray(1))
        fake.seed("/dav/files/alice/Documents/2026-11-04_06-00-00.actuabkp", ByteArray(1))
        val pipeline = EncryptedBackupPipeline(
            transport = transport,
            passphraseProvider = { "correct-horse-battery-staple-42".toCharArray() },
            budgetNameOf = { null },
            restoreSink = RecordingSink(),
            workDir = temporary.newFolder("work"),
            clock = { Instant.parse("2026-11-04T18:00:00Z") },
        )
        val archive = File(temporary.root, "2026-11-04_13-00-00.zip").also { it.writeBytes(ByteArray(64)) }

        pipeline.upload("budget-1", archive)

        // Five backups on the day keep the newest three: the two oldest are removed, nothing else.
        assertFalse("$root/budget-1/2026-11-04_08-00-00.actuabkp" in fake.files)
        assertFalse("$root/budget-1/2026-11-04_09-00-00.actuabkp" in fake.files)
        assertTrue("$root/budget-1/2026-11-04_10-00-00.actuabkp" in fake.files)
        assertTrue("$root/budget-1/2026-11-04_11-00-00.actuabkp" in fake.files)
        assertTrue("$root/budget-1/2026-11-04_13-00-00.actuabkp" in fake.files)
        assertTrue("$root/budget-1/notes.txt" in fake.files)
        assertTrue("$root/budget-1/2026-11-04_07-00-00.actuabkp.part" in fake.files)
        assertTrue("/dav/files/alice/Documents/2026-11-04_06-00-00.actuabkp" in fake.files)
    }

    // --- Restoring on a fresh install: no passphrase stored, every budget's backups listed ---------------------

    private fun freshInstallPipeline(sink: RecordingSink) = EncryptedBackupPipeline(
        transport = transport,
        passphraseProvider = { null },
        budgetNameOf = { null },
        restoreSink = sink,
        workDir = temporary.newFolder(),
        clock = { Instant.parse("2026-11-04T18:00:00Z") },
    )

    private fun uploadedBy(budgetId: String, name: String, content: ByteArray, passphrase: String): String {
        val pipeline = EncryptedBackupPipeline(
            transport = transport,
            passphraseProvider = { passphrase.toCharArray() },
            budgetNameOf = { null },
            restoreSink = RecordingSink(),
            workDir = temporary.newFolder(),
            clock = { Instant.parse("2026-11-04T18:00:00Z") },
        )
        val archive = File(temporary.newFolder(), name).also { it.writeBytes(content) }
        return pipeline.upload(budgetId, archive).id
    }

    @Test fun aFreshInstallListsEveryBudgetsBackupsNewestFirst() {
        val a = uploadedBy("budget-1", "2026-11-01_10-00-00.zip", ByteArray(40), "correct-horse-battery-staple-42")
        val b = uploadedBy("budget-2", "2026-11-03_10-00-00.zip", ByteArray(40), "correct-horse-battery-staple-42")
        val c = uploadedBy("budget-1", "2026-11-02_10-00-00.zip", ByteArray(40), "correct-horse-battery-staple-42")
        fake.modified["$root/budget-1/2026-11-01_10-00-00.actuabkp"] = "Sun, 01 Nov 2026 10:00:00 GMT"
        fake.modified["$root/budget-2/2026-11-03_10-00-00.actuabkp"] = "Tue, 03 Nov 2026 10:00:00 GMT"
        fake.modified["$root/budget-1/2026-11-02_10-00-00.actuabkp"] = "Mon, 02 Nov 2026 10:00:00 GMT"

        val listed = freshInstallPipeline(RecordingSink()).listBackups(null)

        assertEquals(listOf(b, c, a), listed.map { it.id })
        assertEquals(listOf("budget-2", "budget-1", "budget-1"), listed.map { it.budgetId })
    }

    @Test fun aFreshInstallRestoresTheChosenBackupAsANewBudgetWithTheTypedPassword() {
        val plaintext = "PK-synthetic-archive-bytes-for-a-fresh-install".toByteArray()
        val id = uploadedBy("budget-1", "2026-11-04_13-00-00.zip", plaintext, "correct-horse-battery-staple-42")
        val sink = RecordingSink()

        freshInstallPipeline(sink).restore(id, passphrase = "correct-horse-battery-staple-42".toCharArray())

        assertArrayEquals(plaintext, sink.importedAsNew)
    }

    @Test fun aFreshInstallWithoutAPasswordOrWithAWrongOneImportsNothing() {
        val id = uploadedBy("budget-1", "2026-11-04_13-00-00.zip", ByteArray(64), "correct-horse-battery-staple-42")
        val sink = RecordingSink()
        val pipeline = freshInstallPipeline(sink)

        assertThrows(com.azimulkabir.actua.data.budget.backup.PassphraseRequiredException::class.java) {
            pipeline.restore(id)
        }
        assertThrows(com.azimulkabir.actua.data.budget.backup.InvalidPassphraseException::class.java) {
            pipeline.restore(id, passphrase = "not-the-password-99".toCharArray())
        }

        assertEquals(null, sink.importedAsNew)
    }

    @Test fun anEmptyServerListsNothing() {
        assertTrue(freshInstallPipeline(RecordingSink()).listBackups(null).isEmpty())
    }

    private class RecordingSink : BackupRestoreSink {
        var importedAsNew: ByteArray? = null
        private val metadata = BudgetMetadata("restored", "Restored", null, null, null, null, null)

        override fun restoreInto(budgetId: String, archive: InputStream): BudgetMetadata = metadata

        override fun importAsNew(archive: InputStream): BudgetMetadata {
            importedAsNew = archive.readBytes()
            return metadata
        }
    }
}
