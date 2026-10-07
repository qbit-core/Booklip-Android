package com.qbitcore.booklip.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.parser.BookAssets
import com.qbitcore.booklip.parser.MarkdownRenderer
import com.qbitcore.booklip.parser.ParsedBook
import com.qbitcore.booklip.parser.ParserFactory
import com.qbitcore.booklip.parser.StyleRun
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Everything the reader needs to show a text book. */
class BookContent(
    val text: String,
    val chapters: List<Chapter>,
    /** One file per [ParsedBook.IMAGE_CHAR] in [text], in order. */
    val imageFiles: List<File>,
    /** The book's embedded fonts, most relevant first. */
    val fontFiles: List<File>,
    /** Markdown styling; empty for other formats. */
    val styles: List<StyleRun>,
)

/**
 * Owns everything on disk: the imported book files, a parsed cache per book
 * (text, chapters, inline images, embedded fonts) so opening a book never has
 * to re-run the parser, and cover images.
 */
class FileStore(context: Context) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    // "parsed-v3": the cache layout and the parsers' output changed with the
    // character-offset reader; older caches ("text", "chapters") are discarded
    // and rebuilt from the original file the next time a book is opened.
    private val parsedDir = File(context.filesDir, "parsed-v3").apply { mkdirs() }

    init {
        File(context.filesDir, "text").deleteRecursively()
        File(context.filesDir, "chapters").deleteRecursively()
    }

    fun bookFile(fileName: String): File = File(booksDir, fileName)
    fun coverFile(fileName: String): File = File(coversDir, fileName)
    private fun cacheDir(fileName: String): File = File(parsedDir, fileName)

    fun importFrom(resolver: ContentResolver, uri: Uri, suggestedName: String): File {
        val dest = File(booksDir, freshFileName(suggestedName))
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        try {
            input.use { stream -> dest.outputStream().use { output -> stream.copyTo(output) } }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        return dest
    }

    /** Moves an already-local file (e.g. a cloud download) into [booksDir] under a fresh name. */
    fun adopt(source: File, suggestedName: String): File {
        val dest = File(booksDir, freshFileName(suggestedName))
        if (!source.renameTo(dest)) {
            source.copyTo(dest, overwrite = true)
            source.delete()
        }
        return dest
    }

    private fun freshFileName(suggestedName: String): String {
        val ext = suggestedName.substringAfterLast('.', "").lowercase()
        return if (ext.isEmpty()) UUID.randomUUID().toString() else "${UUID.randomUUID()}.$ext"
    }

    fun saveCover(bytes: ByteArray, forFileName: String): String {
        val name = "$forFileName.cover"
        File(coversDir, name).writeBytes(bytes)
        return name
    }

    /** Parses [file] and writes its cache. PDFs have no text cache. */
    fun parseAndCache(file: File, format: BookFormat): ParsedBook {
        val dir = cacheDir(file.name)
        dir.deleteRecursively()
        if (format == BookFormat.PDF) return ParserFactory.parse(file, format)
        dir.mkdirs()
        val parsed = ParserFactory.parse(file, format, assetsDir = dir)
        File(dir, TEXT).writeText(parsed.plainText, Charsets.UTF_8)
        File(dir, CHAPTERS).writeText(chaptersJson(parsed.chapters))
        // Written last: its presence marks the cache as complete.
        File(dir, META).writeText(
            JSONObject().put("images", parsed.imageCount).put("fonts", parsed.fontCount).toString()
        )
        return parsed
    }

    fun loadContent(book: Book): BookContent {
        val dir = cacheDir(book.fileName)
        val meta = File(dir, META)
        if (!meta.exists()) parseAndCache(bookFile(book.fileName), book.format)
        val info = JSONObject(meta.readText())
        var text = File(dir, TEXT).readText(Charsets.UTF_8)
        var styles = emptyList<StyleRun>()
        if (book.format == BookFormat.MARKDOWN) {
            val rendered = MarkdownRenderer.render(text)
            text = rendered.text
            styles = rendered.styles
        }
        return BookContent(
            text = text,
            chapters = readChapters(File(dir, CHAPTERS)),
            imageFiles = (0 until info.optInt("images")).map { File(dir, BookAssets.imageName(it)) },
            fontFiles = (0 until info.optInt("fonts")).map { File(dir, BookAssets.fontName(it)) },
            styles = styles,
        )
    }

    private fun chaptersJson(chapters: List<Chapter>): String {
        val array = JSONArray()
        for (chapter in chapters) {
            array.put(JSONObject().put("title", chapter.title).put("progress", chapter.progress).put("level", chapter.level))
        }
        return array.toString()
    }

    private fun readChapters(file: File): List<Chapter> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Chapter(obj.getString("title"), obj.getDouble("progress"), obj.optInt("level", 0))
        }
    }

    fun deleteBookFiles(book: Book) {
        bookFile(book.fileName).delete()
        cacheDir(book.fileName).deleteRecursively()
        book.coverFileName?.let { coverFile(it).delete() }
    }

    private companion object {
        const val TEXT = "text.txt"
        const val CHAPTERS = "chapters.json"
        const val META = "meta.json"
    }
}
