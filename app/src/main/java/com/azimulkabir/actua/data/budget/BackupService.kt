package com.azimulkabir.actua.data.budget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class BackupItem {
    data object Latest : BackupItem()
    data class Archive(val id: String, val modifiedAt: Instant) : BackupItem()
}

/** Android port of iOS BackupService, including retention and one-shot revert. */
class BackupService(
    private val context: Context,
    private val files: BudgetFileManager = BudgetFileManager(context),
) {
    private val destinations = BackupDestinationManager(context)

    fun makeBackup(budgetId: String, now: Instant = Instant.now()): File = synchronized(LOCK) {
        val directory = files.backupsDirectory(budgetId)
        directory.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }.forEach(File::delete)
        val snapshot = File(directory, "db.${now.toEpochMilli()}.sqlite.tmp")
        try {
            snapshot(files.databaseFile(budgetId), snapshot)
            cleanSnapshot(snapshot)
            val archive = File(directory, archiveName(now))
            files.writeArchive(snapshot, files.metadataFile(budgetId), archive)
            files.latestDatabaseFile(budgetId).delete()
            files.latestMetadataFile(budgetId).delete()
            prune(budgetId, now)
            runCatching { destinations.mirror(budgetId, archive) }
            runCatching {
                com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupScheduler.scheduleUpload(context, budgetId)
            }
            runCatching {
                com.azimulkabir.actua.data.budget.webdav.WebDavBackupScheduler.scheduleUpload(context, budgetId)
            }
            archive
        } finally {
            snapshot.delete()
        }
    }

    fun availableBackups(budgetId: String): List<BackupItem> = buildList {
        if (files.latestDatabaseFile(budgetId).isFile && files.latestMetadataFile(budgetId).isFile) {
            add(BackupItem.Latest)
        }
        addAll(archives(budgetId).map { BackupItem.Archive(it.id, it.modifiedAt) })
    }

    fun archiveFile(budgetId: String, backupId: String): File {
        require('/' !in backupId && '\\' !in backupId && backupId.endsWith(".zip")) { "Invalid backup id" }
        return File(files.backupsDirectory(budgetId), backupId).also {
            require(it.isFile) { "Backup $backupId no longer exists" }
        }
    }

    fun mirrorExisting(budgetId: String) = destinations.mirrorExisting(
        budgetId, archives(budgetId).map { File(files.backupsDirectory(budgetId), it.id) },
    )

    /** Validates an external archive before adding it to this budget's managed backup list. */
    fun importArchive(budgetId: String, input: InputStream, now: Instant = Instant.now()): File = synchronized(LOCK) {
        val directory = files.backupsDirectory(budgetId)
        val temporary = File(directory, ".import-${now.toEpochMilli()}.zip.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > BudgetArchivePolicy.MAX_ARCHIVE_BYTES) {
                        throw BudgetFileException.UnsafeArchive("file exceeds 500 MB")
                    }
                    output.write(buffer, 0, count)
                }
            }

            val (validatedDatabase, metadata) = files.extractBackup(temporary)
            validatedDatabase.delete()
            if (metadata.id != budgetId) throw BudgetFileException.IncompatibleBudget

            val destination = uniqueArchiveFile(directory, now)
            check(temporary.renameTo(destination)) { "Unable to add the imported backup" }
            prune(budgetId, now)
            runCatching { destinations.mirror(budgetId, destination) }
            runCatching {
                com.azimulkabir.actua.data.budget.gdrive.GoogleDriveBackupScheduler.scheduleUpload(context, budgetId)
            }
            runCatching {
                com.azimulkabir.actua.data.budget.webdav.WebDavBackupScheduler.scheduleUpload(context, budgetId)
            }
            destination
        } finally {
            temporary.delete()
        }
    }

    /**
     * Replaces [budgetId]'s live files with a backup, or reverts to the pre-restore copy for
     * [LATEST_ID]. Runs while sync is paused and no backup of any budget is being made, and swaps the
     * files in by rename (see [BudgetFileManager.replaceLiveFiles]), so an interrupted restore leaves
     * either the old budget or the restored one. Callers must close their own handle on the budget.
     */
    fun restore(budgetId: String, backupId: String) = ActualSyncRunner.whileSyncPaused {
        synchronized(LOCK) { restoreLocked(budgetId, backupId) }
    }

    private fun restoreLocked(budgetId: String, backupId: String) {
        val liveDb = files.databaseFile(budgetId)
        val liveMetadata = files.metadataFile(budgetId)
        val latestDb = files.latestDatabaseFile(budgetId)
        val latestMetadata = files.latestMetadataFile(budgetId)
        if (backupId == LATEST_ID) {
            require(latestDb.isFile && latestMetadata.isFile) { "Backup $backupId no longer exists" }
            // The revert point is used up: its files become the live budget.
            files.replaceLiveFiles(budgetId, latestDb, latestMetadata)
            return
        }
        val archive = File(files.backupsDirectory(budgetId), backupId)
        require(archive.isFile) { "Backup $backupId no longer exists" }
        if (!latestDb.isFile) saveRevertPoint(liveDb, liveMetadata, latestDb, latestMetadata)

        val directory = files.budgetDirectory(budgetId)
        val stagedDb = File(directory, ".restore-${UUID.randomUUID()}.sqlite")
        val stagedMetadata = File(directory, ".restore-${UUID.randomUUID()}.json")
        val (restoredDb, restoredMetadata) = files.extractBackup(archive)
        try {
            val live = BudgetMetadata.fromJson(JSONObject(liveMetadata.readText()))
            // Staged next to the live files so the swap is a rename on the same filesystem.
            restoredDb.copyTo(stagedDb)
            val json = JSONObject()
                .put("id", restoredMetadata.id)
                .putNullable("budgetName", restoredMetadata.budgetName)
                .putNullable("cloudFileId", live.cloudFileId ?: restoredMetadata.cloudFileId)
                .put("groupId", JSONObject.NULL)
                .putNullable("resetClock", restoredMetadata.resetClock)
                .put("lastUploaded", JSONObject.NULL)
                .putNullable("encryptKeyId", live.encryptKeyId ?: restoredMetadata.encryptKeyId)
            stagedMetadata.writeText(json.toString())
            files.replaceLiveFiles(budgetId, stagedDb, stagedMetadata)
        } finally {
            restoredDb.delete()
            stagedDb.delete()
            stagedMetadata.delete()
        }
    }

    /** Written to temporary files and renamed, so an interrupted write can't leave a partial revert point. */
    private fun saveRevertPoint(liveDb: File, liveMetadata: File, latestDb: File, latestMetadata: File) {
        val temporaryDb = File(latestDb.path + ".tmp")
        val temporaryMetadata = File(latestMetadata.path + ".tmp")
        try {
            latestMetadata.delete()
            snapshot(liveDb, temporaryDb)
            liveMetadata.copyTo(temporaryMetadata, overwrite = true)
            check(temporaryDb.renameTo(latestDb)) { "Unable to save the revert point" }
            check(temporaryMetadata.renameTo(latestMetadata)) { "Unable to save the revert point" }
        } catch (error: Exception) {
            latestDb.delete()
            latestMetadata.delete()
            throw error
        } finally {
            temporaryDb.delete()
            temporaryMetadata.delete()
        }
    }

    private fun snapshot(source: File, destination: File) {
        destination.delete()
        val database = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val version = database.rawQuery("SELECT sqlite_version()", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (supportsVacuumInto(version)) {
                val escaped = destination.path.replace("'", "''")
                database.execSQL("VACUUM INTO '$escaped'")
                return
            }
            // Android 9 and 10 ship SQLite 3.22, before VACUUM INTO (3.27) was introduced.
            // Force a full checkpoint and copy the database once this connection is closed so
            // the snapshot remains self-contained.
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                if (cursor.moveToFirst()) Unit
            }
        } finally {
            database.close()
        }
        source.copyTo(destination, overwrite = true)
    }

    private fun cleanSnapshot(file: File) {
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.beginTransaction()
            if (hasTable(database, "messages_crdt")) database.delete("messages_crdt", null, null)
            if (hasTable(database, "messages_clock")) database.delete("messages_clock", null, null)
            if (hasTable(database, "__migrations__")) {
                database.delete(
                    "__migrations__",
                    "id IN (${ActualBudgetDatabase.PRIVATE_MIGRATION_IDS.joinToString()})",
                    null,
                )
            }
            database.setTransactionSuccessful()
        } finally {
            if (database.inTransaction()) database.endTransaction()
            database.close()
        }
    }

    private fun hasTable(database: SQLiteDatabase, table: String): Boolean =
        database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.moveToFirst() }

    private data class Archive(val id: String, val modifiedAt: Instant)
    private fun archives(budgetId: String): List<Archive> = files.backupsDirectory(budgetId)
        .listFiles().orEmpty().filter { it.isFile && it.extension == "zip" }
        .map { Archive(it.name, Instant.ofEpochMilli(it.lastModified())) }
        .sortedByDescending(Archive::modifiedAt)

    private fun prune(budgetId: String, today: Instant) {
        val archives = archives(budgetId)
        backupsToRemove(archives.map { DatedBackup(it.id, it.modifiedAt) }, today)
            .forEach {
                File(files.backupsDirectory(budgetId), it).delete()
                destinations.removeMirror(budgetId, it)
            }
    }

    private fun uniqueArchiveFile(directory: File, instant: Instant): File {
        val name = archiveName(instant)
        var candidate = File(directory, name)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "${name.removeSuffix(".zip")}-import-${suffix++}.zip")
        }
        return candidate
    }

    companion object {
        const val LATEST_ID = "db.latest.sqlite"

        /**
         * One lock for every [BackupService] instance: the sync runner, the scheduled backup workers
         * and the UI each create their own, and a backup must never run while a restore swaps files.
         */
        private val LOCK = Any()
        private val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        private val VACUUM_INTO_MINIMUM = listOf(3, 27, 0)

        /** True when [sqliteVersion] (from `sqlite_version()`) supports `VACUUM INTO`. */
        fun supportsVacuumInto(sqliteVersion: String?): Boolean {
            val parts = sqliteVersion?.trim()?.split('.')?.map { it.toIntOrNull() ?: return false }
                ?: return false
            if (parts.isEmpty()) return false
            for (index in VACUUM_INTO_MINIMUM.indices) {
                val part = parts.getOrElse(index) { 0 }
                if (part != VACUUM_INTO_MINIMUM[index]) return part > VACUUM_INTO_MINIMUM[index]
            }
            return true
        }

        fun archiveName(instant: Instant): String = "${formatter.format(Date.from(instant))}.zip"
        data class DatedBackup(val id: String, val date: Instant)

        fun backupsToRemove(
            backups: List<DatedBackup>, today: Instant, zone: ZoneId = ZoneId.systemDefault(),
        ): List<String> {
            val todayDate = today.atZone(zone).toLocalDate()
            val removed = mutableListOf<String>()
            backups.groupBy { it.date.atZone(zone).toLocalDate() }.forEach { (day, values) ->
                removed += values.drop(if (day == todayDate) 3 else 1).map(DatedBackup::id)
            }
            val remaining = backups.filterNot { it.id in removed }
            removed += remaining.drop(10).map(DatedBackup::id)
            return removed
        }
    }
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
