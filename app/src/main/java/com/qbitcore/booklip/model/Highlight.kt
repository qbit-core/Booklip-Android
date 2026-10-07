package com.qbitcore.booklip.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class HighlightColor(val label: String, val argb: Long) {
    YELLOW("Yellow", 0xFFFFD60A),
    GREEN("Green", 0xFF34C759),
    BLUE("Blue", 0xFF0A84FF),
    PINK("Pink", 0xFFFF375F),
}

/** A highlighted character range ([location], [length] in UTF-16 units of the book's rendered text), as on iOS. */
@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val location: Int,
    val length: Int,
    val color: HighlightColor,
    val snippet: String,
    /** 0..1 position in the book. */
    val progress: Double,
    val date: Long = System.currentTimeMillis(),
) {
    val end: Int get() = location + length
}
