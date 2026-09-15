package com.qbitcore.booklip.data.cloud

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Google Drive API v3 (https://developers.google.com/drive/api/reference/rest/v3). */
object GoogleDriveService : CloudService {
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    override suspend fun listFolder(token: OAuthToken, folderId: String?): List<CloudFile> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<CloudFile>()
            val parent = folderId ?: "root"
            val query = URLEncoder.encode("'$parent' in parents and trashed = false", "UTF-8")
            var pageToken: String? = null
            do {
                val url = buildString {
                    append("https://www.googleapis.com/drive/v3/files?q=$query")
                    append("&fields=nextPageToken,files(id,name,mimeType,size)&pageSize=200")
                    if (pageToken != null) append("&pageToken=$pageToken")
                }
                val json = getJson(url, token.accessToken)
                val files = json.getJSONArray("files")
                for (i in 0 until files.length()) {
                    val entry = files.getJSONObject(i)
                    val isFolder = entry.getString("mimeType") == FOLDER_MIME
                    result += CloudFile(
                        id = entry.getString("id"),
                        name = entry.getString("name"),
                        isFolder = isFolder,
                        size = if (isFolder) null else entry.optString("size").toLongOrNull(),
                    )
                }
                pageToken = json.optString("nextPageToken").ifBlank { null }
            } while (pageToken != null)
            result
        }

    override suspend fun download(token: OAuthToken, file: CloudFile, destination: File): Unit = withContext(Dispatchers.IO) {
        val connection = URL("https://www.googleapis.com/drive/v3/files/${file.id}?alt=media")
            .openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer ${token.accessToken}")
            connection.connect()
            check(connection.responseCode in 200..299) { "Google Drive download failed: ${connection.responseCode}" }
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
            check(status in 200..299) { "Google Drive API error $status: $text" }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
