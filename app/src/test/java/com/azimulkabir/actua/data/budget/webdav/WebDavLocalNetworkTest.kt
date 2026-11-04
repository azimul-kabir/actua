package com.azimulkabir.actua.data.budget.webdav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WebDavLocalNetworkTest {
    private val android17 = WebDavLocalNetwork.FIRST_RESTRICTED_SDK
    private val android16 = WebDavLocalNetwork.FIRST_RESTRICTED_SDK - 1

    private fun needsPrompt(url: String, sdk: Int = android17, granted: Boolean = false) =
        WebDavLocalNetwork.needsPermissionPrompt(url, sdk, granted)

    @Test fun aServerOnTheHomeNetworkNeedsThePromptOnAndroid17() {
        assertTrue(needsPrompt("https://192.168.1.50:5006/"))
        assertTrue(needsPrompt("http://10.0.0.5:8080/webdav/"))
        assertTrue(needsPrompt("https://172.16.4.2/remote.php/dav/files/me/"))
        assertTrue(needsPrompt("https://[fd12:3456::1]/"))
    }

    @Test fun anMdnsNameIsAlwaysLocal() {
        assertTrue(needsPrompt("https://nas.local/remote.php/dav/files/me/"))
        assertTrue(needsPrompt("https://my-nas.local:5006/"))
    }

    @Test fun aPublicAddressNeverNeedsThePrompt() {
        assertFalse(needsPrompt("https://8.8.8.8/dav/"))
        assertFalse(needsPrompt("https://[2001:4860:4860::8888]/dav/"))
        assertFalse(
            WebDavLocalNetwork.needsPermissionPrompt("https://cloud.example.com/dav/", android17, false, isLocal = { false }),
        )
    }

    @Test fun loopbackStaysOnTheDeviceAndIsNotLocalNetwork() {
        assertFalse(needsPrompt("http://127.0.0.1:8080/"))
        assertFalse(needsPrompt("http://[::1]:8080/"))
    }

    @Test fun androidBefore17OrAnAlreadyGrantedPermissionNeedsNoPrompt() {
        assertFalse(needsPrompt("https://192.168.1.50/", sdk = android16))
        assertFalse(needsPrompt("https://192.168.1.50/", sdk = 28))
        assertFalse(needsPrompt("https://192.168.1.50/", granted = true))
        assertFalse(needsPrompt("https://nas.local/", sdk = android16))
    }

    @Test fun aBlankAddressNeedsNoPrompt() {
        assertFalse(needsPrompt(""))
        assertFalse(needsPrompt("   "))
    }

    // --- Explaining a failure ---------------------------------------------------------------------------------

    private fun explains(
        error: Throwable,
        url: String? = "https://192.168.1.50:5006/",
        sdk: Int = android17,
        granted: Boolean = false,
    ) = WebDavLocalNetwork.explainsFailure(error, url, sdk, granted)

    @Test fun anUnreachableLocalServerWithoutThePermissionIsExplainedByIt() {
        assertTrue(explains(WebDavException.Network(IOException("connect failed"))))
        assertTrue(explains(WebDavException.Network(IOException("timeout")), url = "https://nas.local/"))
    }

    @Test fun otherFailuresAreNotBlamedOnThePermission() {
        assertFalse(explains(WebDavException.Unauthorized(401)))
        assertFalse(explains(WebDavException.NotFound()))
        assertFalse(explains(WebDavException.Tls(IOException("cert"))))
        assertFalse(explains(WebDavException.Server(500, "")))
        assertFalse(explains(IllegalStateException("boom")))
    }

    @Test fun aNetworkErrorIsNotBlamedOnThePermissionWhenItCannotBeTheCause() {
        val error = WebDavException.Network(IOException("connect failed"))
        assertFalse(explains(error, sdk = android16))
        assertFalse(explains(error, granted = true))
        assertFalse(explains(error, url = "https://8.8.8.8/dav/"))
        assertFalse(explains(error, url = null))
        assertFalse(explains(error, url = ""))
    }
}
