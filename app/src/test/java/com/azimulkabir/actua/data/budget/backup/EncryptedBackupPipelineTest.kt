package com.azimulkabir.actua.data.budget.backup

import com.azimulkabir.actua.data.budget.BudgetMetadata
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.TimeZone

class EncryptedBackupPipelineTest {
    @get:Rule val temporary = TemporaryFolder()

    private val passphrase = "correct-horse-battery-staple-42".toCharArray()
    private val now = Instant.parse("2026-11-04T18:00:00Z")
    private val plaintext = "PK-synthetic-archive-bytes-for-the-pipeline-test".toByteArray()
    private lateinit var workDir: File
    private lateinit var transport: FakeTransport
    private lateinit var sink: FakeSink
    private var savedZone: TimeZone? = null

    @Before fun setUp() {
        // Retention groups backups by local day, so pin the zone the pipeline reads.
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        workDir = temporary.newFolder("work")
        transport = FakeTransport()
        sink = FakeSink()
    }

    @After fun tearDown() {
        savedZone?.let(TimeZone::setDefault)
    }

    private fun pipeline(
        provider: () -> CharArray? = { passphrase },
        transport: BackupTransport = this.transport,
    ) = EncryptedBackupPipeline(
        transport = transport,
        passphraseProvider = provider,
        budgetNameOf = { id -> if (id == "budget-1") "Household" else null },
        restoreSink = sink,
        workDir = workDir,
        clock = { now },
    )

    private fun archive(name: String = "2026-11-04_13-00-00.zip"): File =
        temporary.newFile(name).also { it.writeBytes(plaintext) }

