package com.qbitcore.booklip.parser

import com.qbitcore.booklip.model.BookFormat
import java.io.File

interface BookParser {
    fun parse(file: File): ParsedBook
}

object ParserFactory {
    fun parse(file: File, format: BookFormat): ParsedBook = when (format) {
        BookFormat.TXT -> PlainTextParser.parse(file)
        BookFormat.MARKDOWN -> MarkdownParser.parse(file)
        BookFormat.EPUB -> EpubParser.parse(file)
        BookFormat.PDF -> PdfParser.parse(file)
    }
}
