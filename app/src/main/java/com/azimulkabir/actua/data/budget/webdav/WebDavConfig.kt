package com.azimulkabir.actua.data.budget.webdav

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where and how to reach a WebDAV server. [serverUrl] is the WebDAV address of the account's files, for
 * example `https://cloud.example.com/remote.php/dav/files/you/`; [folder] is created below it and holds one
 * sub-folder per budget. [password] is an app password, never the account password.
 */
data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val folder: String = DEFAULT_FOLDER,
    /** Allows `http://`. Off by default: without TLS the credentials travel in the clear. */
    val allowInsecureHttp: Boolean = false,
) {
    /** The validated server address, or why it cannot be used. */
    fun baseUrl(): HttpUrl {
        if (username.isBlank() || password.isEmpty()) {
            throw WebDavException.InvalidSettings(WebDavException.InvalidSettings.Reason.MISSING_CREDENTIALS)
        }
        val url = serverUrl.trim().toHttpUrlOrNull()
            ?.takeIf { it.query == null && it.fragment == null }
            ?: throw WebDavException.InvalidSettings(WebDavException.InvalidSettings.Reason.INVALID_URL)
        if (!url.isHttps && !allowInsecureHttp) {
            throw WebDavException.InvalidSettings(WebDavException.InvalidSettings.Reason.INSECURE_HTTP)
        }
        return url
    }

    /** The folder below [baseUrl] as path segments; empty means directly below it. */
    fun folderSegments(): List<String> = parseFolder(folder)

    /** The collection holding one sub-folder per budget. */
    fun folderUrl(): HttpUrl = baseUrl().newBuilder().apply {
        folderSegments().forEach { addPathSegment(it) }
    }.build()

    companion object {
        const val DEFAULT_FOLDER = "Actua Backups"

        /** Splits a folder path on `/`, dropping empty parts, and refuses `.`, `..` and control characters. */
        fun parseFolder(folder: String): List<String> {
            val segments = folder.trim().split('/').map(String::trim).filter(String::isNotEmpty)
            val valid = segments.all { segment ->
                segment != "." && segment != ".." && segment.none { it == '\\' || it.isISOControl() }
            }
            if (!valid) throw WebDavException.InvalidSettings(WebDavException.InvalidSettings.Reason.INVALID_FOLDER)
            return segments
        }
    }
}
