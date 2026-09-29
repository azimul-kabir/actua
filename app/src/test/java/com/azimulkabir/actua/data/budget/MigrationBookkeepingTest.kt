package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationBookkeepingTest {
    private val upstream = BlankBudgetFactory.MIGRATIONS.toList()

    @Test fun blankBudgetListsActualsMigrationsInOrderWithoutDuplicates() {
        assertEquals(59, upstream.size)
        assertEquals(upstream.sorted().distinct(), upstream)
    }

    @Test fun everyColumnMigrationIdIsEitherActualsOrKnownPrivate() {
        val unknown = ActualBudgetDatabase.columnMigrationIds.filter {
            it !in upstream && it !in ActualBudgetDatabase.PRIVATE_MIGRATION_IDS
        }
        assertEquals("record upstream ids only, or add the id to PRIVATE_MIGRATION_IDS", emptyList<Long>(), unknown)
    }

    @Test fun privateMigrationIdsNeverOverlapActualsList() {
        assertTrue(ActualBudgetDatabase.PRIVATE_MIGRATION_IDS.none { it in upstream })
    }

    @Test fun vacuumIntoNeedsSqlite327() {
        assertFalse(BackupService.supportsVacuumInto("3.22.0")) // Android 9 and 10
        assertFalse(BackupService.supportsVacuumInto("3.26.9"))
        assertTrue(BackupService.supportsVacuumInto("3.27.0"))
        assertTrue(BackupService.supportsVacuumInto("3.27"))
        assertTrue(BackupService.supportsVacuumInto("3.28.0")) // Android 11
        assertTrue(BackupService.supportsVacuumInto("3.44.3"))
        assertTrue(BackupService.supportsVacuumInto("4.0.0"))
        assertFalse(BackupService.supportsVacuumInto(null))
        assertFalse(BackupService.supportsVacuumInto(""))
        assertFalse(BackupService.supportsVacuumInto("unknown"))
    }
}
