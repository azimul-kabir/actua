package com.azimulkabir.actua.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActualServerUrlTest {
    private val client = ActualServerClient()

    @Test
    fun allowsConfiguredPrivateLanHttpFallback() {
        assertEquals("http://192.168.68.109", client.normalizeServerUrl("http://192.168.68.109/"))
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
