package com.qbitcore.booklip.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Unlike the iOS PDFParser (PDFKit gives it real page text via `PDFPage.string`),
 * Android's built-in `PdfRenderer` is raster-only — there is no text layer
 * extraction API in the platform SDK without pulling in a third-party PDF
 * library. So this parser does not produce readable body text at all: word
 * count is left at 0, and the PDF reader (see PdfReaderScreen) renders actual
 * page images instead of reflowing extracted text, the same as the iOS app's
 * PDFReaderView (which also displays real pages, not the extracted text —
 * that extraction there only feeds TTS/search). What this parser DOES do is
 * read the page count and render page 0 as a cover thumbnail for the library.
 */
object PdfParser : BookParser {
    private const val THUMBNAIL_WIDTH = 480

    override fun parse(file: File): ParsedBook {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) throw PdfParserException("PDF has no pages")
                val cover = renderCover(renderer)
                return ParsedBook(
                    title = file.nameWithoutExtension,
                    author = "Unknown",
                    plainText = "",
                    coverImage = cover,
                )
            }
        }
    }

    /**
     * Text of a few pages near the start, middle and end — enough for
     * [LanguageDetector]. null before Android 15, which has no PDF text layer
     * (the book's language then stays undetected rather than "no text").
     */
    fun sampleText(file: File): String? {
        if (Build.VERSION.SDK_INT < 35) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> sampleTextApi35(renderer) }
            }
        }.getOrNull()
    }

    @RequiresApi(35)
    private fun sampleTextApi35(renderer: PdfRenderer): String {
        val count = renderer.pageCount
        val pages = listOf(0.05, 0.5, 0.8)
            .flatMap { fraction -> (count * fraction).toInt().let { it until it + SAMPLE_PAGES } }
            .filter { it in 0 until count }
            .distinct()
        return pages.joinToString("\n") { index ->
            renderer.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } }
        }
    }

    private const val SAMPLE_PAGES = 3

    private fun renderCover(renderer: PdfRenderer): ByteArray? {
        val page = renderer.openPage(0)
        return try {
            val scale = THUMBNAIL_WIDTH.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.toByteArray()
            }
        } finally {
            page.close()
        }
    }
}

class PdfParserException(message: String) : Exception(message)
