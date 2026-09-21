package com.azimulkabir.actua.data.network

import com.azimulkabir.actua.data.sync.ServerKeyInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

data class LoginMethod(val method: String, val displayName: String, val active: Boolean)
data class RemoteBudgetFile(val fileId: String, val groupId: String?, val name: String, val encryptedKeyId: String?)
data class ActualFileInfo(
    val fileId: String,
    val groupId: String?,
    val name: String,
    val deleted: Boolean,
    val encryption: FileEncryptionMetadata?,
)
data class FileEncryptionMetadata(
    val keyId: String,
    val algorithm: String?,
    val ivBase64: String?,
    val authTagBase64: String?,
)

data class BankSyncTransaction(
    val financialId: String,
    val date: Int,
    val amountCents: Long,
    val payeeName: String,
    val notes: String?,
    val booked: Boolean,
)

data class BankSyncDownload(
    val externalAccountId: String,
    val transactions: List<BankSyncTransaction>,
    val status: String,
    val problem: String? = null,
)

data class SimpleFinAccount(val id: String, val name: String, val orgName: String?)
data class GoCardlessInstitution(val id: String, val name: String, val transactionTotalDays: Int?)
data class GoCardlessWebToken(val link: String, val requisitionId: String)
data class GoCardlessAccount(val id: String, val iban: String?, val name: String?, val institutionId: String?)

/** A discovered SimpleFIN account, or why none could be listed. */
sealed class SimpleFinAccountsResult {
    data class Available(val accounts: List<SimpleFinAccount>) : SimpleFinAccountsResult()
    data class Error(val reason: String) : SimpleFinAccountsResult()
}

/** Discovered GoCardless accounts for a requisition, or why none are available yet. */
sealed class GoCardlessAccountsResult {
    data class Available(val accounts: List<GoCardlessAccount>) : GoCardlessAccountsResult()
    /** The requisition exists but the user has not finished authorizing it at their bank yet. */
    data class Pending(val status: String) : GoCardlessAccountsResult()
    data class Error(val reason: String) : GoCardlessAccountsResult()
}

sealed class ActualServerException(message: String) : Exception(message) {
    data object Unauthorized : ActualServerException("Unauthorized")
    data object FileNotFound : ActualServerException("Budget file not found")
    data object InvalidResponse : ActualServerException("The server returned an invalid response")
    class Http(val status: Int, body: String) : ActualServerException("HTTP $status: $body")
}

data class ActualHttpRequest(
    val url: URL,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)
data class ActualHttpResponse(val status: Int, val body: ByteArray)
fun interface ActualHttpTransport { fun execute(request: ActualHttpRequest): ActualHttpResponse }

class UrlConnectionTransport(
    private val certificateStore: TrustedCertificateStore? = null,
) : ActualHttpTransport {
    override fun execute(request: ActualHttpRequest): ActualHttpResponse {
        val connection = request.url.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            certificateStore?.fingerprint(request.url.host)?.let { fingerprint ->
                val trustManager = PinnedCertificateTrustManager(
                    system = systemTrustManager(),
                    host = request.url.host,
                    expectedFingerprint = fingerprint,
                )
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustManager), null)
                }
                connection.sslSocketFactory = sslContext.socketFactory
                // Keep HttpsURLConnection's default hostname verifier. The pin only supplements
                // certificate-chain trust; it never disables hostname verification.
            }
        }
        return try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            ActualHttpResponse(status, stream?.use { it.readBytes() } ?: byteArrayOf())
        } finally {
            connection.disconnect()
        }
    }
}

class ActualServerClient(private val transport: ActualHttpTransport = UrlConnectionTransport()) {
    /** Sent with every request, e.g. Cloudflare Access service-token headers. Never overrides a protocol header. */
    var customHeaders: Map<String, String> = emptyMap()

