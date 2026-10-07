package com.qbitcore.booklip.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.LruCache
import android.widget.TextView
import com.qbitcore.booklip.parser.StyleRun
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Everything that decides where lines and pages break. Pages are measured
 * with a [StaticLayout] built from this and shown in a `TextView` configured
 * from the same values ([applyTo]), so what was measured is what is drawn.
 */
data class LayoutSpec(
    val typeface: Typeface,
    val textSizePx: Float,
    val lineSpacingPx: Float,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
) {
    val isValid: Boolean get() = widthPx > 0 && heightPx > 0

    fun paint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.typeface = typeface
        it.textSize = textSizePx
        it.density = density
    }

    /** A rough guess, used only to size the text window a page is measured from. */
    val estimatedCharsPerPage: Int
        get() {
            val perLine = widthPx / (textSizePx * 0.5f)
            val lines = heightPx / (textSizePx * 1.17f + lineSpacingPx)
            return (perLine * lines).toInt().coerceAtLeast(300)
        }

    fun applyTo(view: TextView) {
        view.setPadding(0, 0, 0, 0)
        view.includeFontPadding = false
        view.typeface = typeface
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
        view.setLineSpacing(lineSpacingPx, 1f)
        view.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) view.isFallbackLineSpacing = true
    }
}

/** Draws an EPUB inline image in place of its placeholder character. */
class InlineImageSpan(private val ref: ImageRef, private val drawWidth: Int, private val drawHeight: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            fm.ascent = -drawHeight
            fm.top = -drawHeight
            fm.descent = 0
            fm.bottom = 0
        }
        return drawWidth
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val bitmap = ImageCache.get(ref, drawWidth) ?: return
        canvas.drawBitmap(bitmap, null, RectF(x, (y - drawHeight).toFloat(), x + drawWidth, y.toFloat()), bitmapPaint)
    }

    private companion object {
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    }
}

