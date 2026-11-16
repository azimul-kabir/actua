package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.budget.BudgetArchivePolicy
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** WebDAV extends the standard budget ZIP with receipt files, before ACTUABKP encryption. */
internal object WebDavReceiptArchive {
    private const val PREFIX = "ReceiptPhotos/"
    private val photoName = Regex("[A-Za-z0-9._-]{1,128}\\.jpg")

    fun write(budget: File, photos: Map<String, () -> InputStream>, output: File) {
        var total = 0L
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            ZipFile(budget).use { source ->
                for (name in listOf("db.sqlite", "metadata.json")) {
                    val entry = source.getEntry(name) ?: error("Missing $name")
                    zip.putNextEntry(ZipEntry(name))
                    source.getInputStream(entry).use { total = copy(it, zip, total) }
                    zip.closeEntry()
                }
            }
            val seenPhotos = mutableSetOf<String>()
            photos.forEach { (id, open) ->
                val name = "$id.jpg"
                require(validPhotoName(name)) { "Invalid receipt id" }
                require(seenPhotos.add(name.lowercase())) { "Duplicate receipt id" }
                zip.putNextEntry(ZipEntry(PREFIX + name))
                open().use { total = copy(it, zip, total) }
                zip.closeEntry()
            }
        }
    }

    /** Validates the entire ZIP and stages photos before the budget restore can change any live files. */
    fun extract(archive: File, photos: File) {
        check(photos.mkdirs() || photos.isDirectory)
        var total = 0L
        val seen = mutableSetOf<String>()
        val budgetEntries = mutableSetOf<String>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                BudgetArchivePolicy.validateEntryName(entry.name, entry.size)
                require(seen.add(entry.name.lowercase())) { "Duplicate backup entry" }
                val baseName = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && baseName in setOf("db.sqlite", "metadata.json")) {
                    require(budgetEntries.add(baseName)) { "Duplicate budget entry" }
                }
                val receipt = entry.name.startsWith(PREFIX)
                val name = entry.name.removePrefix(PREFIX)
                if (receipt) require(!entry.isDirectory && validPhotoName(name)) { "Invalid receipt entry" }
                zip.getInputStream(entry).use { input ->
                    if (receipt) File(photos, name).outputStream().use { total = copy(input, it, total) }
                    else total = copy(input, DISCARD, total)
                }
            }
        }
        require(budgetEntries == setOf("db.sqlite", "metadata.json")) { "Missing budget entries" }
    }

    private fun validPhotoName(name: String): Boolean =
        photoName.matches(name) && name.removeSuffix(".jpg") !in setOf(".", "..")

    private fun copy(input: InputStream, output: OutputStream, initial: Long): Long {
        var total = initial
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            require(total <= BudgetArchivePolicy.MAX_ARCHIVE_BYTES) { "Backup exceeds 500 MB" }
            output.write(buffer, 0, count)
        }
    }

    private val DISCARD = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}
