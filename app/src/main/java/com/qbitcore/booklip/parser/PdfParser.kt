package com.qbitcore.booklip.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
