package com.azimulkabir.actua.data.importing

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedStatementIntentTest {
    private val statement = Uri.parse("content://com.example.mail.files/statement.ofx")

    @Test
    fun sendWithStreamReturnsTheSharedFile() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, statement)

        assertEquals(statement, SharedStatementIntent.fileUri(intent))
    }

    @Test
    fun sendFallsBackToTheClipDataUri() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/csv")
        intent.clipData = ClipData.newRawUri("statement", statement)

        assertEquals(statement, SharedStatementIntent.fileUri(intent))
    }

    @Test
    fun viewReturnsTheDataUri() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(statement, "application/x-ofx")

        assertEquals(statement, SharedStatementIntent.fileUri(intent))
    }

    @Test
    fun onlyContentUrisAreAccepted() {
        val file = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/statement.ofx"), "application/x-ofx")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/statement.ofx"))
        val oidc = Intent(Intent.ACTION_VIEW, Uri.parse("actua://oidc-complete"))

        assertNull(SharedStatementIntent.fileUri(file))
        assertNull(SharedStatementIntent.fileUri(web))
        assertNull(SharedStatementIntent.fileUri(oidc))
    }

    @Test
    fun plainTextSharesAndOtherActionsAreIgnored() {
        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Card spend 4.50")
        val main = Intent(Intent.ACTION_MAIN)

        assertNull(SharedStatementIntent.fileUri(text))
        assertNull(SharedStatementIntent.fileUri(main))
    }
}
