package com.qbitcore.booklip.data.cloud

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Microsoft Graph API (https://learn.microsoft.com/en-us/graph/api/resources/onedrive). */
object OneDriveService : CloudService {
    override suspend fun listFolder(token: OAuthToken, folderId: String?): List<CloudFile> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<CloudFile>()
            var url = if (folderId == null) {
                "https://graph.microsoft.com/v1.0/me/drive/root/children?\$top=200"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children?\$top=200"
            }
            while (true) {
                val json = getJson(url, token.accessToken)
                val values = json.getJSONArray("value")
                for (i in 0 until values.length()) {
                    val entry = values.getJSONObject(i)
                    val isFolder = entry.has("folder")
                    result += CloudFile(
                        id = entry.getString("id"),
                        name = entry.getString("name"),
                        isFolder = isFolder,
                        size = if (isFolder) null else entry.optLong("size"),
                    )
                }
                val next = json.optString("@odata.nextLink").ifBlank { null } ?: break
                url = next
            }
            result
        }

    override suspend fun download(token: OAuthToken, file: CloudFile, destination: File): Unit = withContext(Dispatchers.IO) {
        val connection = URL("https://graph.microsoft.com/v1.0/me/drive/items/${file.id}/content")
            .openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer ${token.accessToken}")
            connection.instanceFollowRedirects = true
            connection.connect()
            check(connection.responseCode in 200..299) { "OneDrive download failed: ${connection.responseCode}" }
            connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "OneDrive API error $status: $text" }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
