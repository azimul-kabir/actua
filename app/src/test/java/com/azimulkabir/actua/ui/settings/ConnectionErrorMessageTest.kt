package com.azimulkabir.actua.ui.settings

import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionErrorMessageTest {
    @Test
    fun explainsAnUntrustedCertificateInsteadOfTheRawExceptionMessage() {
        val certPathError = CertPathValidatorException("Trust anchor for certification path not found")
        val handshakeError = SSLHandshakeException("handshake failed").apply { initCause(certPathError) }

        val messageFromCause = connectionErrorMessage(handshakeError, "Could not connect to the server.")
        val messageFromDirectException = connectionErrorMessage(certPathError, "Could not connect to the server.")

        assertTrue(messageFromCause.contains("CA certificate"))
        assertTrue(messageFromDirectException.contains("CA certificate"))
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
}
