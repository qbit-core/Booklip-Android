package com.qbitcore.booklip.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class BookFormat {
    TXT, EPUB, PDF, MARKDOWN;

    val displayName: String
        get() = when (this) {
            TXT -> "Text"
            EPUB -> "ePub"
            PDF -> "PDF"
            MARKDOWN -> "Markdown"
        }

    companion object {
        fun fromFileName(name: String): BookFormat? =
            when (name.substringAfterLast('.', "").lowercase()) {
                "txt" -> TXT
                "epub" -> EPUB
                "pdf" -> PDF
                "md", "markdown" -> MARKDOWN
                else -> null
            }
    }
}

enum class SortOption(val label: String) {
    DATE_ADDED("Date Added"),
    TITLE("Title"),
    AUTHOR("Author"),
    PROGRESS("Progress"),
    FORMAT("Format"),
}

enum class ViewMode(val label: String, val columns: Int) {
    LIST("List", 1),
    SMALL_GRID("Small", 4),
    MEDIUM_GRID("Medium", 3),
    LARGE_GRID("Large", 2),
}

/**
 * [charIndex] is the reader's last scroll position expressed as a paragraph
 * index (see ReaderScreen) — not a character offset. It only needs to be
 * meaningful to this app's own reader, unlike the iOS version's UTF-16 index.
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String = "Unknown",
    val format: BookFormat,
    val fileName: String,
    val progress: Double = 0.0,
    val dateAdded: Long = System.currentTimeMillis(),
    val wordCount: Int = 0,
    val folderId: String? = null,
    val coverFileName: String? = null,
    val progressUpdated: Long? = null,
    val charIndex: Int = 0,
)
