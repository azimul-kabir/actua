package com.azimulkabir.actua.data.budget.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WebDavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var fake: FakeWebDavServer
    private val http = OkHttpClient.Builder().followSslRedirects(false).build()

    private fun client(password: String = "app-pass") = WebDavClient(http, "alice", password)

    private fun url(path: String) = server.url(path)

    @Before fun setUp() {
        fake = FakeWebDavServer()
        server = MockWebServer().apply { dispatcher = fake; start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun propfindListsFilesAndCollectionsWithSizesAndDates() {
        fake.seed("/dav/files/alice/Actua Backups/budget-1/2026-11-04_10-00-00.actuabkp", ByteArray(42))

        val entries = client().propfind(url("/dav/files/alice/Actua%20Backups/budget-1/"), 1)

        val file = entries.single { !it.isCollection }
        assertEquals("2026-11-04_10-00-00.actuabkp", file.url.pathSegments.last())
        assertEquals(42L, file.sizeBytes)
        assertEquals("2026-11-04T12:00:00Z", file.modified)
        assertTrue(entries.any { it.isCollection })
    }

    @Test fun propfindOfAMissingFolderIsNotFound() {
        assertThrows(WebDavException.NotFound::class.java) { client().propfind(url("/dav/files/alice/missing/"), 1) }
    }

    @Test fun aWrongPasswordIsUnauthorizedAndTheMessageNeverContainsIt() {
        val error = assertThrows(WebDavException.Unauthorized::class.java) {
            client(password = "wrong-secret-123").propfind(url("/dav/files/alice/"), 0)
        }
        assertEquals(401, error.status)
        assertFalse(error.message.orEmpty().contains("wrong-secret-123"))
    }

    @Test fun mkcolCreatesOnceAndReportsAnExistingCollection() {
        assertTrue(client().mkcol(url("/dav/files/alice/Actua")))
        assertFalse(client().mkcol(url("/dav/files/alice/Actua")))
        assertTrue("/dav/files/alice/Actua" in fake.collections)
    }

    @Test fun ensureCollectionsCreatesOnlyWhatIsMissing() {
        client().ensureCollections(url("/dav/files/alice/"), listOf("Actua Backups", "budget-1"))
        client().ensureCollections(url("/dav/files/alice/"), listOf("Actua Backups", "budget-1"))

        assertEquals(2, fake.requestsOf("MKCOL").size)
        assertTrue("/dav/files/alice/Actua Backups/budget-1" in fake.collections)
    }

    @Test fun putSendsTheExactLengthAndGetReturnsTheSameBytes() {
        val content = ByteArray(10_000) { (it % 251).toByte() }

        client().put(url("/dav/files/alice/backup.bin"), content.size.toLong(), ByteArrayInputStream(content))
        val output = ByteArrayOutputStream()
        val size = client().get(url("/dav/files/alice/backup.bin"), output)

        val put = fake.requestsOf("PUT").single()
        assertEquals("10000", put.getHeader("Content-Length"))
        assertNull(put.getHeader("Transfer-Encoding"))
        assertEquals(10_000L, size)
        assertArrayEquals(content, output.toByteArray())
    }

    @Test fun moveSendsTheDestinationAndOverwrites() {
        fake.seed("/dav/files/alice/a.part", byteArrayOf(1, 2, 3))

        client().move(url("/dav/files/alice/a.part"), url("/dav/files/alice/a.actuabkp"))

        val move = fake.requestsOf("MOVE").single()
        assertTrue(move.getHeader("Destination").orEmpty().endsWith("/dav/files/alice/a.actuabkp"))
        assertEquals("T", move.getHeader("Overwrite"))
        assertTrue("/dav/files/alice/a.actuabkp" in fake.files)
        assertFalse("/dav/files/alice/a.part" in fake.files)
    }

    @Test fun aServerThatCannotMoveIsReported() {
        fake.seed("/dav/files/alice/a.part")
        fake.failMove = true
        assertThrows(WebDavException.MoveUnsupported::class.java) {
            client().move(url("/dav/files/alice/a.part"), url("/dav/files/alice/a.actuabkp"))
        }
    }

    @Test fun deleteOfAnAlreadyMissingFileIsNotAnError() {
        client().delete(url("/dav/files/alice/never-there.actuabkp"))
    }

    @Test fun aFullServerIsReported() {
        fake.storageFull = true
        assertThrows(WebDavException.InsufficientStorage::class.java) {
            client().put(url("/dav/files/alice/x.part"), 3, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        }
    }

    @Test fun aRedirectToAnotherSchemeIsNotFollowedWithCredentials() {
        val other = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(301).setHeader("Location", "https://example.invalid/dav/"))
            start()
        }
        try {
            val error = assertThrows(WebDavException.Redirect::class.java) {
                client().propfind(other.url("/dav/"), 0)
            }
            assertEquals("https://example.invalid/dav/", error.location)
            assertEquals(1, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test fun anUnreachableServerIsANetworkError() {
        val closed = url("/dav/files/alice/")
        server.shutdown()
        assertThrows(WebDavException.Network::class.java) { client().propfind(closed, 0) }
    }

    @Test fun aListingWithADoctypeIsRefused() {
        val xml = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e "boom">]><d:multistatus xmlns:d="DAV:"/>"""
        assertThrows(WebDavException.Server::class.java) {
            WebDavClient.parseMultistatus(url("/dav/"), xml.toByteArray())
        }
    }

    @Test fun aGarbledListingIsRefused() {
        assertThrows(WebDavException.Server::class.java) {
            WebDavClient.parseMultistatus(url("/dav/"), "not xml".toByteArray())
        }
    }

    @Test fun entriesWithoutAContentLengthOrDateStillParse() {
        val xml = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/x.actuabkp</d:href><d:propstat>""" +
            """<d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>""" +
            """</d:response></d:multistatus>"""

        val entry = WebDavClient.parseMultistatus(url("/dav/"), xml.toByteArray()).single()

        assertEquals(-1L, entry.sizeBytes)
        assertNull(entry.modified)
        assertFalse(entry.isCollection)
        assertNotNull(entry.url)
    }

    @Test fun propertiesTheServerRefusedAreIgnored() {
        val xml = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/x</d:href>""" +
            """<d:propstat><d:prop><d:getcontentlength>99</d:getcontentlength></d:prop>""" +
            """<d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response></d:multistatus>"""

        assertEquals(-1L, WebDavClient.parseMultistatus(url("/dav/"), xml.toByteArray()).single().sizeBytes)
    }
}
