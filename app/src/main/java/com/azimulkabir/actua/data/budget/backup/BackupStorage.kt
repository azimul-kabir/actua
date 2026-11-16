package com.azimulkabir.actua.data.budget.backup

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Checks device capacity rather than imposing a total-size cap on receipt backups. */
internal object BackupStorage {
    // Keep a little working space for SQLite and other apps while temporary backup files exist.
    private const val RESERVE_BYTES = 32L * 1024 * 1024
    private const val CHECK_INTERVAL = 1024 * 1024

    fun requireSpace(directory: File, bytes: Long) = requireSpace(directory.usableSpace, bytes)

    private fun requireSpace(available: Long, bytes: Long) {
        if (bytes < 0 || available < RESERVE_BYTES || bytes > available - RESERVE_BYTES) {
            throw InsufficientBackupSpaceException()
        }
    }

    fun open(file: File): OutputStream {
        requireSpace(file.parentFile!!, 0)
        return checked(file.outputStream()) { file.parentFile!!.usableSpace }
    }

    /** Rechecks during streaming, including downloads and encryption, without a filesystem call per byte. */
    internal fun checked(output: OutputStream, availableBytes: () -> Long): OutputStream = object : OutputStream() {
        private var untilCheck = 0

        override fun write(value: Int) {
            checkSpace(1)
            output.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length == 0) return
            checkSpace(length)
            output.write(buffer, offset, length)
        }

        private fun checkSpace(count: Int) {
            if (count > untilCheck) {
                requireSpace(availableBytes(), count.toLong() + CHECK_INTERVAL)
                untilCheck = CHECK_INTERVAL
            }
            untilCheck -= count
        }

        override fun flush() = output.flush()
        override fun close() = output.close()
    }
}

internal class InsufficientBackupSpaceException : IOException("Not enough free space on this device to back up or restore. Free some storage and try again.")
