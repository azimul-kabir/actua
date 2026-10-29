package com.azimulkabir.actua.data.importing

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Finds the statement file, if any, that another app shared with Actua (`ACTION_SEND`) or asked it
 * to open (`ACTION_VIEW`). Only `content://` URIs are accepted: they carry a temporary read grant
 * from the sender, so Actua never needs storage permissions and never reads arbitrary paths.
 */
object SharedStatementIntent {
    fun fileUri(intent: Intent): Uri? {
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        return uri?.takeIf { it.scheme == "content" }
    }
}
