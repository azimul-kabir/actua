package com.azimulkabir.actua.data.network

import android.content.Context
import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class ServerCertificateInfo(
    val host: String,
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
    val sha256Fingerprint: String,
)

class TrustedCertificateStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("trusted_server_certificates", Context.MODE_PRIVATE)

    fun fingerprint(host: String): String? =
        preferences.getString(normalizeHost(host), null)

    fun trust(host: String, fingerprint: String) {
        preferences.edit().putString(normalizeHost(host), fingerprint).apply()
    }

    fun forget(host: String) {
        preferences.edit().remove(normalizeHost(host)).apply()
    }

    fun hasTrust(host: String): Boolean = fingerprint(host) != null

    private fun normalizeHost(host: String): String = host.lowercase().trimEnd('.')
}

internal fun certificateFingerprint(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString(":") { "%02X".format(it.toInt() and 0xff) }

internal fun certificateMatchesHost(
    host: String,
    dnsNames: List<String>,
    ipAddresses: List<String>,
    commonName: String? = null,
): Boolean {
    val normalizedHost = host.trim().trimEnd('.')
    val isIpAddress = isIpLiteral(normalizedHost)

    if (isIpAddress) {
        val hostBytes = runCatching { InetAddress.getByName(normalizedHost).address }.getOrNull()
            ?: return false
        return ipAddresses.any { candidate ->
            isIpLiteral(candidate) &&
                runCatching {
                    InetAddress.getByName(candidate.substringBefore('%')).address.contentEquals(hostBytes)
                }.getOrDefault(false)
        }
    }

    val asciiHost = runCatching { IDN.toASCII(normalizedHost).lowercase() }.getOrNull()
        ?: return false
    if (dnsNames.isNotEmpty()) {
        return dnsNames.any { dnsNameMatches(asciiHost, it) }
    }
    return commonName?.let { dnsNameMatches(asciiHost, it) } == true
}

private fun isIpLiteral(value: String): Boolean {
    if (value.contains(':')) {
        // IPv6 literals contain ':'; reject non-address characters before using InetAddress so
        // malformed hostnames can never trigger DNS resolution.
        if (!value.matches(Regex("""[0-9A-Fa-f:.%]+"""))) return false
        return runCatching { InetAddress.getByName(value.substringBefore('%')).address.size == 16 }
            .getOrDefault(false)
    }

    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() &&
            part.all { it in '0'..'9' } &&
            part.length <= 3 &&
            (part.length == 1 || part[0] != '0') &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

internal fun commonNameFromRfc2253(distinguishedName: String): String? {
    var start = 0
    while (start < distinguishedName.length) {
        var end = start
        var escaped = false
        var quoted = false
        while (end < distinguishedName.length) {
            val ch = distinguishedName[end]
            if (escaped) {
                escaped = false
            } else {
                when (ch) {
                    '\\' -> escaped = true
                    '"' -> quoted = !quoted
                    ',' -> if (!quoted) break
                }
            }
            end++
        }

        val rdn = distinguishedName.substring(start, end).trim()
        val separator = rdn.indexOf('=')
        if (separator > 0 && rdn.substring(0, separator).trim().equals("CN", ignoreCase = true)) {
            return unescapeRfc2253Value(rdn.substring(separator + 1).trim())
        }
        start = end + 1
    }
    return null
}

private fun unescapeRfc2253Value(value: String): String {
    val unquoted = if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        value.substring(1, value.length - 1)
    } else {
        value
    }
    val result = StringBuilder(unquoted.length)
    var index = 0
    while (index < unquoted.length) {
        if (unquoted[index] == '\\' && index + 1 < unquoted.length) {
            result.append(unquoted[index + 1])
            index += 2
        } else {
            result.append(unquoted[index])
            index++
        }
    }
    return result.toString()
}

private fun dnsNameMatches(asciiHost: String, certificateName: String): Boolean {
    val trimmedName = certificateName.trim().trimEnd('.')
    if (!trimmedName.contains('*')) {
        val name = runCatching { IDN.toASCII(trimmedName).lowercase() }.getOrNull() ?: return false
        return asciiHost == name
    }

    // Wildcards are certificate syntax rather than IDN input. Validate the wildcard first, then
    // convert only the suffix to ASCII so internationalized suffixes are handled consistently.
    if (!trimmedName.startsWith("*.") || trimmedName.indexOf('*', startIndex = 1) >= 0) return false
    val asciiSuffix = runCatching { IDN.toASCII(trimmedName.substring(2)).lowercase() }
        .getOrNull() ?: return false
    // Never allow a wildcard directly below a TLD (for example, *.com).
    if (!asciiSuffix.contains('.')) return false
    val suffix = ".$asciiSuffix"
    if (!asciiHost.endsWith(suffix)) return false
    val prefix = asciiHost.removeSuffix(suffix)
    return prefix.isNotEmpty() && !prefix.contains('.')
}

private fun verifyCertificateHost(host: String, certificate: X509Certificate) {
    val dnsNames = mutableListOf<String>()
    val ipAddresses = mutableListOf<String>()
    certificate.subjectAlternativeNames?.forEach { entry ->
        val type = entry.getOrNull(0) as? Int
        val value = entry.getOrNull(1) as? String
        when (type) {
            2 -> value?.let(dnsNames::add)
            7 -> value?.let(ipAddresses::add)
        }
    }
    val commonName = commonNameFromRfc2253(certificate.subjectX500Principal.name)

    if (!certificateMatchesHost(host, dnsNames, ipAddresses, commonName)) {
        throw CertificateException("The server certificate does not match $host.")
    }
}

internal fun systemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as java.security.KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}

