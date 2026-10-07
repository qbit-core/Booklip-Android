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
        val supportedExtensions = setOf("txt", "epub", "pdf", "md", "markdown")

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

/** [minCellWidthDp] null = single-column list; otherwise the adaptive grid's minimum cell width (same as iOS). */
enum class ViewMode(val label: String, val minCellWidthDp: Int?) {
    LIST("List", null),
    SMALL_GRID("Small", 100),
    MEDIUM_GRID("Medium", 150),
    LARGE_GRID("Large", 210),
}

/**
 * [charIndex] is the last reading position: for text formats a UTF-16 offset
 * into the book's rendered text (same index space as the iOS app), for PDF a
 * page index. [UNKNOWN_POSITION] means "not known" — the reader then falls
 * back to [progress].
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
) {
    companion object {
        const val UNKNOWN_POSITION = -1
    }
}
