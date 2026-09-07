package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BackupRetentionTest {
    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int = 0) = Instant.parse("2017-01-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z")

    @Test fun keepsThreeToday() {
        val rows = (1..4).map { BackupService.Companion.DatedBackup("backup$it", at(1, it)) }
        assertEquals(listOf("backup4"), BackupService.backupsToRemove(rows, at(1, 12), zone))
    }

    @Test fun keepsOnePerPriorDayAndTenTotal() {
        val rows = (1..12).map { BackupService.Companion.DatedBackup("backup$it", at(13 - it)) }
        assertEquals(setOf("backup11", "backup12"),
            BackupService.backupsToRemove(rows, at(31), zone).toSet())
    }

    @Test fun localMidnightCreatesSeparateBuckets() {
        val rows = listOf(
            BackupService.Companion.DatedBackup("today", Instant.parse("2017-01-02T00:00:00Z")),
            BackupService.Companion.DatedBackup("late1", Instant.parse("2017-01-01T23:59:00Z")),
            BackupService.Companion.DatedBackup("late2", Instant.parse("2017-01-01T23:58:00Z")),
        )
        assertEquals(listOf("late2"), BackupService.backupsToRemove(rows, rows.first().date, zone))
    }
}
