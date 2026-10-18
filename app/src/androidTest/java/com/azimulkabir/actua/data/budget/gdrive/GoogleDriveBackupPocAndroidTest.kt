package com.azimulkabir.actua.data.budget.gdrive

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetOpenProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GoogleDriveBackupPocAndroidTest {

    @Test
    fun endToEndProofOfConceptEncryptUploadDownloadDecryptAndValidate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Drive PoC ${UUID.randomUUID()}")
        val passphrase = "poc-test-passphrase-2026".toCharArray()

        val tempDir = File(context.cacheDir, "gdrive-poc-${UUID.randomUUID()}").also { it.mkdirs() }
        val rawArchiveFile = File(tempDir, "raw-backup.zip")
        val encryptedArchiveFile = File(tempDir, "backup.actuabkp")
        val downloadedArchiveFile = File(tempDir, "downloaded.zip")

        try {
            // 1. Generate unencrypted zip archive from live budget
            FileOutputStream(rawArchiveFile).use { out ->
                files.writeArchive(budget.id, out)
            }
            assertTrue(rawArchiveFile.exists() && rawArchiveFile.length() > 0)

            // 2. Client-side authenticated encryption (before cloud upload)
            rawArchiveFile.inputStream().use { input ->
                FileOutputStream(encryptedArchiveFile).use { output ->
                    EncryptedBackupArchive.encrypt(input, output, passphrase)
                }
            }
            assertTrue(encryptedArchiveFile.exists() && encryptedArchiveFile.length() > 0)

            // 3. Fast canary validation
            encryptedArchiveFile.inputStream().use { input ->
                assertTrue(EncryptedBackupArchive.validatePassphrase(input, passphrase))
            }
            encryptedArchiveFile.inputStream().use { input ->
                assertFalse(EncryptedBackupArchive.validatePassphrase(input, "invalid-passphrase".toCharArray()))
            }

            // 4. Simulate Drive upload and download stream round-trip
            val simulatedCloudStorage = ByteArrayOutputStream()
            encryptedArchiveFile.inputStream().use { it.copyTo(simulatedCloudStorage) }

            val downloadedBytes = simulatedCloudStorage.toByteArray()
            assertEquals(encryptedArchiveFile.length(), downloadedBytes.size.toLong())

            // 5. Decrypt downloaded stream
            ByteArrayInputStream(downloadedBytes).use { encryptedIn ->
                FileOutputStream(downloadedArchiveFile).use { decryptedOut ->
                    EncryptedBackupArchive.decrypt(encryptedIn, decryptedOut, passphrase)
                }
            }
            assertTrue(downloadedArchiveFile.exists() && downloadedArchiveFile.length() > 0)

            // 6. Validate downloaded and decrypted backup archive with core database probes
            val (decryptedDb, metadata) = files.extractBackup(downloadedArchiveFile)
            try {
                assertEquals(budget.id, metadata.id)
                ActualBudgetDatabase.validate(decryptedDb)
                BudgetOpenProbe.validate(decryptedDb)
            } finally {
                decryptedDb.delete()
                files.metadataFile(metadata.id).delete()
            }
        } finally {
            runCatching { files.deleteBudget(budget.id) }
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun restoreAsStandaloneBudgetGeneratesValidBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Drive Standalone PoC ${UUID.randomUUID()}")
        val passphrase = "poc-test-passphrase-2026".toCharArray()

        val tempDir = File(context.cacheDir, "gdrive-standalone-${UUID.randomUUID()}").also { it.mkdirs() }
        val rawArchiveFile = File(tempDir, "raw-backup.zip")
        val encryptedArchiveFile = File(tempDir, "backup.actuabkp")
        val decryptedArchiveFile = File(tempDir, "decrypted.zip")

        var importedBudgetId: String? = null
        try {
            FileOutputStream(rawArchiveFile).use { out ->
                files.writeArchive(budget.id, out)
            }

            rawArchiveFile.inputStream().use { input ->
                FileOutputStream(encryptedArchiveFile).use { output ->
                    EncryptedBackupArchive.encrypt(input, output, passphrase)
                }
            }

            encryptedArchiveFile.inputStream().use { input ->
                FileOutputStream(decryptedArchiveFile).use { output ->
                    EncryptedBackupArchive.decrypt(input, output, passphrase)
                }
            }

            val imported = decryptedArchiveFile.inputStream().use { stream ->
                files.importLocalBudget(stream)
            }
            importedBudgetId = imported.id
            assertTrue(imported.isUserLocalOnly)
            assertTrue(files.databaseFile(imported.id).isFile)
            ActualBudgetDatabase.validate(files.databaseFile(imported.id))
            BudgetOpenProbe.validate(files.databaseFile(imported.id))
        } finally {
            importedBudgetId?.let { runCatching { files.deleteBudget(it) } }
            runCatching { files.deleteBudget(budget.id) }
            tempDir.deleteRecursively()
        }
    }
}
