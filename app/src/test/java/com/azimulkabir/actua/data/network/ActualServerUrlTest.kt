package com.azimulkabir.actua.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActualServerUrlTest {
    private val client = ActualServerClient()

    @Test
    fun allowsAnyPrivateLanHttpFallback() {
        assertEquals("http://192.168.68.109", client.normalizeServerUrl("http://192.168.68.109/"))
        assertEquals("http://192.168.1.50", client.normalizeServerUrl("http://192.168.1.50/"))
        assertEquals("http://10.0.0.5", client.normalizeServerUrl("http://10.0.0.5/"))
        assertEquals("http://localhost", client.normalizeServerUrl("http://localhost/"))
    }

    @Test
    fun rejectsPublicCleartextHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            client.normalizeServerUrl("http://example.com")
        }
    }

    @Test
    fun continuesToAllowPublicHttps() {
        assertEquals("https://example.com", client.normalizeServerUrl("https://example.com/"))
    }
}
