package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.budget.BudgetArchivePolicy
import com.azimulkabir.actua.data.budget.backup.BackupStorage
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** WebDAV extends the standard budget ZIP with receipt files, before ACTUABKP encryption. */
internal object WebDavReceiptArchive {
    private const val PREFIX = "ReceiptPhotos/"
    private val photoName = Regex("[A-Za-z0-9._-]{1,128}\\.jpg")

    fun write(budget: File, photos: Map<String, () -> InputStream>, output: File) {
        var budgetBytes = 0L
        ZipOutputStream(BackupStorage.open(output).buffered()).use { zip ->
            ZipFile(budget).use { source ->
                for (name in listOf("db.sqlite", "metadata.json")) {
                    val entry = source.getEntry(name) ?: error("Missing $name")
                    zip.putNextEntry(ZipEntry(name))
                    source.getInputStream(entry).use {
                        budgetBytes += copy(it, zip, BudgetArchivePolicy.MAX_ARCHIVE_BYTES - budgetBytes)
                    }
                    zip.closeEntry()
                }
            }
            val seenPhotos = mutableSetOf<String>()
            photos.forEach { (id, open) ->
                val name = "$id.jpg"
                require(validPhotoName(name)) { "Invalid receipt id" }
                require(seenPhotos.add(name.lowercase())) { "Duplicate receipt id" }
                zip.putNextEntry(ZipEntry(PREFIX + name))
                open().use { copy(it, zip) }
                zip.closeEntry()
            }
        }
    }

    /** Validates the entire ZIP and stages photos before the budget restore can change any live files. */
    fun extract(archive: File, photos: File) {
        check(photos.mkdirs() || photos.isDirectory)
        var budgetBytes = 0L
        val seen = mutableSetOf<String>()
        val budgetEntries = mutableSetOf<String>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val receipt = entry.name.startsWith(PREFIX)
                // Receipt capacity follows available device storage; standard budget data keeps its existing cap.
                BudgetArchivePolicy.validateEntryName(entry.name, if (receipt) 0 else entry.size)
                require(seen.add(entry.name.lowercase())) { "Duplicate backup entry" }
                val baseName = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && baseName in setOf("db.sqlite", "metadata.json")) {
                    require(budgetEntries.add(baseName)) { "Duplicate budget entry" }
                }
                val name = entry.name.removePrefix(PREFIX)
                if (receipt) require(!entry.isDirectory && validPhotoName(name)) { "Invalid receipt entry" }
                require(entry.size >= 0) { "Missing backup entry size" }
                if (receipt) BackupStorage.requireSpace(photos, entry.size)
                val crc = CRC32()
                zip.getInputStream(entry).use { input ->
                    val count = if (receipt) BackupStorage.open(File(photos, name)).use {
                        copy(input, it, entry.size, crc)
                    } else copy(input, DISCARD, minOf(entry.size, BudgetArchivePolicy.MAX_ARCHIVE_BYTES - budgetBytes), crc)
                    require(count == entry.size && crc.value == entry.crc) { "Corrupt backup entry" }
                    if (!receipt) budgetBytes += count
                }
            }
        }
        require(budgetEntries == setOf("db.sqlite", "metadata.json")) { "Missing budget entries" }
    }

    private fun validPhotoName(name: String): Boolean =
        photoName.matches(name) && name.removeSuffix(".jpg") !in setOf(".", "..")

    private fun copy(input: InputStream, output: OutputStream, maximum: Long = Long.MAX_VALUE, crc: CRC32? = null): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            require(count.toLong() <= maximum - total) { "Budget data exceeds its import limit or entry size is invalid" }
            total += count
            crc?.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }

    private val DISCARD = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}
