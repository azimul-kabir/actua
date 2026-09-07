package com.azimulkabir.actua.data.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActualServerClientTest {
    @Test
    fun listFilesFiltersDeletedBudgetsAndReadsEncryption() {
        val transport = RecordingTransport {
            ActualHttpResponse(
                200,
                """{"status":"ok","data":[{"fileId":"f1","groupId":"g1","name":"Main","deleted":0,"encryptKeyId":"k1"},{"fileId":"f2","name":"Old","deleted":1}]}"""
                    .encodeToByteArray(),
            )
        }
        val files = ActualServerClient(transport).listFiles("https://actual.test", "token")
        assertEquals(listOf(RemoteBudgetFile("f1", "g1", "Main", "k1")), files)
        assertEquals("/sync/list-user-files", transport.last.url.path)
        assertEquals("token", transport.last.headers["X-ACTUAL-TOKEN"])
    }

    @Test
    fun fileAndKeyMetadataMatchActualResponses() {
        val transport = RecordingTransport { request ->
            when (request.url.path) {
                "/sync/get-user-file-info" -> ActualHttpResponse(
                    200,
                    """{"status":"ok","data":{"fileId":"f1","groupId":"g1","name":"Main","deleted":0,"encryptMeta":{"keyId":"k1","algorithm":"aes-256-gcm","iv":"aXY=","authTag":"dGFn"}}}"""
                        .encodeToByteArray(),
                )
                else -> ActualHttpResponse(
                    200,
                    """{"status":"ok","data":{"id":"k1","salt":"salt","test":"test-json"}}"""
                        .encodeToByteArray(),
                )
            }
        }
        val client = ActualServerClient(transport)
        val info = client.getFileInfo("https://actual.test", "token", "f1")
        assertEquals("k1", info.encryption?.keyId)
        assertEquals("aXY=", info.encryption?.ivBase64)

        val key = client.getKeyInfo("https://actual.test", "token", "f1")
        assertEquals("k1", key.id)
        assertEquals("salt", key.salt)
        assertEquals("POST", transport.last.method)
        assertEquals("application/json", transport.last.headers["Content-Type"])
    }

    @Test
    fun downloadAndSyncUseExactHeadersAndBinaryBodies() {
        val binaryResponse = byteArrayOf(9, 8, 7)
        val transport = RecordingTransport { ActualHttpResponse(200, binaryResponse) }
        val client = ActualServerClient(transport)

        assertArrayEquals(binaryResponse, client.downloadFile("https://actual.test", "token", "f1"))
        assertEquals("f1", transport.last.headers["X-ACTUAL-FILE-ID"])

        val requestBody = byteArrayOf(1, 2, 3)
        assertArrayEquals(binaryResponse, client.postSync("https://actual.test", "token", requestBody))
        assertEquals("/sync/sync", transport.last.url.path)
        assertEquals("application/actual-sync", transport.last.headers["Content-Type"])
        assertArrayEquals(requestBody, transport.last.body)
    }

    @Test
    fun authorizationAndMissingFilesHaveDistinctErrors() {
        val unauthorized = ActualServerClient(RecordingTransport { ActualHttpResponse(403, byteArrayOf()) })
        assertThrows(ActualServerException.Unauthorized::class.java) {
            unauthorized.postSync("https://actual.test", "token", byteArrayOf())
        }

        val missing = ActualServerClient(RecordingTransport { ActualHttpResponse(404, byteArrayOf()) })
        assertThrows(ActualServerException.FileNotFound::class.java) {
            missing.downloadFile("https://actual.test", "token", "missing")
        }
    }

    private class RecordingTransport(
        private val response: (ActualHttpRequest) -> ActualHttpResponse,
    ) : ActualHttpTransport {
        lateinit var last: ActualHttpRequest
        override fun execute(request: ActualHttpRequest): ActualHttpResponse {
            last = request
            return response(request)
        }
    }
}
