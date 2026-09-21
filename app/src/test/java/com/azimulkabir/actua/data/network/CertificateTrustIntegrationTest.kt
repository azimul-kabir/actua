package com.azimulkabir.actua.data.network

import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real TLS handshake path (a genuine [SSLServerSocket] presenting a real
 * `keytool`-generated certificate) instead of only unit-testing the standalone hostname-matching
 * helpers, per the "Investigation required" / "Tests to add" sections of the GrapheneOS +
 * Tailscale login report.
 */
class CertificateTrustIntegrationTest {
    private val servers = mutableListOf<SSLServerSocket>()
    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        tempFiles.forEach { it.delete() }
    }

    /** Generates a self-signed EC keystore with the given SAN extension string (keytool syntax). */
    private fun generateKeystore(commonName: String, sanExtension: String): File {
        val keystoreFile = File.createTempFile("actua-test-cert", ".p12").apply { deleteOnExit() }
        tempFiles += keystoreFile
        keystoreFile.delete()
        val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
        val process = ProcessBuilder(
            keytool, "-genkeypair",
            "-alias", "server",
            "-keyalg", "EC", "-keysize", "256", "-sigalg", "SHA256withECDSA",
            "-validity", "3650",
            "-keystore", keystoreFile.absolutePath,
            "-storetype", "PKCS12",
            "-storepass", "changeit", "-keypass", "changeit",
            "-dname", "CN=$commonName",
            "-ext", sanExtension,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        require(exit == 0) { "keytool failed ($exit): $output" }
        return keystoreFile
    }

    private fun loadLeafCertificate(keystoreFile: File): X509Certificate {
        val keyStore = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { keyStore.load(it, "changeit".toCharArray()) }
        return keyStore.getCertificate("server") as X509Certificate
    }

    /** Starts a TLS server on loopback presenting [keystoreFile]'s certificate, accepting one connection. */
    private fun startServer(keystoreFile: File): Int {
        val keyStore = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { keyStore.load(it, "changeit".toCharArray()) }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "changeit".toCharArray())
        val context = SSLContext.getInstance("TLS")
        context.init(keyManagerFactory.keyManagers, null, null)
        val serverSocket = context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        servers += serverSocket
        Thread {
            runCatching {
                val socket = serverSocket.accept() as SSLSocket
                socket.startHandshake()
                socket.close()
            }
        }.apply { isDaemon = true; start() }
        return serverSocket.localPort
    }

    @Test
    fun inspectServerCertificate_matchesRealHandshakeAgainstDnsHostname() {
        val keystore = generateKeystore("localhost", "SAN=dns:localhost")
        val port = startServer(keystore)
        val leaf = loadLeafCertificate(keystore)

        val info = inspectServerCertificate("https://localhost:$port")

        assertEquals("localhost", info.host)
        assertEquals(certificateFingerprint(leaf), info.sha256Fingerprint)
    }

    @Test
    fun inspectServerCertificate_failsClosedForIpUrlWithoutMatchingIpSan() {
        // A DNS-only certificate (the common Tailscale/Let's Encrypt shape) must never be
        // accepted for a bare IP connection, even though the same handshake otherwise succeeds.
        val keystore = generateKeystore("localhost", "SAN=dns:localhost")
        val port = startServer(keystore)

        assertThrows(CertificateException::class.java) {
            inspectServerCertificate("https://127.0.0.1:$port")
        }
    }

    @Test
    fun inspectServerCertificate_matchesRealHandshakeAgainstIpSan() {
        val keystore = generateKeystore("127.0.0.1", "SAN=ip:127.0.0.1")
        val port = startServer(keystore)
        val leaf = loadLeafCertificate(keystore)

        val info = inspectServerCertificate("https://127.0.0.1:$port")

        assertEquals(certificateFingerprint(leaf), info.sha256Fingerprint)
    }

    @Test
    fun pinnedCertificateTrustManager_honorsApprovedPin_whenPlatformTrustRejectsSelfSignedChain() {
        // Simulates the GrapheneOS "Trust anchor for certification path not found" failure: the
        // platform trust manager always rejects this self-signed chain, but the user has
        // explicitly pinned its exact fingerprint for this host.
        val keystore = generateKeystore("localhost", "SAN=dns:localhost")
        val leaf = loadLeafCertificate(keystore)
        val rejectingSystemTrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Trust anchor for certification path not found.")
            }
        }
        val trustManager = PinnedCertificateTrustManager(
            system = rejectingSystemTrustManager,
            host = "localhost",
            expectedFingerprint = certificateFingerprint(leaf),
        )

        trustManager.checkServerTrusted(arrayOf(leaf), "ECDHE_ECDSA")
    }

    @Test
    fun pinnedCertificateTrustManager_failsClosed_whenTheCertificateChangesAfterApproval() {
        val trustedKeystore = generateKeystore("localhost", "SAN=dns:localhost")
        val trustedLeaf = loadLeafCertificate(trustedKeystore)
        val newKeystore = generateKeystore("localhost", "SAN=dns:localhost")
        val newLeaf = loadLeafCertificate(newKeystore)
        val rejectingSystemTrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Trust anchor for certification path not found.")
            }
        }
        val trustManager = PinnedCertificateTrustManager(
            system = rejectingSystemTrustManager,
            host = "localhost",
            expectedFingerprint = certificateFingerprint(trustedLeaf),
        )

        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(arrayOf(newLeaf), "ECDHE_ECDSA")
        }
        assertFalse(certificateFingerprint(trustedLeaf) == certificateFingerprint(newLeaf))
    }

    @Test
    fun pinnedCertificateTrustManager_retryConnectsOverRealSocket_usingTheApprovedPin() {
        // End-to-end: probe → trust → retry, all over a real socket, exercising the exact
        // trust manager UrlConnectionTransport installs for a pinned host.
        val keystore = generateKeystore("localhost", "SAN=dns:localhost")
        val port = startServer(keystore)
        val leaf = loadLeafCertificate(keystore)
        val store = TrustedFingerprintOnly(certificateFingerprint(leaf))
        val trustManager = PinnedCertificateTrustManager(
            system = store.systemThatAlwaysRejects(),
            host = "localhost",
            expectedFingerprint = store.fingerprint,
        )
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), null)

        val socket = context.socketFactory.createSocket("localhost", port) as SSLSocket
        socket.use {
            it.soTimeout = 5_000
            it.startHandshake()
            assertTrue(it.session.isValid)
        }
    }

    /** Small holder to keep the end-to-end test above readable. */
    private class TrustedFingerprintOnly(val fingerprint: String) {
        fun systemThatAlwaysRejects(): X509TrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Trust anchor for certification path not found.")
            }
        }
    }
}
