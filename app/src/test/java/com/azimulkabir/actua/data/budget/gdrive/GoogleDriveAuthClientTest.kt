package com.azimulkabir.actua.data.budget.gdrive

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder

class GoogleDriveAuthClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: GoogleDriveAuthClient
    private var lastRequestBody: String = ""

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        client = GoogleDriveAuthClient(
            tokenEndpoint = "http://127.0.0.1:$port/token",
            revocationEndpoint = "http://127.0.0.1:$port/revoke",
        )
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun exchangeAuthorizationCodeSendsCorrectParamsAndParsesTokens() {
        server.createContext("/token") { exchange ->
            lastRequestBody = exchange.requestBody.bufferedReader().readText()
            val response = """
                {
                    "access_token": "ya29.test_access_token",
                    "refresh_token": "1//test_refresh_token",
                    "expires_in": 3600,
                    "token_type": "Bearer"
                }
            """.trimIndent()
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        val tokens = client.exchangeAuthorizationCode(
            clientId = "test-client-id",
            redirectUri = "com.azimulkabir.actua:/oauth2redirect",
            code = "test-auth-code",
            codeVerifier = "test-verifier-xyz123",
        )

        assertEquals("ya29.test_access_token", tokens.accessToken)
        assertEquals("1//test_refresh_token", tokens.refreshToken)
        assertFalse(tokens.isExpired)

        val params = lastRequestBody.split("&").associate {
            val parts = it.split("=")
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals("test-client-id", params["client_id"])
        assertEquals("com.azimulkabir.actua:/oauth2redirect", params["redirect_uri"])
        assertEquals("test-auth-code", params["code"])
        assertEquals("test-verifier-xyz123", params["code_verifier"])
        assertEquals("authorization_code", params["grant_type"])
    }

    @Test
    fun refreshAccessTokenPreservesExistingRefreshTokenWhenServerOmits() {
        server.createContext("/token") { exchange ->
            lastRequestBody = exchange.requestBody.bufferedReader().readText()
            val response = """
                {
                    "access_token": "ya29.refreshed_access_token",
                    "expires_in": 1800,
                    "token_type": "Bearer"
                }
            """.trimIndent()
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        val tokens = client.refreshAccessToken(
            clientId = "test-client-id",
            refreshToken = "existing-refresh-token",
        )

        assertEquals("ya29.refreshed_access_token", tokens.accessToken)
        assertEquals("existing-refresh-token", tokens.refreshToken)
        assertFalse(tokens.isExpired)

        assertTrue(lastRequestBody.contains("grant_type=refresh_token"))
        assertTrue(lastRequestBody.contains("refresh_token=existing-refresh-token"))
    }

    @Test
    fun refreshAccessTokenThrowsUnauthorizedOn401() {
        server.createContext("/token") { exchange ->
            val errorResponse = """{"error": "invalid_grant"}"""
            val bytes = errorResponse.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        assertThrows(GoogleDriveException.Unauthorized::class.java) {
            client.refreshAccessToken("test-client", "invalid-token")
        }
    }

    @Test
    fun exchangeAuthorizationCodeThrowsApiErrorOnNon200() {
        server.createContext("/token") { exchange ->
            val errorResponse = """{"error": "server_error"}"""
            val bytes = errorResponse.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(500, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        val exception = assertThrows(GoogleDriveException.ApiError::class.java) {
            client.exchangeAuthorizationCode("id", "uri", "code", "ver")
        }
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun revokeTokenReturnsTrueOnSuccess() {
        server.createContext("/revoke") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        val success = client.revokeToken("token-to-revoke")
        assertTrue(success)
    }
}
