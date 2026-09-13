package com.qbitcore.booklip.parser

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Mirrors the iOS PlainTextParser's encoding fallback chain: try strict
 * decodes in order and keep the first one that succeeds without errors, since
 * a mis-decoded Korean .txt (EUC-KR / CP949) silently "succeeds" as mojibake
 * under a lenient/replacing decoder instead of failing over to the right one.
 */
object PlainTextParser : BookParser {
    private val encodingNames = listOf("UTF-8", "EUC-KR", "MS949", "UTF-16", "windows-1252", "ISO-8859-1")

    override fun parse(file: File): ParsedBook {
        val text = readText(file)
        return ParsedBook(title = file.nameWithoutExtension, author = "Unknown", plainText = text)
    }

    private fun readText(file: File): String {
        val bytes = file.readBytes()
        for (name in encodingNames) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val decoded = decodeStrict(bytes, charset)
            if (!decoded.isNullOrEmpty()) return decoded
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}
