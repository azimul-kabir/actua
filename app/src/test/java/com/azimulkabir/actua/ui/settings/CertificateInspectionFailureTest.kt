package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.data.network.ServerCertificateExpiredException
import com.azimulkabir.actua.data.network.ServerCertificateHostMismatchException
import com.azimulkabir.actua.data.network.ServerCertificateNotYetValidException
import java.util.Date
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificateInspectionFailureTest {
    @Test
    fun reportsAnExpiredCertificateWithItsExpiryDate() {
        val expiry = Date(1_790_010_661_000)
        assertEquals(
            CertificateInspectionFailure.Expired(expiry),
            certificateInspectionFailure(ServerCertificateExpiredException(expiry)),
        )
    }

    @Test
    fun reportsACertificateThatIsNotYetValid() {
        val start = Date(1_790_010_661_000)
        assertEquals(
            CertificateInspectionFailure.NotYetValid(start),
            certificateInspectionFailure(ServerCertificateNotYetValidException(start)),
        )
    }

    @Test
    fun reportsAHostMismatchFoundInTheCauseChain() {
        val wrapped = RuntimeException("probe failed", ServerCertificateHostMismatchException("actual.example.com"))
        assertEquals(
            CertificateInspectionFailure.HostMismatch("actual.example.com"),
            certificateInspectionFailure(wrapped),
        )
    }

    @Test
    fun returnsNullForOtherFailures() {
        assertNull(certificateInspectionFailure(SSLHandshakeException("TLS protocol negotiation failed")))
    }
}
