package com.qbitcore.booklip.parser

import com.qbitcore.booklip.model.Chapter

/**
 * [plainText] is the exact text the reader renders. Each EPUB image occupies
 * [IMAGE_PLACEHOLDER] in it (object-replacement char + paragraph break, as on
 * iOS), in the same order as the image files in the book's assets directory.
 */
class ParsedBook(
    val title: String,
    val author: String,
    val plainText: String,
    val chapters: List<Chapter> = emptyList(),
    val coverImage: ByteArray? = null,
    /** Number of image files the parser wrote to its assets directory (see [BookAssets]). */
    val imageCount: Int = 0,
    /** Number of embedded font files written there (de-obfuscated, most relevant first). */
    val fontCount: Int = 0,
) {
    val wordCount: Int
        get() {
            var count = 0
            var inWord = false
            for (c in plainText) {
                if (c.isWhitespace()) inWord = false else if (!inWord) { inWord = true; count++ }
            }
            return count
        }

    companion object {
        const val IMAGE_CHAR = '￼'
        const val IMAGE_PLACEHOLDER = "￼\n\n"
    }
}

/** Names of the per-book files a parser extracts next to the text cache. */
object BookAssets {
    fun imageName(index: Int) = "img_$index"
    fun fontName(index: Int) = "font_$index"
}
