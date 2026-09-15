package com.qbitcore.booklip.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    /** 0..1 position in the book. */
    val progress: Double,
    /** Short text preview at that position. */
    val snippet: String,
    val date: Long = System.currentTimeMillis(),
)