    fun normalizeServerUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = URI(withScheme)
        require(uri.scheme == "https" || uri.scheme == "http") { "Use an http or https server URL." }
        require(!uri.host.isNullOrBlank()) { "Enter a valid server URL." }
        require(uri.scheme == "https" || isPrivateHost(uri.host)) {
            "Cleartext HTTP is only allowed for a private or local server address."
        }
        return withScheme
    }

    private fun isPrivateHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true
        if (host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")) return true
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 || octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31)
    }

    fun loginMethods(serverUrl: String): List<LoginMethod> {
        val response = request(serverUrl, "/account/login-methods", "GET")
        if (response.status == 404) return listOf(LoginMethod("password", "Password", true))
        requireSuccess(response)
        val json = response.json()
        if (json.optString("status") != "ok") throw ActualServerException.InvalidResponse
        val methods = json.optJSONArray("methods") ?: return listOf(LoginMethod("password", "Password", true))
        return buildList {
            for (index in 0 until methods.length()) {
                val item = methods.getJSONObject(index)
                add(LoginMethod(item.getString("method"), item.optString("displayName", item.getString("method")), item.optInt("active", 0) != 0))
            }
        }
    }

    fun login(serverUrl: String, password: String): String {
        val response = request(
            serverUrl, "/account/login", "POST", mapOf("Content-Type" to "application/json"),
            JSONObject()
                .put("loginMethod", "password")
                .put("password", password)
                .toString()
                .encodeToByteArray(),
        )
        if (response.status == 400 || response.status == 401) error(loginError(response, "Incorrect server password."))
        requireSuccess(response)
        val json = response.json()
        if (json.optString("status") != "ok") error(json.optString("reason", "Login failed."))
        return json.getJSONObject("data").getString("token")
    }

    /**
     * Starts Actual's server-mediated OpenID Connect flow. The returned URL is the identity-provider
     * authorization URL that should be opened in the user's browser. Actual will eventually redirect
     * to [returnUrl]/openid-cb?token=... after completing the provider callback.
     */
    fun startOpenIdLogin(serverUrl: String, returnUrl: String, password: String = ""): String {
        val body = JSONObject()
            .put("loginMethod", "openid")
            .put("returnUrl", returnUrl.trimEnd('/'))
            .apply { if (password.isNotBlank()) put("password", password) }
            .toString()
            .encodeToByteArray()
        val response = request(
            serverUrl,
            "/account/login",
            "POST",
            mapOf("Content-Type" to "application/json"),
            body,
        )
        if (response.status == 400 || response.status == 401) {
            error(loginError(response, "Could not start OpenID sign-in."))
        }
        requireSuccess(response)
        val json = response.json()
        if (json.optString("status") != "ok") error(json.optString("reason", "Could not start OpenID sign-in."))
        return json.optJSONObject("data")?.optString("returnUrl")?.takeIf(String::isNotBlank)
            ?: throw ActualServerException.InvalidResponse
    }

    fun listFiles(serverUrl: String, token: String): List<RemoteBudgetFile> {
        val json = authenticatedGet(serverUrl, "/sync/list-user-files", token).json()
        if (json.optString("status") != "ok") throw ActualServerException.InvalidResponse
        val files = json.optJSONArray("data") ?: throw ActualServerException.InvalidResponse
        return buildList {
            for (index in 0 until files.length()) {
                val file = files.getJSONObject(index)
                if (file.optInt("deleted", 0) == 0) {
                    add(RemoteBudgetFile(file.getString("fileId"), file.optionalString("groupId"), file.getString("name"), file.optionalString("encryptKeyId")))
                }
            }
        }
    }

    fun downloadFile(serverUrl: String, token: String, fileId: String): ByteArray {
        val response = request(serverUrl, "/sync/download-user-file", "GET", actualHeaders(token) + ("X-ACTUAL-FILE-ID" to fileId))
        checkAuthorization(response)
        if (response.status == 400 || response.status == 404) throw ActualServerException.FileNotFound
        requireSuccess(response)
        return response.body
    }

    fun uploadFile(serverUrl: String, token: String, fileId: String, name: String, archive: ByteArray): String {
        val encodedName = encodeURIComponent(name)
        val response = request(
            serverUrl, "/sync/upload-user-file", "POST",
            actualHeaders(token) + mapOf(
                "X-ACTUAL-FILE-ID" to fileId,
                "X-ACTUAL-NAME" to encodedName,
                "X-ACTUAL-FORMAT" to "2",
                "Content-Type" to "application/encrypted-file",
            ),
            archive,
        )
        checkAuthorization(response)
        requireSuccess(response)
        val json = response.json()
        if (json.optString("status") != "ok") throw ActualServerException.InvalidResponse
        return json.optString("groupId").takeIf(String::isNotBlank)
            ?: throw ActualServerException.InvalidResponse
    }

    fun deleteFile(serverUrl: String, token: String, fileId: String) {
        val body = JSONObject().put("token", token).put("fileId", fileId).toString().encodeToByteArray()
        val response = request(
            serverUrl, "/sync/delete-user-file", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        if (response.status == 400 && response.body.decodeToString() == "file-not-found") {
            throw ActualServerException.FileNotFound
        }
        requireSuccess(response)
    }

    fun getFileInfo(serverUrl: String, token: String, fileId: String): ActualFileInfo {
        val response = request(serverUrl, "/sync/get-user-file-info", "GET", actualHeaders(token) + ("X-ACTUAL-FILE-ID" to fileId))
        checkAuthorization(response)
        requireSuccess(response)
        val root = response.json()
        val data = if (root.optString("status") == "ok") root.optJSONObject("data") else null
            ?: throw ActualServerException.FileNotFound
        val encryption = data.optJSONObject("encryptMeta")?.let {
            FileEncryptionMetadata(it.getString("keyId"), it.optionalString("algorithm"), it.optionalString("iv"), it.optionalString("authTag"))
        }
        return ActualFileInfo(data.getString("fileId"), data.optionalString("groupId"), data.getString("name"), data.optInt("deleted", 0) != 0, encryption)
    }

    fun getKeyInfo(serverUrl: String, token: String, fileId: String): ServerKeyInfo {
        val body = JSONObject().put("token", token).put("fileId", fileId).toString().encodeToByteArray()
        val response = request(serverUrl, "/sync/user-get-key", "POST", actualHeaders(token) + ("Content-Type" to "application/json"), body)
        checkAuthorization(response)
        requireSuccess(response)
        val root = response.json()
        val data = if (root.optString("status") == "ok") root.optJSONObject("data") else null
            ?: throw ActualServerException.InvalidResponse
        return ServerKeyInfo(data.getString("id"), data.getString("salt"), data.optionalString("test"))
    }

    fun postSync(serverUrl: String, token: String, requestData: ByteArray): ByteArray {
        val response = request(
            serverUrl, "/sync/sync", "POST",
            actualHeaders(token) + ("Content-Type" to "application/actual-sync"), requestData,
        )
        checkAuthorization(response)
        requireSuccess(response)
        return response.body
    }

    /** Downloads Actual's normalized server-hosted SimpleFIN feed. */
    fun downloadSimpleFinTransactions(
        serverUrl: String,
        token: String,
        accountIds: List<String>,
        startDates: List<String>,
    ): List<BankSyncDownload> {
        require(accountIds.isNotEmpty() && accountIds.size == startDates.size)
        val body = JSONObject()
            .put("accountId", org.json.JSONArray(accountIds))
            .put("startDate", org.json.JSONArray(startDates))
            .toString().encodeToByteArray()
        val response = request(
            serverUrl, "/simplefin/transactions", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        if (response.status in setOf(404, 405, 501)) {
            throw IllegalStateException("This Actual server does not support SimpleFIN bank sync.")
        }
        requireSuccess(response)
        return parseSimpleFinDownloads(response.json(), accountIds)
    }

    /** Stores an admin-managed provider secret (e.g. `simplefin_token`, `gocardless_secretId`) on the server. */
    fun setSecret(serverUrl: String, token: String, name: String, value: String) {
        val body = JSONObject().put("name", name).put("value", value).toString().encodeToByteArray()
        val response = request(
            serverUrl, "/secret/", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        if (response.status == 403) error("You must be an admin on this server to configure bank sync.")
        requireSuccess(response)
    }

    fun simpleFinStatus(serverUrl: String, token: String): Boolean =
        providerConfigured(serverUrl, token, "/simplefin/status")

    fun goCardlessStatus(serverUrl: String, token: String): Boolean =
        providerConfigured(serverUrl, token, "/gocardless/status")

    private fun providerConfigured(serverUrl: String, token: String, path: String): Boolean {
        val response = request(serverUrl, path, "POST", actualHeaders(token))
        checkAuthorization(response)
        if (response.status in setOf(404, 405, 501)) return false
        requireSuccess(response)
        return response.json().optJSONObject("data")?.optBoolean("configured", false) ?: false
    }

    fun simpleFinAccounts(serverUrl: String, token: String): SimpleFinAccountsResult {
        val response = request(serverUrl, "/simplefin/accounts", "POST", actualHeaders(token))
        checkAuthorization(response)
        requireSuccess(response)
        val data = response.json().optJSONObject("data") ?: throw ActualServerException.InvalidResponse
        data.optString("error_code").takeIf(String::isNotBlank)?.let {
            return SimpleFinAccountsResult.Error(data.optString("reason", "SimpleFIN error ($it)."))
        }
        val accounts = data.optJSONArray("accounts") ?: return SimpleFinAccountsResult.Available(emptyList())
        return SimpleFinAccountsResult.Available(buildList {
            for (index in 0 until accounts.length()) {
                val item = accounts.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                add(SimpleFinAccount(id, item.optString("name", id), item.optJSONObject("org")?.optString("name")))
            }
        })
    }

    fun goCardlessInstitutions(serverUrl: String, token: String, country: String): List<GoCardlessInstitution> {
        val body = JSONObject().put("country", country).toString().encodeToByteArray()
        val response = request(
            serverUrl, "/gocardless/get-banks", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        requireSuccess(response)
        val data = response.json().optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                add(GoCardlessInstitution(id, item.optString("name", id), item.optInt("transaction_total_days", 0).takeIf { it > 0 }))
            }
        }
    }

    /** Starts a GoCardless bank authorization. Open [GoCardlessWebToken.link] in a browser for the user to complete it. */
    fun goCardlessCreateWebToken(serverUrl: String, token: String, institutionId: String, origin: String): GoCardlessWebToken {
        val body = JSONObject().put("institutionId", institutionId).put("origin", origin).toString().encodeToByteArray()
        val response = request(
            serverUrl, "/gocardless/create-web-token", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        requireSuccess(response)
        val data = response.json().optJSONObject("data") ?: throw ActualServerException.InvalidResponse
        val link = data.optString("link").takeIf(String::isNotBlank) ?: throw ActualServerException.InvalidResponse
        val requisitionId = data.optString("requisitionId").takeIf(String::isNotBlank) ?: throw ActualServerException.InvalidResponse
        return GoCardlessWebToken(link, requisitionId)
    }

    /** Call after the user returns from authorizing their bank, to check whether accounts are ready to link. */
    fun goCardlessAccounts(serverUrl: String, token: String, requisitionId: String): GoCardlessAccountsResult {
        val body = JSONObject().put("requisitionId", requisitionId).toString().encodeToByteArray()
        val response = request(
            serverUrl, "/gocardless/get-accounts", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        requireSuccess(response)
        val root = response.json()
        root.optString("requisitionStatus").takeIf(String::isNotBlank)?.let {
            return GoCardlessAccountsResult.Pending(it)
        }
        val data = root.optJSONObject("data") ?: throw ActualServerException.InvalidResponse
        data.optString("error_code").takeIf(String::isNotBlank)?.let {
            return GoCardlessAccountsResult.Error(data.optString("reason", "GoCardless error ($it)."))
        }
        val accounts = data.optJSONArray("accounts") ?: return GoCardlessAccountsResult.Available(emptyList())
        return GoCardlessAccountsResult.Available(buildList {
            for (index in 0 until accounts.length()) {
                val item = accounts.optJSONObject(index) ?: continue
                val id = item.optString("account_id", item.optString("id")).takeIf(String::isNotBlank) ?: continue
                add(GoCardlessAccount(
                    id, item.optionalString("iban"), item.optionalString("name") ?: item.optionalString("displayName"),
                    item.optionalString("institution_id"),
                ))
            }
        })
    }

    /** Downloads Actual's normalized GoCardless feed for a single linked account. */
    fun downloadGoCardlessTransactions(
        serverUrl: String, token: String, requisitionId: String, accountId: String,
        startDate: String, endDate: String,
    ): BankSyncDownload {
        val body = JSONObject().put("requisitionId", requisitionId).put("accountId", accountId)
            .put("startDate", startDate).put("endDate", endDate).toString().encodeToByteArray()
        val response = request(
            serverUrl, "/gocardless/transactions", "POST",
            actualHeaders(token) + ("Content-Type" to "application/json"), body,
        )
        checkAuthorization(response)
        if (response.status in setOf(404, 405, 501)) {
            throw IllegalStateException("This Actual server does not support GoCardless bank sync.")
        }
        requireSuccess(response)
        return parseSingleBankSyncDownload(response.json(), accountId)
    }

    private fun authenticatedGet(serverUrl: String, path: String, token: String): ActualHttpResponse {
        val response = request(serverUrl, path, "GET", actualHeaders(token))
        checkAuthorization(response)
        requireSuccess(response)
        return response
    }

    private fun request(
        serverUrl: String,
        path: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): ActualHttpResponse = transport.execute(
        ActualHttpRequest(
            URL(normalizeServerUrl(serverUrl) + path), method,
            mapOf("Accept" to "application/json") + customHeaders + headers, body,
        ),
    )

    private fun actualHeaders(token: String) = mapOf("X-ACTUAL-TOKEN" to token)

    internal fun parseSimpleFinDownloads(root: JSONObject, accountIds: List<String>): List<BankSyncDownload> {
        val data = root.optJSONObject("data") ?: throw ActualServerException.InvalidResponse
        data.optString("error_code").takeIf(String::isNotBlank)?.let {
            error(data.optString("reason", "Bank sync failed ($it)."))
        }
        val errors = data.optJSONObject("errors")
        return accountIds.mapNotNull { accountId ->
            val account = data.optJSONObject(accountId)
            val accountErrors = errors?.optJSONArray(accountId)
            val firstError = accountErrors?.optJSONObject(0)
            if (account == null && firstError == null) return@mapNotNull null
            val status = firstError?.optString("error_code")?.let(::bankSyncStatus) ?: "ok"
            val transactions = parseBankSyncRows(account?.optJSONObject("transactions")?.optJSONArray("all"))
            BankSyncDownload(accountId, transactions, status,
                firstError?.optString("reason")?.takeIf(String::isNotBlank))
        }
    }

    /** A single-account normalized bank-sync response, e.g. GoCardless's `/transactions`. */
    internal fun parseSingleBankSyncDownload(root: JSONObject, accountId: String): BankSyncDownload {
        val data = root.optJSONObject("data") ?: throw ActualServerException.InvalidResponse
        data.optString("error_code").takeIf(String::isNotBlank)?.let {
            return BankSyncDownload(accountId, emptyList(), bankSyncStatus(it), data.optString("reason", "Bank sync failed ($it)."))
        }
        return BankSyncDownload(accountId, parseBankSyncRows(data.optJSONObject("transactions")?.optJSONArray("all")), "ok")
    }

    private fun parseBankSyncRows(all: org.json.JSONArray?): List<BankSyncTransaction> = buildList {
        if (all == null) return@buildList
        for (index in 0 until all.length()) {
            val item = all.optJSONObject(index) ?: continue
            val id = item.optString("transactionId").takeIf(String::isNotBlank) ?: continue
            val date = item.optString("date").replace("-", "").toIntOrNull() ?: continue
            val amount = item.optJSONObject("transactionAmount")?.optString("amount")
                ?.toBigDecimalOrNull()?.movePointRight(2)?.let {
                    runCatching { it.longValueExact() }.getOrNull()
                } ?: continue
            add(BankSyncTransaction(
                financialId = id,
                date = date,
                amountCents = amount,
                payeeName = item.optString("payeeName").ifBlank { "Unknown" },
                notes = item.optString("notes").takeIf(String::isNotBlank),
                booked = item.optBoolean("booked", true),
            ))
        }
    }

    private fun bankSyncStatus(code: String): String = when (code) {
        "ITEM_LOGIN_REQUIRED", "INVALID_ACCESS_TOKEN" -> "reauth-required"
        "ACCOUNT_NEEDS_ATTENTION" -> "attention-required"
        "RATE_LIMIT_EXCEEDED" -> "rate-limit-exceeded"
        "TIMED_OUT" -> "timed-out"
        "ACCOUNT_MISSING" -> "account-missing"
        else -> "failed"
    }
    private fun checkAuthorization(response: ActualHttpResponse) {
        if (response.status == 401 || response.status == 403) throw ActualServerException.Unauthorized
    }
    private fun requireSuccess(response: ActualHttpResponse) {
        if (response.status != 200) throw ActualServerException.Http(response.status, response.body.decodeToString())
    }
    private fun loginError(response: ActualHttpResponse, fallback: String): String = runCatching {
        JSONObject(response.body.decodeToString()).optString("reason").takeIf(String::isNotBlank)
    }.getOrNull() ?: fallback
    private fun ActualHttpResponse.json(): JSONObject = try {
        JSONObject(body.decodeToString())
    } catch (_: Exception) {
        throw ActualServerException.InvalidResponse
    }
}

private fun encodeURIComponent(value: String): String = buildString {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xff
        val character = code.toChar()
        if (character in unreserved) append(character) else append("%%%02X".format(code))
    }
}

private fun JSONObject.optionalString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf(String::isNotBlank) else null
