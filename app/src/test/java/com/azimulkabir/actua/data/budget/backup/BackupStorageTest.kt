package com.azimulkabir.actua.data.budget.backup

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupStorageTest {
    @Test fun storageExhaustionIsDetectedBeforeWritingMoreBytes() {
        var free = 64L * 1024 * 1024
        val bytes = ByteArrayOutputStream()
        BackupStorage.checked(bytes) { free }.use { output ->
            output.write(ByteArray(1024 * 1024))
            free = 32L * 1024 * 1024
            assertThrows(InsufficientBackupSpaceException::class.java) { output.write(1) }
        }
        assertEquals(1024 * 1024, bytes.size())
    }

    @Test fun unavailableStorageFailsWithoutWriting() {
        val bytes = ByteArrayOutputStream()
        BackupStorage.checked(bytes) { 0L }.use { output ->
            assertThrows(InsufficientBackupSpaceException::class.java) { output.write(byteArrayOf(1, 2)) }
        }
        assertEquals(0, bytes.size())
    }
}
