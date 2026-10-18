package com.azimulkabir.actua.data.budget.gdrive

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class GoogleDriveFileMetadata(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String?,
    val appProperties: Map<String, String>,
)

data class GoogleDriveUserInfo(
    val displayName: String?,
    val emailAddress: String?,
)

sealed class GoogleDriveException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object Unauthorized : GoogleDriveException("Google Drive authentication token expired or invalid")
    data object QuotaExceeded : GoogleDriveException("Google Drive storage or API quota exceeded")
    data class ApiError(val statusCode: Int, val serverMessage: String) : GoogleDriveException("Drive API error ($statusCode): $serverMessage")
}

/**
 * Pure Kotlin / Android HTTP client for Google Drive REST API v3.
 * Zero proprietary Google Play Services dependencies; fully compliant with F-Droid.
 */
class GoogleDriveRestClient(
    private val tokenProvider: () -> String?,
    private val apiBaseUrl: String = "https://www.googleapis.com",
    private val uploadBaseUrl: String = "https://www.googleapis.com/upload",
) {

    fun listBackups(parentFolderId: String? = null): List<GoogleDriveFileMetadata> {
        val query = buildString {
            append("trashed = false")
            if (!parentFolderId.isNullOrBlank()) {
                append(" and '$parentFolderId' in parents")
            }
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val fields = URLEncoder.encode("files(id,name,mimeType,size,modifiedTime,appProperties)", "UTF-8")
        val url = URL("$apiBaseUrl/drive/v3/files?q=$encodedQuery&fields=$fields&pageSize=100")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        val responseCode = connection.responseCode
        if (responseCode == 401) throw GoogleDriveException.Unauthorized
        if (responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.ApiError(responseCode, errorText)
        }

        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val filesArray = json.optJSONArray("files") ?: JSONArray()
        return (0 until filesArray.length()).map { i ->
            parseFileMetadata(filesArray.getJSONObject(i))
        }
    }

    fun uploadMultipart(
        name: String,
        content: InputStream,
        mimeType: String,
        parentFolderId: String? = null,
        appProperties: Map<String, String> = emptyMap(),
    ): GoogleDriveFileMetadata {
        val boundary = "actua-boundary-${UUID.randomUUID()}"
        val url = URL("$uploadBaseUrl/drive/v3/files?uploadType=multipart")

        val metadataJson = JSONObject().apply {
            put("name", name)
            if (!parentFolderId.isNullOrBlank()) {
                put("parents", JSONArray().put(parentFolderId))
            }
            if (appProperties.isNotEmpty()) {
                val props = JSONObject()
                appProperties.forEach { (k, v) -> props.put(k, v) }
                put("appProperties", props)
            }
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 60_000
        }

        BufferedOutputStream(connection.outputStream).use { out ->
            // Part 1: Metadata
            out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(metadataJson.toString().toByteArray(Charsets.UTF_8))
            out.write("\r\n".toByteArray(Charsets.UTF_8))

            // Part 2: Media Content
            out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray(Charsets.UTF_8))
            content.copyTo(out)
            out.write("\r\n".toByteArray(Charsets.UTF_8))

            // End
            out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            out.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode == 401) throw GoogleDriveException.Unauthorized
        if (responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.ApiError(responseCode, errorText)
        }

        val responseJson = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        return parseFileMetadata(responseJson)
    }

    fun downloadFile(fileId: String, output: OutputStream): Long {
        val url = URL("$apiBaseUrl/drive/v3/files/$fileId?alt=media")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            connectTimeout = 15_000
            readTimeout = 60_000
        }

        val responseCode = connection.responseCode
        if (responseCode == 401) throw GoogleDriveException.Unauthorized
        if (responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.ApiError(responseCode, errorText)
        }

        return BufferedInputStream(connection.inputStream).use { input ->
            input.copyTo(output)
        }
    }

    fun deleteFile(fileId: String): Boolean {
        val url = URL("$apiBaseUrl/drive/v3/files/$fileId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        val responseCode = connection.responseCode
        if (responseCode == 401) throw GoogleDriveException.Unauthorized
        return responseCode in 200..299 || responseCode == 404
    }

    fun findOrCreateFolder(folderName: String, parentFolderId: String? = null): String {
        val query = buildString {
            append("name = '$folderName' and mimeType = '${GoogleDriveConfig.MIME_TYPE_FOLDER}' and trashed = false")
            if (!parentFolderId.isNullOrBlank()) {
                append(" and '$parentFolderId' in parents")
            }
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val listUrl = URL("$apiBaseUrl/drive/v3/files?q=$encodedQuery&fields=files(id,name)&pageSize=1")

        val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            setRequestProperty("Accept", "application/json")
        }
        if (listConn.responseCode == 401) throw GoogleDriveException.Unauthorized
        if (listConn.responseCode in 200..299) {
            val json = JSONObject(listConn.inputStream.bufferedReader().use { it.readText() })
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).getString("id")
            }
        }

        // Folder not found: create it
        val createUrl = URL("$apiBaseUrl/drive/v3/files")
        val createJson = JSONObject().apply {
            put("name", folderName)
            put("mimeType", GoogleDriveConfig.MIME_TYPE_FOLDER)
            if (!parentFolderId.isNullOrBlank()) {
                put("parents", JSONArray().put(parentFolderId))
            }
        }

        val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        createConn.outputStream.use { it.write(createJson.toString().toByteArray(Charsets.UTF_8)) }

        if (createConn.responseCode == 401) throw GoogleDriveException.Unauthorized
        if (createConn.responseCode !in 200..299) {
            val err = createConn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw GoogleDriveException.ApiError(createConn.responseCode, err)
        }

        val created = JSONObject(createConn.inputStream.bufferedReader().use { it.readText() })
        return created.getString("id")
    }

    fun getUserInfo(): GoogleDriveUserInfo? {
        val url = URL("$apiBaseUrl/drive/v3/about?fields=user(displayName,emailAddress)")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${requireToken()}")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        if (connection.responseCode == 401) throw GoogleDriveException.Unauthorized
        if (connection.responseCode !in 200..299) return null
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val userJson = json.optJSONObject("user") ?: return null
        return GoogleDriveUserInfo(
            displayName = userJson.optString("displayName").takeIf(String::isNotBlank),
            emailAddress = userJson.optString("emailAddress").takeIf(String::isNotBlank),
        )
    }

    private fun requireToken(): String {
        return tokenProvider() ?: throw GoogleDriveException.Unauthorized
    }

    private fun parseFileMetadata(json: JSONObject): GoogleDriveFileMetadata {
        val appPropsJson = json.optJSONObject("appProperties")
        val appProps = mutableMapOf<String, String>()
        if (appPropsJson != null) {
            val keys = appPropsJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                appProps[k] = appPropsJson.optString(k)
            }
        }
        return GoogleDriveFileMetadata(
            id = json.getString("id"),
            name = json.optString("name", "untitled"),
            mimeType = json.optString("mimeType", "application/octet-stream"),
            sizeBytes = json.optLong("size", 0L),
            modifiedTime = json.optString("modifiedTime").takeIf(String::isNotBlank),
            appProperties = appProps,
        )
    }
}
