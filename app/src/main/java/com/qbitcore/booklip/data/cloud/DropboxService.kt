package com.qbitcore.booklip.data.cloud

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Dropbox API v2 (https://www.dropbox.com/developers/documentation/http/documentation). */
object DropboxService : CloudService {
    override suspend fun listFolder(token: OAuthToken, folderId: String?): List<CloudFile> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<CloudFile>()
            var body = JSONObject().put("path", folderId.orEmpty()).toString()
            var url = "https://api.dropboxapi.com/2/files/list_folder"
            while (true) {
                val json = postJson(url, token.accessToken, body)
                val entries = json.getJSONArray("entries")
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val isFolder = entry.getString(".tag") == "folder"
                    result += CloudFile(
                        id = entry.getString(if (isFolder) "path_lower" else "id"),
                        name = entry.getString("name"),
                        isFolder = isFolder,
                        size = if (isFolder) null else entry.optLong("size"),
                    )
                }
                if (!json.optBoolean("has_more", false)) break
                url = "https://api.dropboxapi.com/2/files/list_folder/continue"
                body = JSONObject().put("cursor", json.getString("cursor")).toString()
            }
            result
        }

    override suspend fun download(token: OAuthToken, file: CloudFile, destination: File): Unit = withContext(Dispatchers.IO) {
        val connection = URL("https://content.dropboxapi.com/2/files/download").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${token.accessToken}")
            connection.setRequestProperty("Dropbox-API-Arg", JSONObject().put("path", file.id).toString())
            connection.connect()
            check(connection.responseCode in 200..299) { "Dropbox download failed: ${connection.responseCode}" }
            connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(url: String, accessToken: String, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Dropbox API error $status: $text" }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
