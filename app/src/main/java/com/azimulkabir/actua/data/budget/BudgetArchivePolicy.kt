package com.azimulkabir.actua.data.budget

internal object BudgetArchivePolicy {
    const val MAX_ARCHIVE_BYTES = 500L * 1024 * 1024
    private val drivePrefix = Regex("^[A-Za-z]:")

    fun validateEntryName(name: String, uncompressedSize: Long) {
        val segments = name.split('/')
        if ('\u0000' in name || '\\' in name || name.startsWith('/') ||
            drivePrefix.containsMatchIn(name) || ".." in segments
        ) {
            throw BudgetFileException.UnsafeArchive("unsafe entry name $name")
        }
        if (uncompressedSize > MAX_ARCHIVE_BYTES) {
            throw BudgetFileException.UnsafeArchive("entry $name is too large")
        }
    }
}
