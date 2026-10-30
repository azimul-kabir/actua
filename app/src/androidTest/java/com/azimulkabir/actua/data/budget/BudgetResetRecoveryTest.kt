package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.sync.EncryptionKeyException
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.LoadedEncryptionKey
import com.azimulkabir.actua.data.sync.SyncEncryption
import com.azimulkabir.actua.data.sync.SyncEncryptionException
import com.azimulkabir.actua.data.sync.SyncedLogMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Recovery after the server reports `file-has-reset` (actua#1343). */
class BudgetResetRecoveryTest {
    @Test
    fun unsyncedChangesCountsOnlyEditsAfterTheLastRecordedSync() = withLocalBudget { env ->
        assertNull("without a recorded sync the count is unknown", env.recovery.unsyncedChanges(env.local.id))

        env.markSynced()
        assertEquals(0, env.recovery.unsyncedChanges(env.local.id))

        env.editOffline("Edited offline")
        assertTrue(env.recovery.unsyncedChanges(env.local.id)!! > 0)
    }

    @Test
    fun recoveryBacksUpFirstThenReplacesTheBudgetInPlace() = withLocalBudget { env ->
        env.markSynced()
        env.editOffline("Edited offline")
        val unsynced = env.recovery.unsyncedChanges(env.local.id)
        assertTrue(unsynced != null && unsynced > 0)
        val serverArchive = env.serverArchive("Server name")
        val requests = mutableListOf<String>()
        val recovery = env.recoveryWith(ActualServerClient { request ->
            requests += request.url.path
            if (request.url.path.endsWith("get-user-file-info")) {
                ActualHttpResponse(200, env.fileInfo().encodeToByteArray())
            } else {
                ActualHttpResponse(200, serverArchive)
            }
        })

        val result = recovery.recover("https://actual.test", "token", env.local.id)

        // The local id is kept even though the server archive carries another budget id.
        assertEquals(env.local.id, result.metadata.id)
        assertEquals(unsynced, result.unsyncedChanges)
        assertEquals(CLOUD_FILE_ID, result.metadata.cloudFileId)
        assertEquals(CLOUD_FILE_ID, env.files.metadata(env.local.id)?.cloudFileId)
        assertEquals("Server name", env.categoryName(env.files.databaseFile(env.local.id).path))
        assertEquals("the new file has nothing unsynced", 0, env.recovery.unsyncedChanges(env.local.id))
        assertTrue(requests.any { it.endsWith("download-user-file") })

        // The backup written first still holds the offline edit and is listed for restore.
        assertTrue(result.backup.isFile)
        assertTrue(env.backups.availableBackups(env.local.id).any { it is BackupItem.Archive && it.id == result.backup.name })
        val (backupDatabase, backupMetadata) = env.files.extractBackup(result.backup)
        try {
            assertEquals(env.local.id, backupMetadata.id)
            assertEquals("Edited offline", env.categoryName(backupDatabase.path))
        } finally {
            backupDatabase.delete()
        }
    }

    @Test
    fun invalidDownloadLeavesTheLocalBudgetUntouchedAfterBackingUp() = withLocalBudget { env ->
        env.markSynced()
        env.editOffline("Edited offline")
        val before = env.messageLog()
        val recovery = env.recoveryWith(ActualServerClient { request ->
            if (request.url.path.endsWith("get-user-file-info")) {
                ActualHttpResponse(200, env.fileInfo().encodeToByteArray())
            } else {
                ActualHttpResponse(200, "not a zip archive".encodeToByteArray())
            }
        })

        assertThrows(BudgetFileException::class.java) {
            recovery.recover("https://actual.test", "token", env.local.id)
        }

        assertEquals("Edited offline", env.categoryName(env.files.databaseFile(env.local.id).path))
        assertEquals(before, env.messageLog())
        assertEquals(CLOUD_FILE_ID, env.files.metadata(env.local.id)?.cloudFileId)
        assertTrue("a backup is written before any download", env.backups.availableBackups(env.local.id).any { it is BackupItem.Archive })
    }

    @Test
    fun serverFileGoneLeavesTheLocalBudgetUntouched() = withLocalBudget { env ->
        env.editOffline("Edited offline")
        val recovery = env.recoveryWith(ActualServerClient {
            ActualHttpResponse(400, """{"status":"error","reason":"file-not-found"}""".encodeToByteArray())
        })

        assertThrows(ActualServerException.FileNotFound::class.java) {
            recovery.recover("https://actual.test", "token", env.local.id)
        }

        assertEquals("Edited offline", env.categoryName(env.files.databaseFile(env.local.id).path))
        assertTrue(env.files.databaseFile(env.local.id).isFile)
    }

    @Test
    fun newKeyIsVerifiedFirstThenStoredOnlyAfterTheNewFileIsInstalled() = withLocalBudget { env ->
        val keys = BudgetEncryptionKeyStore(env.context)
        try {
            env.useOldKey(keys)
            env.editOffline("Edited offline")
            val recovery = env.recoveryWith(env.encryptedServer(env.serverArchive("Server name")))

            val result = recovery.recover("https://actual.test", "token", env.local.id, KEY_FIXTURE_PASSWORD)

            assertEquals("Server name", env.categoryName(env.files.databaseFile(env.local.id).path))
            assertEquals(KEY_FIXTURE_ID, result.metadata.encryptKeyId)
            assertEquals(KEY_FIXTURE_ID, env.files.metadata(env.local.id)?.encryptKeyId)
            assertEquals(KEY_FIXTURE_ID, keys.load(CLOUD_FILE_ID)?.keyId)
            assertTrue(result.backup.isFile)
        } finally {
            keys.remove(CLOUD_FILE_ID)
        }
    }

    @Test
    fun wrongNewPasswordChangesNothingAndKeepsTheOldKey() = withLocalBudget { env ->
        val keys = BudgetEncryptionKeyStore(env.context)
        try {
            env.useOldKey(keys)
            env.editOffline("Edited offline")
            val before = env.messageLog()
            var downloads = 0
            val recovery = env.recoveryWith(env.encryptedServer(env.serverArchive("Server name")) { downloads++ })

            assertThrows(EncryptionKeyException.InvalidPassword::class.java) {
                recovery.recover("https://actual.test", "token", env.local.id, "not the password")
            }

            assertEquals(0, downloads)
            assertEquals(before, env.messageLog())
            assertEquals("Edited offline", env.categoryName(env.files.databaseFile(env.local.id).path))
            assertEquals(OLD_KEY_ID, keys.load(CLOUD_FILE_ID)?.keyId)
            assertEquals(OLD_KEY_ID, env.files.metadata(env.local.id)?.encryptKeyId)
            assertTrue("no backup is written for a wrong password", env.backups.availableBackups(env.local.id).isEmpty())
        } finally {
            keys.remove(CLOUD_FILE_ID)
        }
    }

    @Test
    fun undecryptableDownloadKeepsTheOldKeyAndTheLocalBudget() = withLocalBudget { env ->
        val keys = BudgetEncryptionKeyStore(env.context)
        try {
            env.useOldKey(keys)
            env.editOffline("Edited offline")
            val recovery = env.recoveryWith(env.encryptedServer(env.serverArchive("Server name"), tamper = true))

            assertThrows(SyncEncryptionException::class.java) {
                recovery.recover("https://actual.test", "token", env.local.id, KEY_FIXTURE_PASSWORD)
            }

            assertEquals("Edited offline", env.categoryName(env.files.databaseFile(env.local.id).path))
            assertEquals(OLD_KEY_ID, keys.load(CLOUD_FILE_ID)?.keyId)
            assertEquals(OLD_KEY_ID, env.files.metadata(env.local.id)?.encryptKeyId)
        } finally {
            keys.remove(CLOUD_FILE_ID)
        }
    }

    @Test
    fun budgetNotLinkedToTheServerIsRefusedWithoutBackupOrRequests() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val local = files.createBudget("LocalOnly ${UUID.randomUUID()}")
        try {
            var requests = 0
            val server = ActualServerClient { requests++; ActualHttpResponse(200, byteArrayOf()) }
            val backups = BackupService(context, files)
            val recovery = BudgetResetRecovery(
                files, backups, BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context)), SyncedLogMarkers(context),
            )

            assertThrows(BudgetRecoveryException.NotServerBacked::class.java) {
                recovery.recover("https://actual.test", "token", local.id)
            }

            assertEquals(0, requests)
            assertTrue(backups.availableBackups(local.id).isEmpty())
        } finally {
            runCatching { files.deleteBudget(local.id) }
        }
    }

    private class Env(
        val files: BudgetFileManager,
        val backups: BackupService,
        val local: BudgetMetadata,
        val context: android.content.Context,
    ) {
        val recovery = recoveryWith(ActualServerClient { ActualHttpResponse(500, byteArrayOf()) })

        fun recoveryWith(server: ActualServerClient) =
            BudgetResetRecovery(
                files, backups, BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context)), SyncedLogMarkers(context),
            )

        fun fileInfo(encryptMeta: String = "null") =
            """{"status":"ok","data":{"fileId":"$CLOUD_FILE_ID","groupId":"new-group","name":"Budget","deleted":0,"encryptMeta":$encryptMeta}}"""

        /** Links the local budget to the old key, as it is when the server reports `file-has-new-key`. */
        fun useOldKey(keys: BudgetEncryptionKeyStore) {
            keys.store(CLOUD_FILE_ID, LoadedEncryptionKey(OLD_KEY_ID, ByteArray(32) { 7 }))
            files.metadataFile(local.id).writeText(
                org.json.JSONObject(files.metadataFile(local.id).readText()).put("encryptKeyId", OLD_KEY_ID).toString(),
            )
        }

        /**
         * A server whose file is now encrypted with the key for [KEY_FIXTURE_PASSWORD]. [downloads] counts
         * downloads; [tamper] corrupts the ciphertext so decrypting it fails.
         */
        fun encryptedServer(plainArchive: ByteArray, tamper: Boolean = false, downloads: () -> Unit = {}): ActualServerClient {
            val key = SyncEncryption.deriveKey(KEY_FIXTURE_PASSWORD, KEY_FIXTURE_SALT)
            val iv = ByteArray(12) { (it + 1).toByte() }
            val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                doFinal(plainArchive)
            }
            val ciphertext = sealed.copyOf(sealed.size - 16).also { if (tamper) it[0] = (it[0] + 1).toByte() }
            val authTag = sealed.copyOfRange(sealed.size - 16, sealed.size)
            val encoder = java.util.Base64.getEncoder()
            val meta = """{"keyId":"$KEY_FIXTURE_ID","algorithm":"aes-256-gcm","iv":"${encoder.encodeToString(iv)}","authTag":"${encoder.encodeToString(authTag)}"}"""
            return ActualServerClient { request ->
                when {
                    request.url.path.endsWith("get-user-file-info") -> ActualHttpResponse(200, fileInfo(meta).encodeToByteArray())
                    request.url.path.endsWith("user-get-key") -> ActualHttpResponse(
                        200,
                        """{"status":"ok","data":{"id":"$KEY_FIXTURE_ID","salt":"$KEY_FIXTURE_SALT","test":${org.json.JSONObject.quote(KEY_FIXTURE_TEST)}}}""".encodeToByteArray(),
                    )
                    else -> { downloads(); ActualHttpResponse(200, ciphertext) }
                }
            }
        }

        /** A snapshot of another budget, as the server's reset file would be. */
        fun serverArchive(categoryName: String): ByteArray {
            val server = files.createBudget("Server ${UUID.randomUUID()}")
            try {
                ActualBudgetDatabase.open(files.databaseFile(server.id)).use { database ->
                    ActualEntityWriter(database, nodeId = "cdcdcdcdcdcdcdcd").renameCategory(GENERAL_CATEGORY_ID, categoryName)
                }
                return ByteArrayOutputStream().use { bytes -> files.writeArchive(server.id, bytes); bytes.toByteArray() }
            } finally {
                files.deleteBudget(server.id)
            }
        }

        /** Records a completed sync older than any edit made afterwards. */
        fun markSynced() {
            SyncedLogMarkers(context).record(local.id, HlcTimestamp(OLD_SYNC_MILLIS, 0, "0000000000000000").toString())
        }

        fun editOffline(name: String) {
            ActualBudgetDatabase.open(files.databaseFile(local.id)).use { database ->
                ActualEntityWriter(database, nodeId = "abababababababab").renameCategory(GENERAL_CATEGORY_ID, name)
            }
        }

        fun categoryName(databasePath: String): String =
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT name FROM categories WHERE id = ?", arrayOf(GENERAL_CATEGORY_ID)).use {
                    assertTrue(it.moveToFirst()); it.getString(0)
                }
            }

        fun messageLog(): List<String> =
            SQLiteDatabase.openDatabase(files.databaseFile(local.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT timestamp, dataset, row, `column` FROM messages_crdt ORDER BY timestamp", null).use {
                    buildList { while (it.moveToNext()) add("${it.getString(0)} ${it.getString(1)} ${it.getString(2)} ${it.getString(3)}") }
                }
            }
    }

    private fun withLocalBudget(block: (Env) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val local = files.createBudget("Reset ${UUID.randomUUID()}")
        try {
            files.saveCloudRegistration(local.id, CLOUD_FILE_ID, "old-group")
            block(Env(files, BackupService(context, files), files.metadata(local.id)!!, context))
        } finally {
            runCatching { files.deleteBudget(local.id) }
        }
    }

    private companion object {
        const val CLOUD_FILE_ID = "cloud-reset-file"
        const val OLD_KEY_ID = "old-key"
        /** Actual's key-test fixture, shared with `EncryptionKeyManagerTest`. */
        const val KEY_FIXTURE_PASSWORD = "correct horse battery staple"
        const val KEY_FIXTURE_ID = "11111111-1111-1111-1111-111111111111"
        const val KEY_FIXTURE_SALT = "XfsOVKaxbU9A5bYyrQAasQrjy0Nvd3rllUW+ZRWCdTE="
        const val KEY_FIXTURE_TEST = """{"value":"q6+m+aF63TaiPBXc8fqlqmmUQ4VIJZzk4NFa/uPsmyQ=","meta":{"keyId":"11111111-1111-1111-1111-111111111111","algorithm":"aes-256-gcm","iv":"AqLh9LX6BdIiavoO","authTag":"ikPp5J5szWjUn3XlE1TQFg=="}}"""
        /** 2020-01-01T00:00:00Z. */
        const val OLD_SYNC_MILLIS = 1_577_836_800_000L
        /** "General" in the blank budget template. */
        const val GENERAL_CATEGORY_ID = "af375fd4-d759-46b3-bffe-74a856151d57"
    }
}
