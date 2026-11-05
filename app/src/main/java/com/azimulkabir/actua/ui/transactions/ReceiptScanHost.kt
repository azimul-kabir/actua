package com.azimulkabir.actua.ui.transactions

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.diagnostics.DiagnosticsLog
import com.azimulkabir.actua.data.receipts.ReceiptLayout
import com.azimulkabir.actua.data.receipts.ReceiptPhotoStore
import com.azimulkabir.actua.data.receipts.ReceiptScanner
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Takes a photo of a receipt, or lets the user pick an image, reads its text on the device and hands the
 * rows of text to [onScanned] together with a compressed copy of the image in the cache (null if it could
 * not be made). Nothing is uploaded, and nothing is kept unless the caller keeps that copy: a photo taken
 * here is deleted as soon as it has been read. Always composed when a [scanner] exists; the chooser shows
 * while [show] is true.
 */
@Composable
fun ReceiptScanHost(
    scanner: ReceiptScanner,
    show: Boolean,
    onDismiss: () -> Unit,
    onScanned: (rows: List<String>, photo: File?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun toast(messageRes: Int) = Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()

    fun read(image: Uri, discardAfter: Boolean) {
        busy = true
        scope.launch {
            var photo: File? = null
            var failure: Throwable? = null
            val lines = try {
                scanner.recognize(image).also { recognized ->
                    if (recognized.isNotEmpty()) {
                        photo = withContext(Dispatchers.IO) { ReceiptPhotoStore.createPending(context, image) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error
                null
            } catch (error: OutOfMemoryError) {
                failure = error
                null
            } catch (error: LinkageError) {
                // The text recognizer's native code could not be loaded on this device.
                failure = error
                null
            } finally {
                if (discardAfter) scanner.discard(image)
                busy = false
            }
            failure?.let { DiagnosticsLog.actionFailed("Scan receipt", it) }
            when {
                lines == null -> Toast.makeText(
                    context,
                    context.getString(R.string.scan_receipt_failed_reason, DiagnosticsLog.errorType(failure)),
                    Toast.LENGTH_LONG,
                ).show()
                lines.isEmpty() -> toast(R.string.scan_receipt_no_text)
                else -> onScanned(ReceiptLayout.rows(lines), photo)
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val image = pendingCapture
        pendingCapture = null
        if (image != null) {
            if (saved) read(image, discardAfter = true) else scanner.discard(image)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { image ->
        if (image != null) read(image, discardAfter = false)
    }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.scan_receipt_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.scan_receipt_privacy), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.scan_receipt_scripts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        onDismiss()
                        try {
                            val target = scanner.newCaptureUri()
                            pendingCapture = target
                            takePicture.launch(target)
                        } catch (_: ActivityNotFoundException) {
                            pendingCapture?.let(scanner::discard)
                            pendingCapture = null
                            toast(R.string.scan_receipt_no_camera)
                        } catch (_: Exception) {
                            toast(R.string.scan_receipt_failed)
                        }
                    }) { Text(stringResource(R.string.scan_receipt_take_photo)) }
                    TextButton(onClick = {
                        onDismiss()
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.scan_receipt_choose_image)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.scan_receipt_reading))
                }
            },
        )
    }
}
