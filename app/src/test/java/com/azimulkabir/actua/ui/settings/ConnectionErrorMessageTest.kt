package com.azimulkabir.actua.ui.settings

import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionErrorMessageTest {
    @Test
    fun explainsAnUntrustedCertificateInsteadOfTheRawExceptionMessage() {
        val certPathError = CertPathValidatorException("Trust anchor for certification path not found")
        val handshakeError = SSLHandshakeException("handshake failed").apply { initCause(certPathError) }
        val certificateMessage = "Server certificate isn’t trusted. Actua couldn’t verify this server’s TLS certificate."

        val messageFromCause = connectionErrorMessage(handshakeError, "Could not connect to the server.", certificateMessage)
        val messageFromDirectException = connectionErrorMessage(certPathError, "Could not connect to the server.", certificateMessage)

        assertEquals(certificateMessage, messageFromCause)
        assertEquals(certificateMessage, messageFromDirectException)
    }

    @Test
    fun doesNotMisclassifyGenericTlsHandshakeFailureAsCertificateTrustFailure() {
        val error = SSLHandshakeException("TLS protocol negotiation failed")
        assertEquals(
            "TLS protocol negotiation failed",
            connectionErrorMessage(error, "Could not connect to the server."),
        )
    }

    @Test
    fun leavesOtherErrorsUntouched() {
        val error = IllegalStateException("Incorrect server password.")
        assertEquals("Incorrect server password.", connectionErrorMessage(error, "Could not connect to the server."))
    }

    @Test
    fun fallsBackWhenTheErrorHasNoMessage() {
        val error = RuntimeException()
        assertEquals("Could not connect to the server.", connectionErrorMessage(error, "Could not connect to the server."))
    }

    @Test
    fun certificateTrustMessageDefaultsToFallbackWhenNotProvided() {
        val error = CertPathValidatorException("Trust anchor for certification path not found")
        assertEquals("Could not connect to the server.", connectionErrorMessage(error, "Could not connect to the server."))
    }
}
