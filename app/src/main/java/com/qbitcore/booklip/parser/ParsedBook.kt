package com.qbitcore.booklip.parser

import com.qbitcore.booklip.model.Chapter

data class ParsedBook(
    val title: String,
    val author: String,
    val plainText: String,
    val chapters: List<Chapter> = emptyList(),
    val coverImage: ByteArray? = null,
) {
    val wordCount: Int
        get() = plainText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}
