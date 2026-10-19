package com.azimulkabir.actua.data.budget.gdrive

import android.net.Uri
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object GoogleDriveConfig {
    /**
     * Least-privilege Drive scope: permits access only to files and folders created or opened by Actua.
     * Does not request full drive access (which would require CASA Tier 2 third-party security audits).
     */
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke"

    const val API_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
    const val API_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    const val MIME_TYPE_ZIP = "application/zip"
    const val MIME_TYPE_ENCRYPTED_BACKUP = "application/octet-stream"

    const val DEFAULT_ROOT_FOLDER_NAME = "Actua Backups"
    const val REDIRECT_URI = "com.azimulkabir.actua:/oauth2redirect"
    const val DEFAULT_CLIENT_ID = "244721694764-git0bjlcnf2hjvk153dmketdao10nqqn.apps.googleusercontent.com"

    /**
     * PKCE helper: generates a cryptographically random code_verifier (43-128 chars URL-safe).
     */
    fun generateCodeVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * PKCE helper: generates code_challenge from code_verifier using SHA-256 (RFC 7636).
     */
    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /**
     * Builds the authorization URL string for user consent using OAuth 2.0 PKCE.
     */
    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
    ): String {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to SCOPE_DRIVE_FILE,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "access_type" to "offline",
            "prompt" to "consent",
        )
        val query = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$AUTH_ENDPOINT?$query"
    }

    /**
     * Builds the authorization Uri for Android intents.
     */
    fun buildAuthorizationUri(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
    ): Uri = Uri.parse(buildAuthorizationUrl(clientId, redirectUri, codeChallenge, state))
}
