package com.qbitcore.booklip.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.Chapter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Owns everything on disk: the imported book files themselves, an extracted
 * plain-text cache (so the reader never has to re-run EpubParser just to
 * open a book), a chapters cache, and cover images.
 */
class FileStore(context: Context) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val textDir = File(context.filesDir, "text").apply { mkdirs() }
    private val chaptersDir = File(context.filesDir, "chapters").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

    fun bookFile(fileName: String): File = File(booksDir, fileName)
    fun coverFile(fileName: String): File = File(coversDir, fileName)
    private fun plainTextFile(fileName: String): File = File(textDir, "$fileName.txt")
    private fun chaptersFile(fileName: String): File = File(chaptersDir, "$fileName.json")

    fun importFrom(resolver: ContentResolver, uri: Uri, suggestedName: String): File {
        val ext = suggestedName.substringAfterLast('.', "")
        val fileName = if (ext.isEmpty()) UUID.randomUUID().toString() else "${UUID.randomUUID()}.$ext"
        val dest = File(booksDir, fileName)
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        input.use { stream -> dest.outputStream().use { output -> stream.copyTo(output) } }
        return dest
    }

    fun saveCover(bytes: ByteArray, forFileName: String): String {
        val name = "$forFileName.cover"
        File(coversDir, name).writeBytes(bytes)
        return name
    }

    fun savePlainText(fileName: String, text: String) {
        plainTextFile(fileName).writeText(text, Charsets.UTF_8)
    }

    fun readPlainText(book: Book): String {
        val cached = plainTextFile(book.fileName)
        return if (cached.exists()) cached.readText(Charsets.UTF_8) else bookFile(book.fileName).readText(Charsets.UTF_8)
    }

    fun saveChapters(fileName: String, chapters: List<Chapter>) {
        val array = JSONArray()
        for (chapter in chapters) {
            array.put(
                JSONObject()
                    .put("title", chapter.title)
                    .put("progress", chapter.progress)
                    .put("level", chapter.level)
            )
        }
        chaptersFile(fileName).writeText(array.toString())
    }

    fun readChapters(book: Book): List<Chapter> {
        val file = chaptersFile(book.fileName)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Chapter(
                title = obj.getString("title"),
                progress = obj.getDouble("progress"),
                level = obj.optInt("level", 0),
            )
        }
    }

    fun deleteBookFiles(book: Book) {
        bookFile(book.fileName).delete()
        plainTextFile(book.fileName).delete()
        chaptersFile(book.fileName).delete()
        book.coverFileName?.let { coverFile(it).delete() }
    }
}
