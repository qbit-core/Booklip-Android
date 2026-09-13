package com.qbitcore.booklip.parser

import java.io.File

object MarkdownParser : BookParser {
    override fun parse(file: File): ParsedBook {
        val raw = file.readText(Charsets.UTF_8)
        val firstLine = raw.lineSequence().firstOrNull().orEmpty()
        val title = if (firstLine.startsWith("# ")) {
            firstLine.removePrefix("# ").trim()
        } else {
            file.nameWithoutExtension
        }
        // Keep raw markdown — the reader renders it as plain text for now.
        return ParsedBook(title = title, author = "Unknown", plainText = raw)
    }
}
