package com.qbitcore.booklip.data

import android.content.Context
import android.net.Uri
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.model.Highlight
import com.qbitcore.booklip.model.HighlightColor
import com.qbitcore.booklip.parser.ParsedBook
import com.qbitcore.booklip.parser.ParserFactory
import java.io.File
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

    /** Import a file the user picked via SAF (Storage Access Framework). */
    suspend fun importBook(uri: Uri, displayName: String, folderId: String? = null): Book =
        withContext(Dispatchers.IO) {
            val format = formatOrThrow(displayName)
            val dest = fileStore.importFrom(context.contentResolver, uri, displayName)
            finishImport(dest, displayName, format, folderId)
        }

    /**
     * Import a file that is already sitting on local disk (a cloud download,
     * for example) instead of behind a content:// Uri. The file is moved into
     * the app's own storage under a fresh name, same as [importBook].
     */
    suspend fun importLocalFile(file: File, displayName: String, folderId: String? = null): Book =
        withContext(Dispatchers.IO) {
            val format = formatOrThrow(displayName)
            val dest = fileStore.adopt(file, displayName)
            finishImport(dest, displayName, format, folderId)
        }

    private fun formatOrThrow(displayName: String): BookFormat =
        BookFormat.fromFileName(displayName)
            ?: throw IllegalArgumentException("Unsupported file format: .${displayName.substringAfterLast('.', "")}")

    private fun finishImport(dest: File, displayName: String, format: BookFormat, folderId: String?): Book {
        val parsed: ParsedBook = ParserFactory.parse(dest, format)

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
        return book
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
        db.bookmarkDao().deleteForBook(book.id)
        db.highlightDao().deleteForBook(book.id)
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
    fun coverFile(name: String): File = fileStore.coverFile(name)
    fun bookFile(book: Book): File = fileStore.bookFile(book.fileName)

    // MARK: - Bookmarks

    fun bookmarks(bookId: String): Flow<List<Bookmark>> = db.bookmarkDao().observeForBook(bookId)

    suspend fun addBookmark(bookId: String, progress: Double, snippet: String) = withContext(Dispatchers.IO) {
        db.bookmarkDao().upsert(Bookmark(bookId = bookId, progress = progress, snippet = snippet))
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        db.bookmarkDao().delete(bookmark)
    }

    // MARK: - Highlights

    fun highlights(bookId: String): Flow<List<Highlight>> = db.highlightDao().observeForBook(bookId)

    suspend fun addHighlight(bookId: String, paragraphIndex: Int, color: HighlightColor, snippet: String, progress: Double) =
        withContext(Dispatchers.IO) {
            db.highlightDao().upsert(
                Highlight(
                    bookId = bookId,
                    paragraphIndex = paragraphIndex,
                    color = color,
                    snippet = snippet,
                    progress = progress,
                )
            )
        }

    suspend fun deleteHighlight(highlight: Highlight) = withContext(Dispatchers.IO) {
        db.highlightDao().delete(highlight)
    }
}
