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
    fun rejectsWildcardDirectlyBelowTld() {
        assertFalse(certificateMatchesHost("example.com", listOf("*.com"), emptyList()))
    }

    @Test
    fun rejectsLeadingZeroIpv4Literal() {
        assertFalse(certificateMatchesHost("127.0.0.010", emptyList(), listOf("127.0.0.010")))
    }

    @Test
    fun ignoresMalformedIpSanCandidate() {
        assertFalse(certificateMatchesHost("100.64.0.10", emptyList(), listOf("not-an-ip.example")))
    }

    @Test
    fun rejectsInvalidIpv4LookingHostWithoutTreatingItAsAnIp() {
        assertFalse(certificateMatchesHost("999.64.0.10", emptyList(), listOf("999.64.0.10")))
    }

    @Test
    fun extractsEscapedCommaFromCommonName() {
        assertTrue(commonNameFromRfc2253("""CN=budget\,server.example.com,O=Example,C=US""") == "budget,server.example.com")
    }

    @Test
    fun extractsQuotedCommonNameContainingComma() {
        assertTrue(commonNameFromRfc2253("""CN="budget,server.example.com",O=Example,C=US""") == "budget,server.example.com")
    }

    @Test
    fun findsCommonNameAfterAnotherRdn() {
        assertTrue(commonNameFromRfc2253("O=Example,CN=legacy.example.com,C=US") == "legacy.example.com")
    }

    @Test
    fun rejectsUnrelatedDnsName() {
        assertFalse(certificateMatchesHost("machine.tailnet.ts.net", listOf("attacker.example"), emptyList()))
    }
}
