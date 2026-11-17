package com.azimulkabir.actua.data.receipts

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Receipt text recognition with ML Kit's on-device models (bundled with the app) for Latin, Chinese,
 * Japanese, Korean and Devanagari scripts. The image is read on the phone and never uploaded; a photo taken
 * for scanning lives in the app's cache only until it has been read. Cyrillic, Arabic, Hebrew, Thai, Greek
 * and Bangla are not supported by ML Kit and may be read badly or not at all.
 */
private class MlKitReceiptScanner(context: Context) : ReceiptScanner {
    private val context = context.applicationContext
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val others by lazy {
        listOf(
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
        )
    }

    override fun newCaptureUri(): Uri {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File.createTempFile("scan_", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.receipts", file)
    }

    /**
     * Latin is read first. A receipt in another script reads as little or nothing with it, so the other
     * models are tried too and the one that found the most text wins, but Latin keeps the result when it is
     * nearly as good (the other models also read Latin letters, a little differently).
     */
    override suspend fun recognize(image: Uri): List<RecognizedLine> {
        // The photo is decoded upright and bounded in size here, rather than by ML Kit from the file at full camera
        // resolution, so a very large photo cannot exhaust memory and a failure to load it is ours to report.
        val bitmap = withContext(Dispatchers.IO) {
            ReceiptPhotoStore.decodeUpright(context, image, ReceiptPhotoSizing.RECOGNITION_EDGE)
        }
        try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val first = read(latin, input)
            if (size(first) >= ENOUGH_CHARACTERS) return first
            var best = first
            for (recognizer in others) {
                val candidate = read(recognizer, input)
                if (size(candidate) > size(best)) best = candidate
            }
            return if (size(first) * 5 >= size(best) * 4) first else best
        } finally {
            bitmap.recycle()
        }
    }

    private fun size(lines: List<RecognizedLine>): Int = lines.sumOf { line -> line.text.count(Char::isLetterOrDigit) }

    private suspend fun read(recognizer: TextRecognizer, input: InputImage): List<RecognizedLine> =
        suspendCancellableCoroutine { continuation ->
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

    override fun discard(image: Uri) {
        if (image.authority == "${context.packageName}.receipts") {
            runCatching { context.contentResolver.delete(image, null, null) }
        }
    }
}

/** Characters of text from Latin recognition that count as a receipt read well enough to skip the other models. */
private const val ENOUGH_CHARACTERS = 60

fun createReceiptScanner(context: Context): ReceiptScanner? = MlKitReceiptScanner(context)