internal class PinnedCertificateTrustManager(
    private val system: X509TrustManager,
    private val host: String,
    private val expectedFingerprint: String,
) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            system.checkServerTrusted(chain, authType)
            return
        } catch (platformFailure: CertificateException) {
            val leaf = chain.firstOrNull() ?: throw platformFailure
            leaf.checkValidity()
            if (!certificateFingerprint(leaf).equals(expectedFingerprint, ignoreCase = true)) {
                throw CertificateException(
                    "The certificate presented by $host no longer matches the certificate trusted in Actua.",
                    platformFailure,
                )
            }
        }
    }
}

/**
 * Performs only a TLS handshake to inspect the certificate presented by [serverUrl].
 * No HTTP request, password, token, headers, or Actual API data is sent.
 *
 * Trust is deliberately relaxed only inside this probe so the certificate can be shown to the
 * user. HTTPS hostname verification remains enabled via endpoint identification.
 */
fun inspectServerCertificate(serverUrl: String): ServerCertificateInfo {
    val uri = URI(serverUrl)
    require(uri.scheme.equals("https", ignoreCase = true)) { "Certificate trust is only available for HTTPS servers." }
    val host = requireNotNull(uri.host) { "Enter a valid server URL." }
    val port = if (uri.port == -1) 443 else uri.port

    val inspectionTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    }
    val context = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(inspectionTrustManager), SecureRandom())
    }

    val socket = context.socketFactory.createSocket(host, port) as SSLSocket
    return socket.use {
        it.soTimeout = 15_000
        // This probe intentionally bypasses CA-chain trust so Actua can show an unknown
        // certificate to the user. Verify the requested host explicitly against the leaf
        // certificate before offering trust; certificate trust must never bypass server identity.
        it.startHandshake()
        val certificate = it.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw CertificateException("The server did not present an X.509 certificate.")
        certificate.checkValidity()
        verifyCertificateHost(host, certificate)
        ServerCertificateInfo(
            host = host,
            issuer = certificate.issuerX500Principal.name,
            validFrom = certificate.notBefore.toString(),
            validUntil = certificate.notAfter.toString(),
            sha256Fingerprint = certificateFingerprint(certificate),
        )
    }
}
