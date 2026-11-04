package com.azimulkabir.actua.data.receipts

import android.net.Uri

/** One line of recognized text with where it sits on the image (pixels, origin top left). */
data class RecognizedLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Reads the text on a receipt photo, on the device only. The ML Kit implementation exists only in the
 * `playstore` flavor; `fdroid` has none (see `createReceiptScanner`), so shared code depends on this interface.
 */
interface ReceiptScanner {
    /** A fresh content address the camera app can write one photo to. The photo is deleted by [discard]. */
    fun newCaptureUri(): Uri

    /** The recognized lines of [image], or an empty list when there is no text. Throws when the image cannot be read. */
    suspend fun recognize(image: Uri): List<RecognizedLine>

    /** Deletes a photo made for [newCaptureUri]; a no-op for an image the user picked. */
    fun discard(image: Uri)
}
