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

    @Test
    fun rejectsPublicHostnamesThatStartLikeIpv6Prefixes() {
        // #1512: fc00::/7 and fe80::/10 are address ranges, not hostname prefixes.
        listOf(
            "http://fdexample.com",
            "http://fcexample.com",
            "http://fdroid-mirror.example.com",
            "http://fe80.example.com",
            "http://fd00",
        ).forEach { url ->
            assertThrows(url, IllegalArgumentException::class.java) { client.normalizeServerUrl(url) }
        }
    }

    @Test
    fun allowsPrivateIpv6Literals() {
        listOf(
            "http://[fd00::1]",
            "http://[fc00::1]",
            "http://[fdab:cdef:1234::42]:5006",
            "http://[fe80::1]",
            "http://[febf::1]",
            "http://[::1]",
            "http://[0:0:0:0:0:0:0:1]",
        ).forEach { url -> assertEquals(url, client.normalizeServerUrl("$url/")) }
    }

    @Test
    fun rejectsPublicAndMalformedIpv6Literals() {
        listOf(
            "http://[2001:db8::1]",
            "http://[fec0::1]",
            "http://[::]",
            "http://[::ffff:8.8.8.8]",
        ).forEach { url ->
            assertThrows(url, IllegalArgumentException::class.java) { client.normalizeServerUrl(url) }
        }
    }

    @Test
    fun keepsLocalHostnamesAndPrivateIpv4() {
        assertEquals("http://actual.local", client.normalizeServerUrl("http://actual.local"))
        assertEquals("http://budget.localhost:5006", client.normalizeServerUrl("http://budget.localhost:5006"))
        assertEquals("http://172.16.0.2", client.normalizeServerUrl("http://172.16.0.2"))
        assertEquals("http://169.254.1.1", client.normalizeServerUrl("http://169.254.1.1"))
        assertEquals("http://127.0.0.1:5006", client.normalizeServerUrl("http://127.0.0.1:5006"))
        assertThrows(IllegalArgumentException::class.java) { client.normalizeServerUrl("http://172.32.0.1") }
        assertThrows(IllegalArgumentException::class.java) { client.normalizeServerUrl("http://8.8.8.8") }
    }
}
