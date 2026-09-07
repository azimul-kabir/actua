package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertThrows
import org.junit.Test

class BudgetArchivePolicyTest {
    @Test
    fun acceptsActualArchiveEntries() {
        BudgetArchivePolicy.validateEntryName("db.sqlite", 100)
        BudgetArchivePolicy.validateEntryName("folder/metadata.json", 100)
        BudgetArchivePolicy.validateEntryName("a..b/extra", 100)
    }

    @Test
    fun rejectsTraversalAndPlatformSpecificAbsolutePaths() {
        listOf("../db.sqlite", "folder/../db.sqlite", "/db.sqlite", "C:/db.sqlite", "a\\db.sqlite")
            .forEach { name ->
                assertThrows(BudgetFileException.UnsafeArchive::class.java) {
                    BudgetArchivePolicy.validateEntryName(name, 100)
                }
            }
    }

    @Test
    fun rejectsOversizedEntries() {
        assertThrows(BudgetFileException.UnsafeArchive::class.java) {
            BudgetArchivePolicy.validateEntryName(
                "db.sqlite",
                BudgetArchivePolicy.MAX_ARCHIVE_BYTES + 1,
            )
        }
    }
}
