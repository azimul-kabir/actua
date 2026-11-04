package com.azimulkabir.actua.data.budget.webdav

import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A minimal in-memory WebDAV server for tests: Basic authentication, PROPFIND (depth 0 and 1), MKCOL, PUT, MOVE,
 * GET and DELETE, with switches for the failures real servers produce.
 */
class FakeWebDavServer(
    val user: String = "alice",
    val password: String = "app-pass",
    root: String = "/dav/files/alice",
) : Dispatcher() {
    val files = linkedMapOf<String, ByteArray>()
    val collections = linkedSetOf(root)
    /** Last-modified times (RFC 1123) by path; anything not listed here answers with [DEFAULT_MODIFIED]. */
    val modified = mutableMapOf<String, String>()
    val requests: MutableList<RecordedRequest> = java.util.Collections.synchronizedList(mutableListOf())

    /** Answer 501 to MOVE, like a server that cannot rename. */
    var failMove = false

    /** Answer 507 to PUT, like a server whose quota is used up. */
    var storageFull = false

    /** Store this many bytes fewer than were sent, like a truncated upload. */
    var truncateBy = 0

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        if (request.getHeader("Authorization") != Credentials.basic(user, password)) {
            return MockResponse().setResponseCode(401)
        }
        val path = normalize(request.path.orEmpty().substringBefore('?'))
        return when (request.method) {
            "PROPFIND" -> propfind(path, request.getHeader("Depth") ?: "1")
            "MKCOL" -> mkcol(path)
            "PUT" -> put(path, request.body.readByteArray())
            "MOVE" -> move(path, normalize(URI(request.getHeader("Destination").orEmpty()).rawPath))
            "GET" -> files[path]?.let { MockResponse().setResponseCode(200).setBody(Buffer().write(it)) }
                ?: MockResponse().setResponseCode(404)
            "DELETE" -> if (files.remove(path) != null || collections.remove(path)) {
                MockResponse().setResponseCode(204)
            } else MockResponse().setResponseCode(404)
            else -> MockResponse().setResponseCode(405)
        }
    }

    /** Puts a file in place without a request, creating its folders. */
    fun seed(path: String, content: ByteArray = ByteArray(0)) {
        var folder = ""
        path.trim('/').split('/').dropLast(1).forEach { folder += "/$it"; collections += folder }
        files[normalize(path)] = content
    }

    fun requestsOf(method: String): List<RecordedRequest> = requests.filter { it.method == method }

    private fun propfind(path: String, depth: String): MockResponse {
        val isCollection = path in collections
        if (!isCollection && path !in files) return MockResponse().setResponseCode(404)
        val entries = mutableListOf(path)
        if (isCollection && depth != "0") {
            entries += (collections + files.keys).filter { it != path && parent(it) == path }.sorted()
        }
        val body = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:">""")
            for (entry in entries) {
                val collection = entry in collections
                append("<d:response><d:href>${encode(entry)}${if (collection) "/" else ""}</d:href><d:propstat><d:prop>")
                append(if (collection) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/>")
                if (!collection) append("<d:getcontentlength>${files.getValue(entry).size}</d:getcontentlength>")
                append("<d:getlastmodified>${modified[entry] ?: DEFAULT_MODIFIED}</d:getlastmodified>")
                append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>")
            }
            append("</d:multistatus>")
        }
        return MockResponse().setResponseCode(207).setHeader("Content-Type", "application/xml").setBody(body)
    }

    private fun mkcol(path: String): MockResponse = when {
        path in collections || path in files -> MockResponse().setResponseCode(405)
        parent(path) !in collections -> MockResponse().setResponseCode(409)
        else -> { collections += path; MockResponse().setResponseCode(201) }
    }

    private fun put(path: String, bytes: ByteArray): MockResponse = when {
        storageFull -> MockResponse().setResponseCode(507)
        parent(path) !in collections -> MockResponse().setResponseCode(409)
        else -> {
            files[path] = bytes.copyOf((bytes.size - truncateBy).coerceAtLeast(0))
            MockResponse().setResponseCode(201)
        }
    }

    private fun move(from: String, to: String): MockResponse {
        if (failMove) return MockResponse().setResponseCode(501)
        val content = files.remove(from) ?: return MockResponse().setResponseCode(404)
        if (parent(to) !in collections) return MockResponse().setResponseCode(409)
        val replaced = files.put(to, content) != null
        return MockResponse().setResponseCode(if (replaced) 204 else 201)
    }

    private fun parent(path: String): String = path.substringBeforeLast('/')

    private fun normalize(rawPath: String): String =
        URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8").trimEnd('/')

    private fun encode(path: String): String =
        path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    companion object {
        const val DEFAULT_MODIFIED = "Wed, 04 Nov 2026 12:00:00 GMT"
    }
}
