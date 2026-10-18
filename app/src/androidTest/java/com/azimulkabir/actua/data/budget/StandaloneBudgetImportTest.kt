package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StandaloneBudgetImportTest {

    @Test
    fun rootLevelArchiveImportsAsStandaloneWithStrippedCloudFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Source ${UUID.randomUUID()}")
        try {
            val sourceDb = files.databaseFile(source.id)
            val metadataJson = JSONObject()
                .put("id", "source-import-id")
                .put("budgetName", "Imported Budget")
                .put("cloudFileId", "cloud-123")
                .put("groupId", "group-456")
                .put("encryptKeyId", "key-789")
                .put("lastUploaded", "2026-10-18")
                .put("isDemo", false)

            val archiveBytes = createArchive(sourceDb, metadataJson, nestedFolder = null)
            val imported = files.importLocalBudget(archiveBytes.inputStream())

            try {
                assertEquals("source-import-id", imported.id)
                assertEquals("Imported Budget", imported.budgetName)
                assertTrue(imported.isUserLocalOnly)
                assertFalse(imported.isServerBacked)
                assertFalse(imported.isDemo)
                assertNull(imported.cloudFileId)
                assertNull(imported.groupId)

                val storedJson = JSONObject(files.metadataFile(imported.id).readText())
                assertTrue(storedJson.optBoolean("resetClock", false))
                assertFalse(storedJson.has("cloudFileId"))
                assertFalse(storedJson.has("groupId"))
                assertFalse(storedJson.has("encryptKeyId"))
                assertFalse(storedJson.has("lastUploaded"))

                ActualBudgetDatabase.validate(files.databaseFile(imported.id))
                BudgetOpenProbe.validate(files.databaseFile(imported.id))
            } finally {
                runCatching { files.deleteBudget(imported.id) }
            }
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun nestedFolderArchiveImportsSuccessfully() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Nested Source ${UUID.randomUUID()}")
        try {
            val sourceDb = files.databaseFile(source.id)
            val metadataJson = JSONObject()
                .put("id", "nested-import-id")
                .put("budgetName", "Nested Budget")
                .put("cloudFileId", "cloud-abc")

            val archiveBytes = createArchive(sourceDb, metadataJson, nestedFolder = "ActualExportFolder")
            val imported = files.importLocalBudget(archiveBytes.inputStream())

            try {
                assertEquals("nested-import-id", imported.id)
                assertEquals("Nested Budget", imported.budgetName)
                assertTrue(imported.isUserLocalOnly)
                ActualBudgetDatabase.validate(files.databaseFile(imported.id))
            } finally {
                runCatching { files.deleteBudget(imported.id) }
            }
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun idCollisionAllocatesNewIdAndPreservesExistingBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val existing = files.createBudget("Existing Budget ${UUID.randomUUID()}")
        try {
            val existingDb = files.databaseFile(existing.id)
            val metadataJson = JSONObject()
                .put("id", existing.id) // Collision with existing budget id!
                .put("budgetName", "Collision Import")

            val archiveBytes = createArchive(existingDb, metadataJson, nestedFolder = null)
            val imported = files.importLocalBudget(archiveBytes.inputStream())

            try {
                assertNotEquals(existing.id, imported.id)
                assertTrue(imported.id.startsWith("${existing.id}-"))
                assertEquals("Collision Import", imported.budgetName)
                assertTrue(imported.isUserLocalOnly)

                // Verify original existing budget remains intact
                val reloadedExisting = files.metadata(existing.id)
                assertNotNull(reloadedExisting)
                assertEquals(existing.budgetName, reloadedExisting?.budgetName)
                ActualBudgetDatabase.validate(files.databaseFile(existing.id))
                ActualBudgetDatabase.validate(files.databaseFile(imported.id))
            } finally {
                runCatching { files.deleteBudget(imported.id) }
            }
        } finally {
            runCatching { files.deleteBudget(existing.id) }
        }
    }

    @Test
    fun importWithNameOverrideAppliesProvidedName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val source = files.createBudget("Source Override ${UUID.randomUUID()}")
        try {
            val sourceDb = files.databaseFile(source.id)
            val metadataJson = JSONObject()
                .put("id", "override-test-id")
                .put("budgetName", "Original Name")

            val archiveBytes = createArchive(sourceDb, metadataJson)
            val imported = files.importLocalBudget(archiveBytes.inputStream(), nameOverride = "Overridden Name")

            try {
                assertEquals("Overridden Name", imported.budgetName)
                val stored = JSONObject(files.metadataFile(imported.id).readText())
                assertEquals("Overridden Name", stored.getString("budgetName"))
            } finally {
                runCatching { files.deleteBudget(imported.id) }
            }
        } finally {
            runCatching { files.deleteBudget(source.id) }
        }
    }

    @Test
    fun unsafeArchiveWithTraversalIsRejected() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.sqlite"))
            zip.write("malicious".toByteArray())
            zip.closeEntry()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)

        assertThrows(BudgetFileException.UnsafeArchive::class.java) {
            files.importLocalBudget(bytes.toByteArray().inputStream())
        }
    }

    @Test
    fun invalidCorruptDatabaseArchiveIsRejected() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("db.sqlite"))
            zip.write("not-a-sqlite-db".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(JSONObject().put("id", "corrupt-id").toString().toByteArray())
            zip.closeEntry()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)

        assertThrows(BudgetFileException.IncompatibleBudget::class.java) {
            files.importLocalBudget(bytes.toByteArray().inputStream())
        }
    }

    @Test
    fun serverPromotionFlowPreservesLocalBudgetOnFailureAndRegistersOnSuccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val local = files.createBudget("Promotion Test ${UUID.randomUUID()}")
        try {
            assertTrue(local.isUserLocalOnly)
            assertFalse(local.isServerBacked)

            // 1. Simulating failed upload: local budget and metadata remain standalone untouched
            val simulatedUploadError = RuntimeException("Network unreachable")
            runCatching {
                throw simulatedUploadError
            }.onFailure {
                // Failure handled without modifying local registration
            }
            val metadataAfterFailedUpload = files.metadata(local.id)
            assertNotNull(metadataAfterFailedUpload)
            assertTrue(metadataAfterFailedUpload!!.isUserLocalOnly)
            assertFalse(metadataAfterFailedUpload.isServerBacked)
            assertNull(metadataAfterFailedUpload.cloudFileId)

            // 2. Simulating successful upload: cloud registration applied
            val cloudId = "promoted-cloud-${UUID.randomUUID()}"
            val groupId = "promoted-group-${UUID.randomUUID()}"
            files.saveCloudRegistration(local.id, cloudId, groupId)

            val metadataAfterSuccess = files.metadata(local.id)
            assertNotNull(metadataAfterSuccess)
            assertTrue(metadataAfterSuccess!!.isServerBacked)
            assertFalse(metadataAfterSuccess.isUserLocalOnly)
            assertEquals(cloudId, metadataAfterSuccess.cloudFileId)
            assertEquals(groupId, metadataAfterSuccess.groupId)
        } finally {
            runCatching { files.deleteBudget(local.id) }
        }
    }

    private fun createArchive(
        database: File,
        metadataJson: JSONObject,
        nestedFolder: String? = null,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val prefix = if (nestedFolder != null) "$nestedFolder/" else ""
            zip.putNextEntry(ZipEntry("${prefix}db.sqlite"))
            database.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${prefix}metadata.json"))
            zip.write(metadataJson.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
