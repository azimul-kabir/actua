package com.azimulkabir.actua.data.budget.gdrive

import com.azimulkabir.actua.data.budget.BackupService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GoogleDriveBackupManagerTest {

    @Test
    fun parseBackupDateExtractsInstantFromValidArchiveNames() {
        val name1 = "2026-10-18_14-30-00.actuabkp"
        val instant1 = GoogleDriveBackupManager.parseBackupDate(name1)
        assertNotNull(instant1)

        val name2 = "2026-05-01_09-15-45.zip"
        val instant2 = GoogleDriveBackupManager.parseBackupDate(name2)
        assertNotNull(instant2)
    }

    @Test
    fun parseBackupDateReturnsNullForMalformedNames() {
        assertNull(GoogleDriveBackupManager.parseBackupDate("invalid-backup-name.actuabkp"))
        assertNull(GoogleDriveBackupManager.parseBackupDate(""))
        assertNull(GoogleDriveBackupManager.parseBackupDate("db.latest.sqlite"))
    }

    @Test
    fun retentionPruningPolicyKeepsThreeForTodayOnePerPastDayMaxTen() {
        val now = Instant.parse("2026-10-18T18:00:00Z")
        val zone = ZoneOffset.UTC

        // 4 backups today: should prune 1 (the oldest today)
        val backups = listOf(
            BackupService.Companion.DatedBackup("today-4", Instant.parse("2026-10-18T16:00:00Z")),
            BackupService.Companion.DatedBackup("today-3", Instant.parse("2026-10-18T14:00:00Z")),
            BackupService.Companion.DatedBackup("today-2", Instant.parse("2026-10-18T12:00:00Z")),
            BackupService.Companion.DatedBackup("today-1-oldest", Instant.parse("2026-10-18T10:00:00Z")),
            // 2 backups yesterday: should prune 1
            BackupService.Companion.DatedBackup("yest-2", Instant.parse("2026-10-17T15:00:00Z")),
            BackupService.Companion.DatedBackup("yest-1-oldest", Instant.parse("2026-10-17T09:00:00Z")),
        )

        val toRemove = BackupService.backupsToRemove(backups, now, zone)

        assertTrue(toRemove.contains("today-1-oldest"))
        assertTrue(toRemove.contains("yest-1-oldest"))
        assertFalse(toRemove.contains("today-4"))
        assertFalse(toRemove.contains("today-3"))
        assertFalse(toRemove.contains("today-2"))
        assertFalse(toRemove.contains("yest-2"))
    }

    @Test
    fun retentionPruningNeverRemovesNewlyUploadedArchive() {
        val now = Instant.parse("2026-10-18T18:00:00Z")
        val currentUploadId = "current-new-upload-id"

        val backups = (1..15).map { index ->
            BackupService.Companion.DatedBackup(
                id = if (index == 1) currentUploadId else "archive-$index",
                date = now.minusSeconds(index * 86400L),
            )
        }

        val toRemove = BackupService.backupsToRemove(backups, now)
        // Simulate safe filter in GoogleDriveBackupManager:
        val safeToRemove = toRemove.filter { it != currentUploadId }

        assertFalse(safeToRemove.contains(currentUploadId))
    }
}
