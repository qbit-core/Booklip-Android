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

    suspend fun connect(provider: CloudProvider) {
        val token = OAuthClient.authorize(context, provider)
        tokenStore.save(provider, token)
    }

    suspend fun disconnect(provider: CloudProvider) = tokenStore.clear(provider)

    suspend fun listFolder(provider: CloudProvider, folderId: String?): List<CloudFile> {
        val token = validToken(provider)
        return serviceFor(provider).listFolder(token, folderId)
    }

    /** Downloads [file] to a temp location and hands it to [BookRepository.importLocalFile]. */
    suspend fun downloadAndImport(provider: CloudProvider, file: CloudFile, folderId: String?): Book {
        val token = validToken(provider)
        val tempFile = File.createTempFile("cloud_", "_${file.name}", context.cacheDir)
        try {
            serviceFor(provider).download(token, file, tempFile)
            return bookRepository.importLocalFile(tempFile, file.name, folderId)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun validToken(provider: CloudProvider): OAuthToken {
        val current = tokenStore.observe(provider).first()
            ?: throw OAuthException("Not connected to ${provider.label}.")
        if (!current.isExpired) return current
        val refreshed = OAuthClient.refresh(provider, current)
        tokenStore.save(provider, refreshed)
        return refreshed
    }

    private fun serviceFor(provider: CloudProvider): CloudService = when (provider) {
        CloudProvider.DROPBOX -> DropboxService
        CloudProvider.GOOGLE_DRIVE -> GoogleDriveService
        CloudProvider.ONE_DRIVE -> OneDriveService
    }
}
