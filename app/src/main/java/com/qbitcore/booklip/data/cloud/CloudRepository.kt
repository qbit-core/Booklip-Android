package com.qbitcore.booklip.data.cloud

import android.content.Context
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.model.Book
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CloudRepository(
    private val context: Context,
    private val tokenStore: CloudTokenStore,
    private val bookRepository: BookRepository,
) {
    fun tokenFlow(provider: CloudProvider): Flow<OAuthToken?> = tokenStore.observe(provider)

    /** [activityContext]: the sign-in page opens on top of this activity. */
    suspend fun connect(provider: CloudProvider, activityContext: Context) {
        val token = OAuthClient.authorize(activityContext, provider)
        tokenStore.save(provider, token)
    }

    suspend fun disconnect(provider: CloudProvider) = tokenStore.clear(provider)

    /** Folders first, then by name. */
    suspend fun listFolder(provider: CloudProvider, folderId: String?): List<CloudFile> {
        val token = validToken(provider)
        return serviceFor(provider).listFolder(token, folderId)
            .sortedWith(compareByDescending<CloudFile> { it.isFolder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Every supported book inside [folder], subfolders included (depth-capped so a pathological tree can't run forever). */
    suspend fun collectBooks(provider: CloudProvider, folder: CloudFile, depth: Int = 0): List<CloudFile> {
        if (depth >= 8) return emptyList()
        val result = ArrayList<CloudFile>()
        for (entry in listFolder(provider, folder.id)) {
            if (entry.isFolder) result += collectBooks(provider, entry, depth + 1)
            else if (entry.isSupportedBook) result += entry
        }
        return result
    }

    /**
     * Downloads [file] and imports it. [libraryFolderName] non-null files the
     * book into the library folder of that name (created on first use).
     */
    suspend fun downloadAndImport(provider: CloudProvider, file: CloudFile, libraryFolderName: String?): Book {
        val token = validToken(provider)
        val tempFile = File.createTempFile("cloud_", ".download", context.cacheDir)
        try {
            serviceFor(provider).download(token, file, tempFile)
            val folderId = libraryFolderName?.let { bookRepository.folderNamed(it).id }
            return bookRepository.importLocalFile(tempFile, file.name, folderId)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun validToken(provider: CloudProvider): OAuthToken {
        val current = tokenStore.observe(provider).first()
            ?: throw OAuthException("Not connected to ${provider.label}.")
        if (!current.isExpired) return current
        val refreshed = try {
            OAuthClient.refresh(provider, current)
        } catch (e: OAuthException) {
            // The session cannot be renewed (revoked, or no refresh token):
            // sign out so the UI offers Connect again instead of failing forever.
            tokenStore.clear(provider)
            throw OAuthException("Your ${provider.label} session has expired. Please connect again.")
        }
        // Providers may omit the refresh token on a refresh; keep the one we have.
        val merged = refreshed.copy(refreshToken = refreshed.refreshToken ?: current.refreshToken)
        tokenStore.save(provider, merged)
        return merged
    }

    private fun serviceFor(provider: CloudProvider): CloudService = when (provider) {
        CloudProvider.DROPBOX -> DropboxService
        CloudProvider.GOOGLE_DRIVE -> GoogleDriveService
        CloudProvider.ONE_DRIVE -> OneDriveService
    }
}
