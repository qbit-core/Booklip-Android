package com.qbitcore.booklip.parser

import com.qbitcore.booklip.model.BookFormat
import java.io.File

interface BookParser {
    fun parse(file: File): ParsedBook
}

object ParserFactory {
    /** [assetsDir]: where an EPUB's inline images and embedded fonts are written (null = skip them). */
    fun parse(file: File, format: BookFormat, assetsDir: File? = null): ParsedBook = when (format) {
        BookFormat.TXT -> PlainTextParser.parse(file)
        BookFormat.MARKDOWN -> MarkdownParser.parse(file)
        BookFormat.EPUB -> EpubParser.parse(file, assetsDir)
        BookFormat.PDF -> PdfParser.parse(file)
    }
}
