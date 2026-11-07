package com.azimulkabir.actua.data.budget.webdav

import java.io.IOException

/** Why a WebDAV operation failed, in terms the Backups screen can explain to the user. */
sealed class WebDavException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The entered settings cannot be used; nothing was sent to the server. */
    class InvalidSettings(val reason: Reason) : WebDavException("Invalid WebDAV settings: $reason") {
        enum class Reason { MISSING_CREDENTIALS, INVALID_URL, INSECURE_HTTP, INVALID_FOLDER }
    }

    /** The server refused the username or app password, or the account may not use that folder. */
    class Unauthorized(val status: Int) : WebDavException("The server rejected the username or app password ($status)")

    class NotFound : WebDavException("The server could not find that folder or file")

    class InsufficientStorage : WebDavException("The server has no room for the backup")

    /** The server's certificate is not trusted. The connection was closed before any credentials were sent. */
    class Tls(cause: Throwable) : WebDavException("The server's certificate could not be verified", cause)

    /** The server redirected to another scheme or location, which Actua never follows with credentials. */
    class Redirect(val location: String?) : WebDavException("The server redirected the request")

    /** The server cannot rename (MOVE) an uploaded file, so a safe upload is not possible. */
    class MoveUnsupported : WebDavException("The server does not support renaming files")

    /** The server refused to rename an uploaded file because it did not accept the destination address. */
    class MoveRejected(val status: Int) : WebDavException("The server refused to rename the uploaded file ($status)")

    /** The server answered the TLS handshake with something that is not TLS: the port speaks plain HTTP. */
    class NotTls(cause: Throwable) : WebDavException("The server does not use HTTPS on this port", cause)

    /** The stored file is not the size that was sent; it was removed. */
    class UploadIncomplete(val expected: Long, val actual: Long) :
        WebDavException("The server stored $actual of $expected bytes")

    class Server(val status: Int, detail: String) : WebDavException("The server answered $status: $detail")

    class Network(cause: IOException) : WebDavException(cause.message ?: "Could not reach the server", cause)
}
