package com.azimulkabir.actua.data.budget.webdav

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.cert.CertificateException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One entry of a WebDAV directory listing. [sizeBytes] is -1 when the server did not report one. */
data class WebDavEntry(
    val url: HttpUrl,
    val isCollection: Boolean,
    val sizeBytes: Long,
    /** ISO-8601 instant, or null when the server sent none or an unreadable date. */
    val modified: String?,
)

/**
 * The few WebDAV (RFC 4918) operations backups need, over plain OkHttp: PROPFIND, MKCOL, PUT, MOVE, GET and
 * DELETE with HTTP Basic authentication. [http] decides TLS trust and timeouts; it should not follow redirects
 * between http and https, which would resend credentials to another scheme.
 *
 * Every failure is reported as a [WebDavException]. Credentials are only ever sent in the Authorization header
 * and are never part of an error message.
 */
class WebDavClient(
    private val http: OkHttpClient,
    username: String,
    password: String,
) {
    private val authorization = Credentials.basic(username, password, Charsets.UTF_8)

    /** Lists [url] ([depth] 0 for the entry itself, 1 for it and its children). Throws [WebDavException.NotFound]. */
    fun propfind(url: HttpUrl, depth: Int): List<WebDavEntry> {
        val request = request(url).header("Depth", depth.toString())
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .build()
        execute(request).use { response ->
            if (response.code != 207 && response.code != 200) throw failure(response)
            val xml = readLimited(response)
            return parseMultistatus(url, xml)
        }
    }

    /** Creates the collection [url]. Returns false when it already exists. */
    fun mkcol(url: HttpUrl): Boolean {
        val request = request(url.asCollection()).method("MKCOL", EMPTY_BODY).build()
        execute(request).use { response ->
            return when (response.code) {
                201 -> true
                // 405 Method Not Allowed is how servers (Nextcloud, Apache) say the collection exists.
                405 -> false
                else -> throw failure(response)
            }
        }
    }

    /**
     * Makes sure [segments] exist below [base], creating the missing ones in order, and returns the final
     * collection. [base] itself must already exist: it is the account's own files address.
     */
    fun ensureCollections(base: HttpUrl, segments: List<String>): HttpUrl {
        var current = base
        for (segment in segments) {
            current = current.newBuilder().addPathSegment(segment).build()
            val exists = try {
                propfind(current.asCollection(), 0)
                true
            } catch (error: WebDavException.NotFound) {
                false
            }
            if (!exists) mkcol(current)
        }
        return current
    }

    /** Uploads exactly [length] bytes read from [content] to [url], replacing what is there. */
    fun put(url: HttpUrl, length: Long, content: InputStream) {
        val body = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun contentLength() = length
            // The stream cannot be replayed, so OkHttp must not resend it after a dropped connection.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                content.source().use { sink.writeAll(it) }
            }
        }
        execute(request(url).put(body).build()).use { response ->
            if (response.code !in setOf(200, 201, 204)) throw failure(response)
        }
    }

    /** Renames [from] to [to], replacing any file already there. */
    fun move(from: HttpUrl, to: HttpUrl) {
        val request = request(from)
            .header("Destination", to.toString())
            .header("Overwrite", "T")
            .method("MOVE", EMPTY_BODY)
            .build()
        execute(request).use { response ->
            when (response.code) {
                201, 204 -> Unit
                405, 501 -> throw WebDavException.MoveUnsupported()
                // A reverse proxy changes the scheme or port the server sees, so it refuses the Destination.
                400, 502 -> throw WebDavException.MoveRejected(response.code)
                else -> throw failure(response)
            }
        }
    }

    /** Writes the file at [url] to [output] and returns its size. */
    fun get(url: HttpUrl, output: OutputStream): Long {
        execute(request(url).get().build()).use { response ->
            if (response.code != 200) throw failure(response)
            val body = response.body ?: return 0
            return body.byteStream().copyTo(output)
        }
    }

    /** Removes [url]. A file that is already gone is not an error. */
    fun delete(url: HttpUrl) {
        execute(request(url).delete().build()).use { response ->
            if (response.code !in setOf(200, 202, 204, 404)) throw failure(response)
        }
    }

    private fun request(url: HttpUrl): Request.Builder =
        Request.Builder().url(url).header("Authorization", authorization)

    private fun execute(request: Request): Response = try {
        http.newCall(request).execute()
    } catch (error: IOException) {
        throw when {
            isPlainHttpPort(error) -> WebDavException.NotTls(error)
            error.isCertificateProblem() -> WebDavException.Tls(error)
            else -> WebDavException.Network(error)
        }
    }

    private fun failure(response: Response): WebDavException = when (response.code) {
        401, 403 -> WebDavException.Unauthorized(response.code)
        404 -> WebDavException.NotFound()
        413, 507 -> WebDavException.InsufficientStorage()
        301, 302, 303, 307, 308 -> WebDavException.Redirect(response.header("Location"))
        else -> WebDavException.Server(response.code, response.message)
    }

    private fun readLimited(response: Response): ByteArray {
        val source = response.body?.source() ?: return ByteArray(0)
        source.request(MAX_LISTING_BYTES + 1)
        if (source.buffer.size > MAX_LISTING_BYTES) {
            throw WebDavException.Server(response.code, "the listing is too large")
        }
        return source.buffer.readByteArray()
    }

    private fun Throwable.isCertificateProblem(): Boolean =
        generateSequence(this) { it.cause }.any { it is SSLException || it is CertificateException }

    private fun HttpUrl.asCollection(): HttpUrl =
        if (encodedPath.endsWith("/")) this else newBuilder().addPathSegment("").build()

    internal companion object {
        /** A TLS handshake that failed because the other end answered in plain text, not because of a certificate. */
        fun isPlainHttpPort(error: Throwable): Boolean = generateSequence(error) { it.cause }.any { cause ->
            cause is SSLException && PLAIN_HTTP_MARKERS.any { cause.message.orEmpty().contains(it, ignoreCase = true) }
        }

        private const val DAV = "DAV:"
        // What Conscrypt and the JDK report when a TLS client talks to a port that answers in plain HTTP.
        private val PLAIN_HTTP_MARKERS = listOf(
            "WRONG_VERSION_NUMBER",
            "first record does not look like a TLS handshake",
            "Unable to parse TLS packet header",
            "Unrecognized SSL message",
            "Unsupported or unrecognized SSL message",
        )
        private const val MAX_LISTING_BYTES = 8L * 1024 * 1024
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/>""" +
            """</d:prop></d:propfind>"""

        /**
         * Reads the entries of a `207 Multi-Status` answer to a PROPFIND on [requestUrl]. Documents with a
         * DOCTYPE are refused outright, so a server cannot use entities to attack the parser.
         */
        fun parseMultistatus(requestUrl: HttpUrl, xml: ByteArray): List<WebDavEntry> {
            if (String(xml, Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
                throw WebDavException.Server(207, "unsupported XML")
            }
            val document = try {
                val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            } catch (error: Exception) {
                throw WebDavException.Server(207, "unreadable listing")
            }
            val responses = document.getElementsByTagNameNS(DAV, "response")
            return (0 until responses.length).mapNotNull { index ->
                val response = responses.item(index) as? Element ?: return@mapNotNull null
                val href = response.getElementsByTagNameNS(DAV, "href").item(0)?.textContent?.trim()
                    ?: return@mapNotNull null
                val url = requestUrl.resolve(href) ?: return@mapNotNull null
                var isCollection = false
                var size = -1L
                var modified: String? = null
                val propstats = response.getElementsByTagNameNS(DAV, "propstat")
                for (i in 0 until propstats.length) {
                    val propstat = propstats.item(i) as? Element ?: continue
                    val status = propstat.getElementsByTagNameNS(DAV, "status").item(0)?.textContent.orEmpty()
                    if (!status.contains(" 200")) continue
                    isCollection = isCollection || propstat.getElementsByTagNameNS(DAV, "collection").length > 0
                    propstat.getElementsByTagNameNS(DAV, "getcontentlength").item(0)?.textContent
                        ?.trim()?.toLongOrNull()?.let { size = it }
                    propstat.getElementsByTagNameNS(DAV, "getlastmodified").item(0)?.textContent
                        ?.let { modified = parseHttpDate(it.trim()) }
                }
                WebDavEntry(url, isCollection, size, modified)
            }
        }

        private fun parseHttpDate(text: String): String? = runCatching {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString()
        }.getOrNull()
    }
}
