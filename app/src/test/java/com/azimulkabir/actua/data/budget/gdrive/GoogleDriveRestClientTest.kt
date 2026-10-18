package com.azimulkabir.actua.data.budget.gdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleDriveRestClientTest {

    @Test
    fun requireTokenThrowsUnauthorizedWhenNoTokenAvailable() {
        val client = GoogleDriveRestClient(tokenProvider = { null })
        assertThrows(GoogleDriveException.Unauthorized::class.java) {
            client.listBackups()
        }
    }

    @Test
    fun fileMetadataParsesAppPropertiesCorrectly() {
        val metadata = GoogleDriveFileMetadata(
            id = "file-12345",
            name = "actua-backup-2026-10-18.actuabkp",
            mimeType = GoogleDriveConfig.MIME_TYPE_ENCRYPTED_BACKUP,
            sizeBytes = 1024L * 1024L,
            modifiedTime = "2026-10-18T12:00:00.000Z",
            appProperties = mapOf(
                "budgetId" to "budget-abc",
                "encrypted" to "true",
                "formatVersion" to "1",
            ),
        )

        assertEquals("file-12345", metadata.id)
        assertEquals("actua-backup-2026-10-18.actuabkp", metadata.name)
        assertEquals("budget-abc", metadata.appProperties["budgetId"])
        assertEquals("true", metadata.appProperties["encrypted"])
        assertEquals("1", metadata.appProperties["formatVersion"])
    }
}
