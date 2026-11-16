package com.azimulkabir.actua.data.budget.webdav

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebDavReceiptArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun archive(vararg entries: Pair<String, String>): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test fun roundTripIncludesReceiptsAndKeepsBudgetEntries() {
        val budget = archive("db.sqlite" to "database", "metadata.json" to "metadata")
        val bundle = temporary.newFile()
        WebDavReceiptArchive.write(budget, mapOf("txn-1" to { ByteArrayInputStream("jpeg".toByteArray()) }), bundle)
        val photos = temporary.newFolder()
        WebDavReceiptArchive.extract(bundle, photos)
        assertEquals("jpeg", File(photos, "txn-1.jpg").readText())
        java.util.zip.ZipFile(bundle).use { zip ->
            assertEquals("database", zip.getInputStream(zip.getEntry("db.sqlite")).reader().readText())
            assertEquals("metadata", zip.getInputStream(zip.getEntry("metadata.json")).reader().readText())
        }
        assertEquals(listOf("txn-1.jpg"), photos.listFiles()!!.map { it.name })
    }

    @Test fun encryptedContainerRecoversReceiptBytes() {
        val bundle = temporary.newFile()
        val original = archive("db.sqlite" to "db", "metadata.json" to "{}")
        WebDavReceiptArchive.write(original, mapOf("txn" to { "receipt".byteInputStream() }), bundle)
        val encrypted = temporary.newFile()
        val recovered = temporary.newFile()
        val passphrase = "synthetic test password".toCharArray()
        bundle.inputStream().use { input -> encrypted.outputStream().use { output ->
            com.azimulkabir.actua.data.budget.gdrive.EncryptedBackupArchive.encrypt(input, output, passphrase)
        } }
        encrypted.inputStream().use { input -> recovered.outputStream().use { output ->
            com.azimulkabir.actua.data.budget.gdrive.EncryptedBackupArchive.decrypt(input, output, passphrase)
        } }
        val photos = temporary.newFolder()
        WebDavReceiptArchive.extract(recovered, photos)
        assertEquals("receipt", File(photos, "txn.jpg").readText())
    }

    @Test fun legacyBudgetArchiveHasNoPhotos() {
        val photos = temporary.newFolder()
        WebDavReceiptArchive.extract(archive("db.sqlite" to "db", "metadata.json" to "{}"), photos)
        assertTrue(photos.listFiles()!!.isEmpty())
    }

    @Test fun rejectsTraversalNestedPhotosAndCaseDuplicates() {
        listOf("../escape.jpg", "ReceiptPhotos/../escape.jpg", "ReceiptPhotos/nested/a.jpg", "ReceiptPhotos/...jpg").forEach { name ->
            val photos = temporary.newFolder()
            assertTrue(runCatching { WebDavReceiptArchive.extract(archive(name to "bad"), photos) }.isFailure)
            assertFalse(File(photos.parentFile, "escape.jpg").exists())
        }
        val duplicate = archive("ReceiptPhotos/a.jpg" to "one", "ReceiptPhotos/A.jpg" to "two")
        assertTrue(runCatching { WebDavReceiptArchive.extract(duplicate, temporary.newFolder()) }.isFailure)
    }

    @Test fun rejectsOversizedEntriesAndAmbiguousBudgetEntries() {
        val oversized = archive("ReceiptPhotos/a.jpg" to "small")
        val bytes = oversized.readBytes()
        val central = bytes.indices.first { i -> i + 3 < bytes.size &&
            bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() &&
            bytes[i + 2] == 0x01.toByte() && bytes[i + 3] == 0x02.toByte()
        }
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(central + 24, Int.MAX_VALUE)
        oversized.writeBytes(bytes)
        assertTrue(runCatching { WebDavReceiptArchive.extract(oversized, temporary.newFolder()) }.isFailure)
        val ambiguous = archive("db.sqlite" to "one", "nested/db.sqlite" to "two", "metadata.json" to "{}")
        assertTrue(runCatching { WebDavReceiptArchive.extract(ambiguous, temporary.newFolder()) }.isFailure)
    }

    @Test fun invalidZipAndMissingBudgetEntriesCannotBeUploaded() {
        val invalid = temporary.newFile().apply { writeText("not a zip") }
        assertTrue(runCatching { WebDavReceiptArchive.extract(invalid, temporary.newFolder()) }.isFailure)
        assertTrue(runCatching {
            WebDavReceiptArchive.write(archive("metadata.json" to "{}"), emptyMap(), temporary.newFile())
        }.isFailure)
    }
}
