package com.azimulkabir.actua.data.budget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
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
    @Synchronized
    fun makeBackup(budgetId: String, now: Instant = Instant.now()) {
        val directory = files.backupsDirectory(budgetId)
        directory.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }.forEach(File::delete)
        val snapshot = File(directory, "db.${now.toEpochMilli()}.sqlite.tmp")
        try {
            snapshot(files.databaseFile(budgetId), snapshot)
            cleanSnapshot(snapshot)
            files.writeArchive(snapshot, files.metadataFile(budgetId), File(directory, archiveName(now)))
            files.latestDatabaseFile(budgetId).delete()
            files.latestMetadataFile(budgetId).delete()
            prune(budgetId, now)
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
        val database = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val escaped = destination.path.replace("'", "''")
            database.execSQL("VACUUM INTO '$escaped'")
        } finally {
            database.close()
        }
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
            .forEach { File(files.backupsDirectory(budgetId), it).delete() }
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
