package com.azimulkabir.actua.data.network

import android.content.Context
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
        it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        it.startHandshake()
        val certificate = it.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw CertificateException("The server did not present an X.509 certificate.")
        ServerCertificateInfo(
            host = host,
            issuer = certificate.issuerX500Principal.name,
            validFrom = certificate.notBefore.toString(),
            validUntil = certificate.notAfter.toString(),
            sha256Fingerprint = certificateFingerprint(certificate),
        )
    }
}
