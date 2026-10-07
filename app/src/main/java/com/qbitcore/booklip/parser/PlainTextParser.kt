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

    override fun parse(file: File): ParsedBook =
        ParsedBook(title = file.nameWithoutExtension, author = "Unknown", plainText = readText(file))

    fun readText(file: File): String = normalize(decode(file.readBytes()))

    private fun decode(bytes: ByteArray): String {
        // A byte-order mark settles the encoding outright.
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                decodeStrict(bytes, Charsets.UTF_16)?.let { return it }
            }
        }
        for (name in encodingNames) {
            val charset = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            val decoded = decodeStrict(bytes, charset)
            if (!decoded.isNullOrEmpty()) return decoded
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    /** Drops the BOM and CRs so the reader only ever sees "\n" line breaks. */
    private fun normalize(text: String): String {
        val s = if (text.startsWith('﻿')) text.substring(1) else text
        return if (s.indexOf('\r') < 0) s else s.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}
