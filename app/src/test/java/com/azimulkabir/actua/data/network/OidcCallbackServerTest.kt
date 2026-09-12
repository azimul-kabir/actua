package com.azimulkabir.actua.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OidcCallbackServerTest {
    @Test
    fun capturesTokenFromActualOpenIdCallback() {
        OidcCallbackServer().use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val tokenFuture = executor.submit<String> { server.awaitToken(5_000) }
                val callback = URI(server.returnUrl)

                Socket("localhost", callback.port).use { socket ->
                    val writer = socket.getOutputStream().bufferedWriter()
                    writer.write("GET /openid-cb?token=session-token-123 HTTP/1.1\r\n")
                    writer.write("Host: localhost:${callback.port}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.flush()

                    val response = socket.getInputStream().bufferedReader().readText()
                    assertTrue(response.startsWith("HTTP/1.1 200 OK"))
                    assertTrue(response.contains("Sign-in complete"))
                }

                assertEquals("session-token-123", tokenFuture.get(5, TimeUnit.SECONDS))
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
