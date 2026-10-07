package com.qbitcore.booklip.data

import android.content.Context
import android.net.Uri
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Highlight
import com.qbitcore.booklip.model.HighlightColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UnsupportedFormatException(extension: String) :
    Exception("Unsupported file format: .$extension. Supported formats: .txt, .epub, .pdf, .md")

class BookRepository(
    private val context: Context,
    private val db: BooklipDatabase,
    private val fileStore: FileStore,
) {
    val books: Flow<List<Book>> = db.bookDao().observeAll()
    val folders: Flow<List<BookFolder>> = db.folderDao().observeAll()

    private val folderMutex = Mutex()

    suspend fun getBook(id: String): Book? = db.bookDao().get(id)

    /** Import a file the user picked via SAF (Storage Access Framework). */
    suspend fun importBook(uri: Uri, displayName: String, folderId: String? = null): Book =
        withContext(Dispatchers.IO) {
            val format = formatOrThrow(displayName)
            val dest = fileStore.importFrom(context.contentResolver, uri, displayName)
            finishImport(dest, displayName, format, folderId)
        }

    /**
     * Import a file that is already on local disk (a cloud download) instead
     * of behind a content:// Uri. The file is moved into the app's storage.
     */
    suspend fun importLocalFile(file: File, displayName: String, folderId: String? = null): Book =
        withContext(Dispatchers.IO) {
            val format = formatOrThrow(displayName)
            val dest = fileStore.adopt(file, displayName)
            finishImport(dest, displayName, format, folderId)
        }

    private fun formatOrThrow(displayName: String): BookFormat =
        BookFormat.fromFileName(displayName)
            ?: throw UnsupportedFormatException(displayName.substringAfterLast('.', ""))

    private suspend fun finishImport(dest: File, displayName: String, format: BookFormat, folderId: String?): Book {
        val parsed = try {
            fileStore.parseAndCache(dest, format)
        } catch (e: Throwable) {
            // Don't leave an unreadable file behind with no library entry.
            dest.delete()
            throw e
        }
        val coverFileName = parsed.coverImage?.let { fileStore.saveCover(it, dest.name) }
        // Parsers without real metadata fall back to the file's name — which
        // is our UUID storage name, not what the user called the file.
        val originalStem = displayName.substringBeforeLast('.')
        val title = parsed.title.takeUnless { it.isBlank() || it == dest.nameWithoutExtension } ?: originalStem
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

    /** [charIndex] null = "not known" → the stored index is kept. A 0 is a real position and is stored. */
    suspend fun updateProgress(bookId: String, progress: Double, charIndex: Int?) {
        val now = System.currentTimeMillis()
        val p = progress.coerceIn(0.0, 1.0)
        if (charIndex != null) db.bookDao().setProgress(bookId, p, charIndex, now)
        else db.bookDao().setProgress(bookId, p, now)
    }

    suspend fun moveBooks(ids: Collection<String>, folderId: String?) {
        ids.chunked(500).forEach { db.bookDao().setFolder(it, folderId) }
    }

    suspend fun deleteBooks(ids: Collection<String>) = withContext(Dispatchers.IO) {
        for (chunk in ids.chunked(500)) {
            for (book in db.bookDao().get(chunk)) {
                fileStore.deleteBookFiles(book)
                db.bookmarkDao().deleteForBook(book.id)
                db.highlightDao().deleteForBook(book.id)
                db.bookDao().delete(book)
            }
        }
    }

    // MARK: - Folders

    suspend fun createFolder(name: String) {
        db.folderDao().upsert(BookFolder(name = name))
    }

    suspend fun renameFolder(folder: BookFolder, name: String) {
        db.folderDao().upsert(folder.copy(name = name))
    }

    /** Books in the folder go back to unfiled. */
    suspend fun deleteFolder(folder: BookFolder) {
        db.bookDao().clearFolder(folder.id)
        db.folderDao().delete(folder)
    }

    /**
     * The folder called [name] (case-insensitive), created on first use — a
     * cloud folder imported twice lands in the same library folder.
     */
    suspend fun folderNamed(name: String): BookFolder = folderMutex.withLock {
        val trimmed = name.trim().ifEmpty { "Untitled" }
        db.folderDao().all().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: BookFolder(name = trimmed).also { db.folderDao().upsert(it) }
    }

    // MARK: - Content

    suspend fun loadContent(book: Book): BookContent = withContext(Dispatchers.IO) { fileStore.loadContent(book) }
    fun coverFile(name: String): File = fileStore.coverFile(name)
    fun bookFile(book: Book): File = fileStore.bookFile(book.fileName)

    // MARK: - Bookmarks

    fun bookmarks(bookId: String): Flow<List<Bookmark>> = db.bookmarkDao().observeForBook(bookId)

    suspend fun addBookmark(bookId: String, progress: Double, snippet: String) {
        db.bookmarkDao().upsert(Bookmark(bookId = bookId, progress = progress, snippet = snippet))
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = db.bookmarkDao().delete(bookmark)

    // MARK: - Highlights

    fun highlights(bookId: String): Flow<List<Highlight>> = db.highlightDao().observeForBook(bookId)

    suspend fun addHighlight(bookId: String, location: Int, length: Int, color: HighlightColor, snippet: String, progress: Double) {
        db.highlightDao().upsert(
            Highlight(bookId = bookId, location = location, length = length, color = color, snippet = snippet, progress = progress)
        )
    }

    suspend fun deleteHighlight(highlight: Highlight) = db.highlightDao().delete(highlight)
}
