package com.azimulkabir.actua.data.budget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
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

sealed class BackupItem {
    data object Latest : BackupItem()
    data class Archive(val id: String, val modifiedAt: Instant) : BackupItem()
}

/** Android port of iOS BackupService, including retention and one-shot revert. */
class BackupService(context: Context, private val files: BudgetFileManager = BudgetFileManager(context)) {
    private val destinations = BackupDestinationManager(context)

    @Synchronized
    fun makeBackup(budgetId: String, now: Instant = Instant.now()): File {
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
            return archive
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
    @Synchronized
    fun importArchive(budgetId: String, input: InputStream, now: Instant = Instant.now()): File {
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
            return destination
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun restore(budgetId: String, backupId: String) {
        val liveDb = files.databaseFile(budgetId)
        val liveMetadata = files.metadataFile(budgetId)
        val latestDb = files.latestDatabaseFile(budgetId)
        val latestMetadata = files.latestMetadataFile(budgetId)
        if (backupId == LATEST_ID) {
            require(latestDb.isFile && latestMetadata.isFile) { "Backup $backupId no longer exists" }
            replace(liveDb, latestDb)
            replace(liveMetadata, latestMetadata)
            latestDb.delete(); latestMetadata.delete()
            return
        }
        val archive = File(files.backupsDirectory(budgetId), backupId)
        require(archive.isFile) { "Backup $backupId no longer exists" }
        if (!latestDb.isFile) {
            latestMetadata.delete()
            liveMetadata.copyTo(latestMetadata, overwrite = true)
            snapshot(liveDb, latestDb)
        }
        val (restoredDb, restoredMetadata) = files.extractBackup(archive)
        try {
            val live = BudgetMetadata.fromJson(JSONObject(liveMetadata.readText()))
            removeSidecars(liveDb)
            replace(liveDb, restoredDb)
            val json = JSONObject()
                .put("id", restoredMetadata.id)
                .putNullable("budgetName", restoredMetadata.budgetName)
                .putNullable("cloudFileId", live.cloudFileId ?: restoredMetadata.cloudFileId)
                .put("groupId", JSONObject.NULL)
                .putNullable("resetClock", restoredMetadata.resetClock)
                .put("lastUploaded", JSONObject.NULL)
                .putNullable("encryptKeyId", live.encryptKeyId ?: restoredMetadata.encryptKeyId)
            liveMetadata.writeText(json.toString())
        } finally {
            restoredDb.delete()
        }
    }

    private fun snapshot(source: File, destination: File) {
        destination.delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val database = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                val escaped = destination.path.replace("'", "''")
                database.execSQL("VACUUM INTO '$escaped'")
            } finally {
                database.close()
            }
            return
        }

        // Android 9 ships SQLite 3.22, before VACUUM INTO was introduced. Close any
        // connection opened here and copy the database plus committed WAL contents
        // after forcing a full checkpoint so the snapshot remains self-contained.
        val database = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
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
                database.delete("__migrations__", "id IN (${ACTUALI_MIGRATIONS.joinToString()})", null)
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

    private fun replace(destination: File, source: File) {
        removeSidecars(destination)
        destination.delete()
        source.copyTo(destination, overwrite = true)
    }

    private fun removeSidecars(database: File) {
        listOf("-journal", "-wal", "-shm").forEach { File(database.path + it).delete() }
    }

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
        private val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        private val ACTUALI_MIGRATIONS = longArrayOf(
            1765518577216, 1694438752001, 1694438752002, 1720665000001,
            1770000000001, 1770000000002, 1778510362741, 1780606214999,
            1780606215002, 1780606215003, 1780606215004, 1770000000003,
        )

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
