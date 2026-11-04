package com.azimulkabir.actua.data.receipts

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Receipt text recognition with ML Kit's on-device Latin model (bundled with the app). The image is read
 * on the phone and never uploaded; a photo taken for scanning lives in the app's cache only until it has
 * been read. Other scripts (Cyrillic, Arabic, Bangla, ...) may be read badly or not at all.
 */
private class MlKitReceiptScanner(context: Context) : ReceiptScanner {
    private val context = context.applicationContext
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun newCaptureUri(): Uri {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File.createTempFile("scan_", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.receipts", file)
    }

    override suspend fun recognize(image: Uri): List<RecognizedLine> {
        val input = InputImage.fromFilePath(context, image)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(input)
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        RecognizedLine(line.text, box.left, box.top, box.right, box.bottom)
                    }
                    continuation.resume(lines)
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    override fun discard(image: Uri) {
        if (image.authority == "${context.packageName}.receipts") {
            runCatching { context.contentResolver.delete(image, null, null) }
        }
    }
}

fun createReceiptScanner(context: Context): ReceiptScanner? = MlKitReceiptScanner(context)
