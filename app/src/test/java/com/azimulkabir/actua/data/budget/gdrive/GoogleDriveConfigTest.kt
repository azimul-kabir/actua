package com.azimulkabir.actua.data.budget.gdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class GoogleDriveConfigTest {

    @Test
    fun scopeIsLeastPrivilegeDriveFile() {
        assertEquals("https://www.googleapis.com/auth/drive.file", GoogleDriveConfig.SCOPE_DRIVE_FILE)
    }

    @Test
    fun codeVerifierMeetsPkceLengthAndCharset() {
        val verifier1 = GoogleDriveConfig.generateCodeVerifier()
        val verifier2 = GoogleDriveConfig.generateCodeVerifier()

        assertNotEquals(verifier1, verifier2)
        assertTrue(verifier1.length >= 43)
        // URL-safe charset only: alphanumeric, '-', '_'
        assertTrue(verifier1.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun codeChallengeIsDeterministicSha256() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge1 = GoogleDriveConfig.generateCodeChallenge(verifier)
        val challenge2 = GoogleDriveConfig.generateCodeChallenge(verifier)

        assertEquals(challenge1, challenge2)
        assertFalse(challenge1.contains("+"))
        assertFalse(challenge1.contains("/"))
        assertFalse(challenge1.contains("="))
    }

    @Test
    fun authorizationUrlContainsAllRequiredPkceParameters() {
        val url = GoogleDriveConfig.buildAuthorizationUrl(
            clientId = "test-client-id.apps.googleusercontent.com",
            redirectUri = "com.azimulkabir.actua:/oauth2redirect",
            codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            state = "random-state-12345",
        )

        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("accounts.google.com", uri.host)
        assertEquals("/o/oauth2/v2/auth", uri.path)

        val queryParams = uri.query.split("&").associate {
            val (k, v) = it.split("=")
            k to java.net.URLDecoder.decode(v, "UTF-8")
        }

        assertEquals("test-client-id.apps.googleusercontent.com", queryParams["client_id"])
        assertEquals("com.azimulkabir.actua:/oauth2redirect", queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"])
        assertEquals(GoogleDriveConfig.SCOPE_DRIVE_FILE, queryParams["scope"])
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", queryParams["code_challenge"])
        assertEquals("S256", queryParams["code_challenge_method"])
        assertEquals("random-state-12345", queryParams["state"])
        assertEquals("offline", queryParams["access_type"])
        assertEquals("consent", queryParams["prompt"])
    }

    @Test
    fun defaultClientIdIsConfigured() {
        assertEquals("244721694764-git0bjlcnf2hjvk153dmketdao10nqqn.apps.googleusercontent.com", GoogleDriveConfig.DEFAULT_CLIENT_ID)
        assertTrue(GoogleDriveConfig.DEFAULT_CLIENT_ID.endsWith(".apps.googleusercontent.com"))
    }
}
