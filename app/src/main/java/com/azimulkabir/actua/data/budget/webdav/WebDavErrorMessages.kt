package com.azimulkabir.actua.data.budget.webdav

import android.content.res.Resources
import com.azimulkabir.actua.R

/** The user-facing wording for each way a WebDAV operation can fail, shared by the Backups screen and the worker. */
internal object WebDavErrorMessages {
    fun describe(resources: Resources, error: Throwable): String = when (error) {
        is WebDavException.InvalidSettings -> resources.getString(
            when (error.reason) {
                WebDavException.InvalidSettings.Reason.MISSING_CREDENTIALS -> R.string.webdav_error_missing_credentials
                WebDavException.InvalidSettings.Reason.INVALID_URL -> R.string.webdav_error_invalid_url
                WebDavException.InvalidSettings.Reason.INSECURE_HTTP -> R.string.webdav_error_insecure_http
                WebDavException.InvalidSettings.Reason.INVALID_FOLDER -> R.string.webdav_error_invalid_folder
            },
        )
        is WebDavException.Unauthorized -> resources.getString(R.string.webdav_error_unauthorized)
        is WebDavException.NotFound -> resources.getString(R.string.webdav_error_not_found)
        is WebDavException.InsufficientStorage -> resources.getString(R.string.webdav_error_storage_full)
        is WebDavException.Tls -> resources.getString(R.string.webdav_error_tls)
        is WebDavException.Redirect -> resources.getString(R.string.webdav_error_redirect)
        is WebDavException.MoveUnsupported -> resources.getString(R.string.webdav_error_move_unsupported)
        is WebDavException.MoveRejected -> resources.getString(R.string.webdav_error_move_rejected, error.status)
        is WebDavException.NotTls -> resources.getString(R.string.webdav_error_not_tls)
        is WebDavException.UploadIncomplete -> resources.getString(R.string.webdav_error_incomplete)
        is WebDavException.Server -> if (error.status == 405) {
            resources.getString(R.string.webdav_error_method_not_allowed)
        } else {
            resources.getString(R.string.webdav_error_server, error.status)
        }
        is WebDavException.Network -> resources.getString(R.string.webdav_error_network)
        else -> error.message ?: resources.getString(R.string.webdav_error_generic)
    }
}
