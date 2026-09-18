package com.azimulkabir.actua.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateHostnameVerificationTest {
    @Test
    fun matchesExactDnsSan() {
        assertTrue(certificateMatchesHost("machine.tailnet.ts.net", listOf("machine.tailnet.ts.net"), emptyList()))
    }

    @Test
    fun dnsSanTakesPrecedenceOverCommonName() {
        assertFalse(
            certificateMatchesHost(
                "machine.tailnet.ts.net",
                listOf("other.tailnet.ts.net"),
                emptyList(),
                commonName = "machine.tailnet.ts.net",
            ),
        )
    }

    @Test
    fun wildcardMatchesExactlyOneLabel() {
        assertTrue(certificateMatchesHost("machine.example.com", listOf("*.example.com"), emptyList()))
        assertFalse(certificateMatchesHost("nested.machine.example.com", listOf("*.example.com"), emptyList()))
        assertFalse(certificateMatchesHost("example.com", listOf("*.example.com"), emptyList()))
    }

    @Test
    fun wildcardCertificateNameDoesNotGoThroughIdnWithAsterisk() {
        assertTrue(certificateMatchesHost("budget.example.com", listOf("*.example.com"), emptyList()))
    }

    @Test
    fun rejectsMalformedWildcardPatterns() {
        assertFalse(certificateMatchesHost("budget.example.com", listOf("*budget.example.com"), emptyList()))
        assertFalse(certificateMatchesHost("budget.example.com", listOf("*.*.example.com"), emptyList()))
    }

    @Test
    fun fallsBackToCommonNameOnlyWhenDnsSansAreAbsent() {
        assertTrue(certificateMatchesHost("legacy.example.com", emptyList(), emptyList(), "legacy.example.com"))
    }

    @Test
    fun ipAddressRequiresMatchingIpSan() {
        assertTrue(certificateMatchesHost("100.64.0.10", emptyList(), listOf("100.64.0.10")))
        assertFalse(certificateMatchesHost("100.64.0.10", emptyList(), listOf("100.64.0.11"), "100.64.0.10"))
    }

    @Test
    fun ipv6AddressUsesExactAddressIdentity() {
        assertTrue(certificateMatchesHost("fd7a:115c:a1e0::1", emptyList(), listOf("fd7a:115c:a1e0:0:0:0:0:1")))
        assertFalse(certificateMatchesHost("fd7a:115c:a1e0::1", emptyList(), listOf("fd7a:115c:a1e0::2")))
    }

    @Test
    fun rejectsUnrelatedDnsName() {
        assertFalse(certificateMatchesHost("machine.tailnet.ts.net", listOf("attacker.example"), emptyList()))
    }
}
