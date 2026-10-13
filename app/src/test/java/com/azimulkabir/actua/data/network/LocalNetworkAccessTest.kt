package com.azimulkabir.actua.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    private fun resolver(vararg entries: Pair<String, String>): (String) -> List<InetAddress> {
        val table = entries.toMap()
        return { host -> listOf(ip(table[host] ?: throw UnknownHostException(host))) }
    }

    @Test
    fun classifiesPrivateLinkLocalUniqueLocalAndCgnatAsLocal() {
        listOf("10.0.0.5", "172.16.4.2", "172.31.255.1", "192.168.1.50", "169.254.10.1",
            "100.64.0.1", "100.127.255.254", "fd12:3456::1", "fc00::1", "fe80::1")
            .forEach { assertTrue(it, LocalNetworkAccess.isLocalAddress(ip(it))) }
    }

    @Test
    fun classifiesPublicAndLoopbackAsNotLocal() {
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "100.63.255.255", "127.0.0.1", "::1",
            "2001:4860:4860::8888", "0.0.0.0")
            .forEach { assertFalse(it, LocalNetworkAccess.isLocalAddress(ip(it))) }
    }

    @Test
    fun publicServerDoesNotRequireAccess() {
        val resolve = resolver("actual.example.com" to "93.184.216.34")
        assertFalse(LocalNetworkAccess.isRequired(listOf("https://actual.example.com", ""), resolve))
    }

    @Test
    fun privateFallbackRequiresAccess() {
        val resolve = resolver("actual.example.com" to "93.184.216.34", "actual-lan.example.com" to "192.168.1.20")
        assertTrue(
            LocalNetworkAccess.isRequired(listOf("https://actual.example.com", "http://actual-lan.example.com:5006"), resolve),
        )
    }

    @Test
    fun ipLiteralsAndMdnsNamesNeedNoDnsLookup() {
        val noDns: (String) -> List<InetAddress> = { host ->
            if (host.any(Char::isLetter) && ':' !in host) error("unexpected lookup of $host") else listOf(ip(host))
        }
        assertTrue(LocalNetworkAccess.isRequired(listOf("http://10.0.0.5:5006"), noDns))
        assertTrue(LocalNetworkAccess.isRequired(listOf("http://[fd00::5]:5006"), noDns))
        assertTrue(LocalNetworkAccess.isRequired(listOf("http://actual.local:5006"), noDns))
    }

    @Test
    fun unresolvableOrInvalidHostsDoNotRequireAccess() {
        val resolve = resolver()
        assertFalse(LocalNetworkAccess.isRequired(listOf("https://missing.example.com", "not a url", "  "), resolve))
    }

    @Test
    fun extractsHostWithOrWithoutScheme() {
        assertEquals("actual.example.com", LocalNetworkAccess.hostOf(" Actual.Example.com/ "))
        assertEquals("fd00::5", LocalNetworkAccess.hostOf("http://[fd00::5]:5006"))
        assertNull(LocalNetworkAccess.hostOf(""))
    }
}
