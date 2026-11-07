package com.azimulkabir.actua.data.receipts

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** A readable image must decode; the bounds-only pass returns null by design and used to be treated as a failure. */
class ReceiptPhotoStoreDecodeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun tempFile(): File = File(context.cacheDir, "decode-${UUID.randomUUID()}.jpg")

    @Test
    fun decodesAReadableImageWithinTheRequestedEdge() {
        val file = tempFile()
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            FileOutputStream(file).use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val decoded = ReceiptPhotoStore.decodeUpright(context, Uri.fromFile(file), maxEdge = 100)
            assertEquals(100, maxOf(decoded.width, decoded.height))
            decoded.recycle()
        } finally {
            source.recycle()
            file.delete()
        }
    }

    @Test
    fun rejectsAFileThatIsNotAnImage() {
        val file = tempFile()
        try {
            file.writeText("not an image")
            assertThrows(IOException::class.java) {
                ReceiptPhotoStore.decodeUpright(context, Uri.fromFile(file), maxEdge = 100)
            }
        } finally {
            file.delete()
        }
    }
}