/** Decoded inline images, downsampled to about the size they are drawn at. */
private object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(ref: ImageRef, targetWidth: Int): Bitmap? {
        val key = ref.file.path
        cache.get(key)?.let { return it }
        var sample = 1
        while (ref.width / (sample * 2) >= targetWidth) sample *= 2
        val bitmap = runCatching {
            BitmapFactory.decodeFile(key, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

object ReaderLayout {

    /**
     * `doc.text[start, end)` with the spans that affect metrics: inline images
     * and markdown styling. Returns a plain String when there are none.
     */
    fun styledText(doc: ReaderDocument, start: Int, end: Int, spec: LayoutSpec): CharSequence {
        val raw = doc.text.substring(start, end)
        val hasImages = doc.images.isNotEmpty() && raw.indexOf('￼') >= 0
        val firstStyle = firstStyleEndingAfter(doc.styles, start)
        val hasStyles = firstStyle < doc.styles.size && doc.styles[firstStyle].start < end
        if (!hasImages && !hasStyles) return raw

        val spannable = SpannableString(raw)
        if (hasImages) {
            var i = doc.images.binarySearchBy(start) { it.offset }.let { if (it < 0) -it - 1 else it }
            while (i < doc.images.size && doc.images[i].offset < end) {
                val ref = doc.images[i++]
                // Image pixels are treated as dp, then fitted to the page.
                val naturalW = ref.width * spec.density
                val naturalH = ref.height * spec.density
                val maxH = if (spec.heightPx > 0) spec.heightPx * 0.92f else Float.MAX_VALUE
                val scale = minOf(1f, spec.widthPx / naturalW, maxH / naturalH)
                val span = InlineImageSpan(ref, (naturalW * scale).toInt().coerceAtLeast(1), (naturalH * scale).toInt().coerceAtLeast(1))
                spannable.setSpan(span, ref.offset - start, ref.offset - start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (hasStyles) {
            var i = firstStyle
            while (i < doc.styles.size && doc.styles[i].start < end) {
                val run = doc.styles[i++]
                val s = maxOf(run.start, start) - start
                val e = minOf(run.end, end) - start
                if (e <= s) continue
                fun set(span: Any) = spannable.setSpan(span, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                when (run.kind) {
                    StyleRun.Kind.BOLD -> set(StyleSpan(Typeface.BOLD))
                    StyleRun.Kind.ITALIC -> set(StyleSpan(Typeface.ITALIC))
                    StyleRun.Kind.CODE -> set(TypefaceSpan("monospace"))
                    StyleRun.Kind.HEADING1 -> { set(RelativeSizeSpan(1.5f)); set(StyleSpan(Typeface.BOLD)) }
                    StyleRun.Kind.HEADING2 -> { set(RelativeSizeSpan(1.3f)); set(StyleSpan(Typeface.BOLD)) }
                    StyleRun.Kind.HEADING3 -> { set(RelativeSizeSpan(1.15f)); set(StyleSpan(Typeface.BOLD)) }
                }
            }
        }
        return spannable
    }

    /** Styles are sorted by start and (nearly) non-overlapping, so a linear probe from a binary search is enough. */
    private fun firstStyleEndingAfter(styles: List<StyleRun>, offset: Int): Int {
        if (styles.isEmpty()) return 0
        var i = styles.binarySearchBy(offset) { it.start }.let { if (it < 0) -it - 1 else it }
        while (i > 0 && styles[i - 1].end > offset) i--
        return i
    }

    fun layout(text: CharSequence, spec: LayoutSpec, paint: TextPaint = spec.paint()): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, spec.widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(spec.lineSpacingPx, 1f)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(true)
        return builder.build()
    }

    /** Bottom of [line]'s ink: the line box without the extra spacing that only separates it from the next line. */
    private fun inkBottom(layout: Layout, line: Int, spec: LayoutSpec): Float =
        layout.getLineBottom(line) - (if (line < layout.lineCount - 1) spec.lineSpacingPx else 0f)

    private fun safeCut(text: String, index: Int): Int =
        if (index in 1 until text.length && Character.isLowSurrogate(text[index])) index - 1 else index

    /** First offset at or after [from] that is not a line break — a page never starts on blank lines. */
    fun skipLineBreaks(doc: ReaderDocument, from: Int): Int {
        var i = from
        while (i < doc.length && doc.text[i] == '\n') i++
        return i
    }

    /**
     * End offset of the page that starts at [start]: the end of the last line
     * that fits completely, so no line is ever shown half cut. A line taller
     * than the page (a very large image) still gets a page to itself.
     */
    fun pageEnd(doc: ReaderDocument, start: Int, spec: LayoutSpec, paint: TextPaint = spec.paint()): Int {
        val length = doc.length
        if (start >= length) return length
        var window = spec.estimatedCharsPerPage * 2
        while (true) {
            val windowEnd = safeCut(doc.text, minOf(length, start + window))
            val layout = layout(styledText(doc, start, windowEnd, spec), spec, paint)
            var last = -1
            for (line in 0 until layout.lineCount) {
                if (inkBottom(layout, line, spec) <= spec.heightPx) last = line else break
            }
            if (last == layout.lineCount - 1) {
                if (windowEnd >= length) return length
                // The whole window fit: the page holds more text than estimated.
                window *= 2
                continue
            }
            return start + layout.getLineEnd(maxOf(last, 0))
        }
    }

    /**
     * Start offset of a page that ends at [end] — the previous page when
     * paging backwards from a position that is not on a known page boundary.
     */
    fun pageStartBefore(doc: ReaderDocument, end: Int, spec: LayoutSpec, paint: TextPaint = spec.paint()): Int {
        var textEnd = minOf(end, doc.length)
        while (textEnd > 0 && doc.text[textEnd - 1] == '\n') textEnd--
        if (textEnd <= 0) return 0
        var window = spec.estimatedCharsPerPage * 2
        while (true) {
            var windowStart = maxOf(0, textEnd - window)
            if (windowStart > 0) {
                // Start the measured window on a paragraph so its line breaks are
                // the ones a forward layout would produce.
                val lineBreak = doc.text.lastIndexOf('\n', windowStart - 1)
                windowStart = if (lineBreak >= 0 && windowStart - lineBreak < MAX_PARAGRAPH_LOOKBACK) lineBreak + 1
                else safeCut(doc.text, windowStart)
            }
            val layout = layout(styledText(doc, windowStart, textEnd, spec), spec, paint)
            val lastLine = layout.lineCount - 1
            val bottom = layout.getLineBottom(lastLine)
            var first = lastLine
            while (first > 0 && bottom - layout.getLineTop(first - 1) <= spec.heightPx) first--
            if (first == 0 && windowStart > 0) {
                window *= 2
                continue
            }
            return skipLineBreaks(doc, windowStart + layout.getLineStart(first)).coerceAtMost(maxOf(0, textEnd - 1))
        }
    }

    /** Y of the line containing [offset] within the block `[blockStart, blockEnd)` laid out with [spec]. */
    fun lineTopInBlock(doc: ReaderDocument, blockStart: Int, blockEnd: Int, offset: Int, spec: LayoutSpec): Int {
        if (offset <= blockStart) return 0
        val layout = layout(styledText(doc, blockStart, blockEnd, spec), spec)
        return layout.getLineTop(layout.getLineForOffset((offset - blockStart).coerceIn(0, blockEnd - blockStart)))
    }

    /** Start offsets of every page of the book. Cancellable; [onProgress] gets 0…1. */
    suspend fun paginate(doc: ReaderDocument, spec: LayoutSpec, onProgress: (Float) -> Unit): IntArray {
        val starts = ArrayList<Int>(doc.length / spec.estimatedCharsPerPage + 16)
        val paint = spec.paint()
        var start = skipLineBreaks(doc, 0)
        if (start >= doc.length) return intArrayOf(0)
        while (start < doc.length) {
            starts += start
            start = skipLineBreaks(doc, pageEnd(doc, start, spec, paint))
            if (starts.size % 32 == 0) {
                coroutineContext.ensureActive()
                onProgress(start.toFloat() / doc.length)
            }
        }
        return starts.toIntArray()
    }

    private const val MAX_PARAGRAPH_LOOKBACK = 6000
}
