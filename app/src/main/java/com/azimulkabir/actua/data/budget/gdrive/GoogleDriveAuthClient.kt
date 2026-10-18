package com.azimulkabir.actua.data.budget.gdrive

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Pure Kotlin OAuth 2.0 PKCE token exchange and revocation client for Google Drive.
 * Compliant with RFC 8252 and RFC 7636; zero proprietary Google Play Services dependencies.
 */
class GoogleDriveAuthClient(
    private val tokenEndpoint: String = GoogleDriveConfig.TOKEN_ENDPOINT,
    private val revocationEndpoint: String = GoogleDriveConfig.REVOCATION_ENDPOINT,
) {

    fun exchangeAuthorizationCode(
        clientId: String,
        redirectUri: String,
        code: String,
        codeVerifier: String,
    ): GoogleDriveTokens {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "code" to code,
            "code_verifier" to codeVerifier,
            "grant_type" to "authorization_code",
        )
        val formBody = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        val url = URL(tokenEndpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
            it.write(formBody)
            it.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode == 401) throw GoogleDriveException.Unauthorized
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.ApiError(responseCode, errorBody)
        }

        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank)
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = (System.currentTimeMillis() / 1000) + expiresIn

        return GoogleDriveTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
            accountEmail = null,
        )
    }

    fun refreshAccessToken(
        clientId: String,
        refreshToken: String,
    ): GoogleDriveTokens {
        val params = listOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        )
        val formBody = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        val url = URL(tokenEndpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
            it.write(formBody)
            it.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode in listOf(400, 401)) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.Unauthorized
        }
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.ApiError(responseCode, errorBody)
        }

        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val accessToken = json.getString("access_token")
        val newRefreshToken = json.optString("refresh_token").takeIf(String::isNotBlank) ?: refreshToken
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = (System.currentTimeMillis() / 1000) + expiresIn

        return GoogleDriveTokens(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            expiresAtEpochSeconds = expiresAt,
            accountEmail = null,
        )
    }

    fun revokeToken(token: String): Boolean {
        return runCatching {
            val url = URL("$revocationEndpoint?token=${URLEncoder.encode(token, "UTF-8")}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.responseCode in 200..299
        }.getOrDefault(false)
    }
}