    @Test fun uploadEncryptsBeforeTheTransportSeesAnything() {
        val uploaded = pipeline().upload("budget-1", archive())

        val stored = transport.stored.getValue(uploaded.id)
        assertEquals("ACTUABKP", String(stored.copyOfRange(0, 8), Charsets.US_ASCII))
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("synthetic-archive-bytes"))
        val request = transport.uploads.single()
        assertEquals("2026-11-04_13-00-00.actuabkp", request.name)
        assertEquals("2026-11-04_13-00-00.zip", request.archiveName)
        assertEquals("budget-1", request.budgetId)
        assertEquals("Household", request.budgetName)
        assertEquals(now.toString(), request.createdAt)
    }

    @Test fun uploadNamesAnUnknownBudgetAfterItsId() {
        pipeline().upload("budget-2", archive())
        assertEquals("budget-2", transport.uploads.single().budgetName)
    }

    @Test fun uploadWithoutAPassphraseSendsNothing() {
        assertThrows(IllegalStateException::class.java) { pipeline(provider = { null }).upload("budget-1", archive()) }
        assertTrue(transport.uploads.isEmpty())
        assertTrue(workDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun stagingFilesAreRemovedAfterSuccessAndFailure() {
        pipeline().upload("budget-1", archive())
        assertTrue(workDir.listFiles().orEmpty().isEmpty())

        transport.failUpload = true
        assertThrows(IOException::class.java) { pipeline().upload("budget-1", archive("2026-11-04_14-00-00.zip")) }
        assertTrue(workDir.listFiles().orEmpty().isEmpty())
        assertEquals(1, transport.stored.size)
    }

    @Test fun retentionRemovesTheOldestSameDayBackupsButNeverTheNewOne() {
        val old = listOf("08", "09", "10", "11").map { hour ->
            transport.seed("old-$hour", "2026-11-04_$hour-00-00.actuabkp", "budget-1")
        }

        val uploaded = pipeline().upload("budget-1", archive("2026-11-04_13-00-00.zip"))

        // Four older + the new one on the same day keeps the newest three.
        assertEquals(setOf("old-08", "old-09"), transport.deleted.toSet())
        assertFalse(uploaded.id in transport.deleted)
        assertEquals(old.size - 2 + 1, transport.items.size)
    }

    @Test fun retentionKeepsEverythingWhenTheListingFails() {
        transport.seed("old-1", "2026-11-04_08-00-00.actuabkp", "budget-1")
        transport.failList = true

        pipeline().upload("budget-1", archive())

        assertTrue(transport.deleted.isEmpty())
    }

    @Test fun retentionKeepsEverythingWhenTheNewUploadIsNotListedYet() {
        (8..11).forEach { transport.seed("old-$it", "2026-11-04_%02d-00-00.actuabkp".format(it), "budget-1") }
        transport.hideNewUploads = true

        pipeline().upload("budget-1", archive())

        assertTrue(transport.deleted.isEmpty())
    }

    @Test fun deleteFailuresDoNotFailTheUpload() {
        (8..11).forEach { transport.seed("old-$it", "2026-11-04_%02d-00-00.actuabkp".format(it), "budget-1") }
        transport.failDelete = true

        val uploaded = pipeline().upload("budget-1", archive())

        assertNotNull(uploaded)
        assertTrue(uploaded.id in transport.stored)
    }

    @Test fun restoreDecryptsIntoANewBudget() {
        val uploaded = pipeline().upload("budget-1", archive())

        val restored = pipeline().restore(uploaded.id)

        assertEquals(sink.metadata, restored)
        assertArrayEquals(plaintext, sink.importedAsNew)
        assertNull(sink.restoredInto)
        assertTrue(workDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun restoreCanTargetAnOpenBudget() {
        val uploaded = pipeline().upload("budget-1", archive())

        pipeline().restore(uploaded.id, targetBudgetId = "budget-1")

        assertEquals("budget-1", sink.restoredInto)
        assertArrayEquals(plaintext, sink.restoredBytes)
        assertNull(sink.importedAsNew)
    }

    @Test fun restoreUsesAnExplicitPassphraseOverTheStoredOne() {
        val uploaded = pipeline().upload("budget-1", archive())

        pipeline(provider = { "something-else-entirely".toCharArray() }).restore(uploaded.id, passphrase = passphrase)

        assertArrayEquals(plaintext, sink.importedAsNew)
    }

    @Test fun restoreWithAWrongPassphraseTouchesNothing() {
        val uploaded = pipeline().upload("budget-1", archive())

        assertThrows(InvalidPassphraseException::class.java) {
            pipeline().restore(uploaded.id, passphrase = "not-the-password-99".toCharArray())
        }

        assertNull(sink.importedAsNew)
        assertTrue(workDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun restoreOfAnEncryptedBackupNeedsAPassphrase() {
        val uploaded = pipeline().upload("budget-1", archive())

        assertThrows(PassphraseRequiredException::class.java) {
            pipeline(provider = { null }).restore(uploaded.id)
        }

        assertNull(sink.importedAsNew)
    }

    @Test fun restoreOfAPlainZipNeedsNoPassphrase() {
        transport.stored["plain"] = plaintext

        pipeline(provider = { null }).restore("plain")

        assertArrayEquals(plaintext, sink.importedAsNew)
    }

    @Test fun listingIsNewestFirstAndScopedToTheBudgetUnlessAskedForAll() {
        transport.seed("a", "2026-11-01_10-00-00.actuabkp", "budget-1", modified = "2026-11-01T10:00:00Z")
        transport.seed("b", "2026-11-03_10-00-00.actuabkp", "budget-1", modified = "2026-11-03T10:00:00Z")
        transport.seed("c", "2026-11-02_10-00-00.actuabkp", "budget-2", modified = "2026-11-02T10:00:00Z")

        assertEquals(listOf("b", "a"), pipeline().listBackups("budget-1").map { it.id })
        assertEquals(listOf("b", "c", "a"), pipeline().listBackups().map { it.id })
    }

    private class FakeTransport : BackupTransport {
        val stored = linkedMapOf<String, ByteArray>()
        val items = linkedMapOf<String, RemoteBackupItem>()
        val uploads = mutableListOf<BackupUpload>()
        val deleted = mutableListOf<String>()
        var failUpload = false
        var failList = false
        var failDelete = false
        var hideNewUploads = false
        private val hidden = mutableSetOf<String>()
        private var counter = 0

        fun seed(id: String, name: String, budgetId: String, modified: String? = null) {
            stored[id] = ByteArray(0)
            items[id] = RemoteBackupItem(id, name, budgetId, null, 0, modified, true)
        }

        override fun upload(request: BackupUpload, content: InputStream): RemoteBackupItem {
            if (failUpload) throw IOException("upload failed")
            val id = "new-${++counter}"
            val bytes = content.readBytes()
            stored[id] = bytes
            uploads += request
            val item = RemoteBackupItem(id, request.name, request.budgetId, request.budgetName, bytes.size.toLong(), request.createdAt, true)
            items[id] = item
            if (hideNewUploads) hidden += id
            return item
        }

        override fun list(budgetId: String): List<RemoteBackupItem> {
            if (failList) throw IOException("list failed")
            return items.values.filter { it.budgetId == budgetId && it.id !in hidden }
        }

        override fun listAll(): List<RemoteBackupItem> = items.values.toList()

        override fun download(backupId: String, output: OutputStream) {
            output.write(stored.getValue(backupId))
        }

        override fun delete(backupId: String) {
            if (failDelete) throw IOException("delete failed")
            deleted += backupId
            items.remove(backupId)
            stored.remove(backupId)
        }
    }

    private class FakeSink : BackupRestoreSink {
        val metadata = BudgetMetadata("restored", "Restored", null, null, null, null, null)
        var importedAsNew: ByteArray? = null
        var restoredInto: String? = null
        var restoredBytes: ByteArray? = null

        override fun restoreInto(budgetId: String, archive: InputStream): BudgetMetadata {
            restoredInto = budgetId
            restoredBytes = archive.readBytes()
            return metadata
        }

        override fun importAsNew(archive: InputStream): BudgetMetadata {
            importedAsNew = archive.readBytes()
            return metadata
        }
    }
}
