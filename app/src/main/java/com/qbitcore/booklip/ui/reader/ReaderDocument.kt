package com.qbitcore.booklip.ui.reader

import android.graphics.BitmapFactory
import com.qbitcore.booklip.data.BookContent
import com.qbitcore.booklip.parser.ParsedBook
import com.qbitcore.booklip.parser.StyleRun
import java.io.File

/** An inline image: the [ParsedBook.IMAGE_CHAR] at [offset] stands for [file]. */
class ImageRef(val offset: Int, val file: File, val width: Int, val height: Int)

/**
 * The text the reader renders, plus everything attached to character offsets.
 * All positions in the reader — saved position, chapters, bookmarks,
 * highlights, search matches, the spoken sentence — are UTF-16 offsets into
 * [text].
 */
class ReaderDocument(
    val text: String,
    /** Sorted by offset. */
    val images: List<ImageRef>,
    /** Sorted by start. */
    val styles: List<StyleRun>,
) {
    val length: Int get() = text.length

    /**
     * Start offsets of the blocks the scrolling reader lays out as separate
     * list items. Blocks break only at line breaks, so splitting never changes
     * how the text wraps; block `i` is `text[starts[i], starts[i + 1])`.
     */
    val segmentStarts: IntArray by lazy { buildSegments() }

    fun segmentIndex(offset: Int): Int = indexOfLastAtMost(segmentStarts, offset)
    fun segmentEnd(index: Int): Int = if (index + 1 < segmentStarts.size) segmentStarts[index + 1] else length

    private fun buildSegments(): IntArray {
        val starts = ArrayList<Int>(length / SEGMENT_TARGET + 1)
        var segmentStart = 0
        starts += 0
        var i = 0
        while (i < length) {
            var lineEnd = text.indexOf('\n', i)
            lineEnd = if (lineEnd < 0) length else lineEnd + 1
            // A single line far longer than a screen (text with no line breaks)
            // is cut at whitespace so no list item becomes enormous.
            while (lineEnd - segmentStart > SEGMENT_MAX) {
                var cut = segmentStart + SEGMENT_TARGET
                val space = text.lastIndexOf(' ', cut)
                if (space > maxOf(i, segmentStart + SEGMENT_TARGET / 2)) cut = space + 1
                else if (Character.isLowSurrogate(text[cut])) cut--
                starts += cut
                segmentStart = cut
            }
            if (lineEnd - segmentStart >= SEGMENT_TARGET && lineEnd < length) {
                // Keep a run of blank lines with the paragraph above it.
                var end = lineEnd
                while (end < length && text[end] == '\n') end++
                if (end < length) {
                    starts += end
                    segmentStart = end
                }
                i = end
            } else {
                i = lineEnd
            }
        }
        return starts.toIntArray()
    }

    companion object {
        private const val SEGMENT_TARGET = 1200
        private const val SEGMENT_MAX = 4000

        /** Reads image sizes — call off the main thread. */
        fun from(content: BookContent): ReaderDocument {
            val images = ArrayList<ImageRef>(content.imageFiles.size)
            if (content.imageFiles.isNotEmpty()) {
                var index = 0
                var offset = content.text.indexOf(ParsedBook.IMAGE_CHAR)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                while (offset >= 0 && index < content.imageFiles.size) {
                    val file = content.imageFiles[index++]
                    BitmapFactory.decodeFile(file.path, options)
                    images += ImageRef(offset, file, maxOf(1, options.outWidth), maxOf(1, options.outHeight))
                    offset = content.text.indexOf(ParsedBook.IMAGE_CHAR, offset + 1)
                }
            }
            return ReaderDocument(content.text, images, content.styles.sortedBy { it.start })
        }
    }
}

/** Index of the last element `<= value` in the sorted [array] (0 if none). */
fun indexOfLastAtMost(array: IntArray, value: Int): Int {
    var low = 0
    var high = array.size - 1
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (array[mid] <= value) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

/** Index of the first element `>= value` in the sorted [array] (`array.size` if none). */
fun indexOfFirstAtLeast(array: IntArray, value: Int): Int {
    var low = 0
    var high = array.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (array[mid] < value) low = mid + 1 else high = mid
    }
    return low
}
