package com.azimulkabir.actua.data.network

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualServerClientTest {
    @Test
    fun passwordLoginExplicitlyRequestsPasswordMethod() {
        val transport = RecordingTransport {
            ActualHttpResponse(200, """{"status":"ok","data":{"token":"token-1"}}""".encodeToByteArray())
        }

        val token = ActualServerClient(transport).login("https://actual.test", "secret")

        assertEquals("token-1", token)
        assertEquals("/account/login", transport.last.url.path)
        assertEquals("POST", transport.last.method)
        val body = JSONObject(transport.last.body!!.decodeToString())
        assertEquals("password", body.getString("loginMethod"))
        assertEquals("secret", body.getString("password"))
    }

    @Test
    fun openIdLoginRequestsOpenIdAndReturnsAuthorizationUrl() {
        val transport = RecordingTransport {
            ActualHttpResponse(
                200,
                """{"status":"ok","data":{"returnUrl":"https://idp.test/authorize?state=abc"}}""".encodeToByteArray(),
            )
        }

        val authorizationUrl = ActualServerClient(transport).startOpenIdLogin(
            "https://actual.test",
            "http://localhost:43210",
            "server-password",
        )

        assertEquals("https://idp.test/authorize?state=abc", authorizationUrl)
        val body = JSONObject(transport.last.body!!.decodeToString())
        assertEquals("openid", body.getString("loginMethod"))
        assertEquals("http://localhost:43210", body.getString("returnUrl"))
        assertEquals("server-password", body.getString("password"))
    }

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

    @Test
    fun uploadAndDeleteUseActualBudgetFileProtocol() {
        val transport = RecordingTransport { request ->
            if (request.url.path.endsWith("upload-user-file")) {
                ActualHttpResponse(200, """{"status":"ok","groupId":"group-1"}""".encodeToByteArray())
            } else {
                ActualHttpResponse(200, byteArrayOf())
            }
        }
        val client = ActualServerClient(transport)
        val archive = byteArrayOf(1, 2, 3)
        assertEquals("group-1", client.uploadFile("https://actual.test", "token", "file-1", "Travel & Fun", archive))
        assertEquals("/sync/upload-user-file", transport.last.url.path)
        assertEquals("Travel%20%26%20Fun", transport.last.headers["X-ACTUAL-NAME"])
        assertEquals("2", transport.last.headers["X-ACTUAL-FORMAT"])
        assertArrayEquals(archive, transport.last.body)

        client.deleteFile("https://actual.test", "token", "file-1")
        assertEquals("/sync/delete-user-file", transport.last.url.path)
        assertTrue(transport.last.body!!.decodeToString().contains("\"fileId\":\"file-1\""))
    }

    @Test
    fun deleteOnlyTreatsActualMissingFileResponseAsAlreadyDeleted() {
        val client = ActualServerClient(RecordingTransport { ActualHttpResponse(400, "file-not-found".encodeToByteArray()) })
        assertThrows(ActualServerException.FileNotFound::class.java) {
            client.deleteFile("https://actual.test", "token", "missing")
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
