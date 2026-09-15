package com.qbitcore.booklip.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class HighlightColor(val argb: Long) {
    YELLOW(0xFFFFF176),
    GREEN(0xFFAED581),
    BLUE(0xFF81D4FA),
    PINK(0xFFF48FB1),
}

/**
 * Unlike the iOS app's arbitrary character-range highlight (it renders one
 * continuous NSTextStorage), the Android reader is a list of discrete
 * paragraphs, so a highlight here covers a whole paragraph rather than a
 * substring within one. [paragraphIndex] doubles as the reader's position
 * unit (see [Book.charIndex]'s doc comment).
 */
@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val paragraphIndex: Int,
    val color: HighlightColor,
    val snippet: String,
    /** 0..1 position in the book. */
    val progress: Double,
    val date: Long = System.currentTimeMillis(),
)
