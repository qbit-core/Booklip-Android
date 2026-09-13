package com.qbitcore.booklip.data

import android.content.Context
import android.net.Uri
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.parser.ParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val context: Context,
    private val db: BooklipDatabase,
    private val fileStore: FileStore,
) {
    val books: Flow<List<Book>> = db.bookDao().observeAll()
    val folders: Flow<List<BookFolder>> = db.folderDao().observeAll()

    suspend fun getBook(id: String): Book? = withContext(Dispatchers.IO) { db.bookDao().get(id) }

    suspend fun importBook(uri: Uri, displayName: String, folderId: String? = null): Book =
        withContext(Dispatchers.IO) {
            val format = BookFormat.fromFileName(displayName)
                ?: throw IllegalArgumentException("Unsupported file format: .${displayName.substringAfterLast('.', "")}")
            val dest = fileStore.importFrom(context.contentResolver, uri, displayName)
            val parsed = ParserFactory.parse(dest, format)

            fileStore.savePlainText(dest.name, parsed.plainText)
            fileStore.saveChapters(dest.name, parsed.chapters)
            val coverFileName = parsed.coverImage?.let { fileStore.saveCover(it, dest.name) }

            val originalStem = displayName.substringBeforeLast('.')
            val title = parsed.title.ifBlank { originalStem }

            val book = Book(
                title = title,
                author = parsed.author,
                format = format,
                fileName = dest.name,
                wordCount = parsed.wordCount,
                folderId = folderId,
                coverFileName = coverFileName,
            )
            db.bookDao().upsert(book)
            book
        }

    suspend fun updateProgress(bookId: String, progress: Double, charIndex: Int?) = withContext(Dispatchers.IO) {
        val book = db.bookDao().get(bookId) ?: return@withContext
        db.bookDao().update(
            book.copy(
                progress = progress,
                progressUpdated = System.currentTimeMillis(),
                charIndex = charIndex ?: book.charIndex,
            )
        )
    }

    suspend fun moveBook(book: Book, folderId: String?) = withContext(Dispatchers.IO) {
        db.bookDao().update(book.copy(folderId = folderId))
    }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        fileStore.deleteBookFiles(book)
        db.bookDao().delete(book)
    }

    suspend fun createFolder(name: String) = withContext(Dispatchers.IO) {
        db.folderDao().upsert(BookFolder(name = name))
    }

    suspend fun renameFolder(folder: BookFolder, name: String) = withContext(Dispatchers.IO) {
        db.folderDao().upsert(folder.copy(name = name))
    }

    suspend fun deleteFolder(folder: BookFolder) = withContext(Dispatchers.IO) {
        db.bookDao().inFolder(folder.id).forEach { db.bookDao().update(it.copy(folderId = null)) }
        db.folderDao().delete(folder)
    }

    fun readContent(book: Book): String = fileStore.readPlainText(book)
    fun readChapters(book: Book): List<Chapter> = fileStore.readChapters(book)
    fun coverFile(name: String) = fileStore.coverFile(name)
}
