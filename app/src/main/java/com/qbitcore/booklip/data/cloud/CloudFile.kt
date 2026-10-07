package com.qbitcore.booklip.data.cloud

data class CloudFile(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long?,
) {
    val isSupportedBook: Boolean
        get() = !isFolder && name.substringAfterLast('.', "").lowercase() in
            setOf("txt", "epub", "pdf", "md", "markdown")

    /** What the browser's Select mode can pick: books, and folders (imported as a library folder). */
    val isSelectable: Boolean get() = isFolder || isSupportedBook
}

interface CloudService {
    /** Lists the contents of [folderId] (null = root). */
    suspend fun listFolder(token: OAuthToken, folderId: String?): List<CloudFile>

    /** Downloads [file] to [destination], overwriting it. */
    suspend fun download(token: OAuthToken, file: CloudFile, destination: java.io.File)
}
