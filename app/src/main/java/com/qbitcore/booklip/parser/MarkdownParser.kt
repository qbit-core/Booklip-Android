package com.qbitcore.booklip.parser

import java.io.File

object MarkdownParser : BookParser {
    override fun parse(file: File): ParsedBook {
        val raw = PlainTextParser.readText(file)
        val firstLine = raw.lineSequence().firstOrNull().orEmpty()
        val title = if (firstLine.startsWith("# ")) firstLine.removePrefix("# ").trim() else file.nameWithoutExtension
        // Keep the raw markdown — the reader renders it via MarkdownRenderer.
        return ParsedBook(title = title, author = "Unknown", plainText = raw)
    }
}

/** A styled range of the rendered markdown text. */
data class StyleRun(val start: Int, val end: Int, val kind: Kind) {
    enum class Kind { BOLD, ITALIC, CODE, HEADING1, HEADING2, HEADING3 }
}

class RenderedMarkdown(val text: String, val styles: List<StyleRun>)

/**
 * Renders the common markdown subset to plain text + style runs: headings,
 * bold / italic / inline code, links (label only), list bullets and block
 * quotes. Anything else is shown as written.
 */
object MarkdownRenderer {
    private val heading = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+")
    private val rule = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val inline = Regex(
        "\\*\\*(.+?)\\*\\*|__(.+?)__|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])|" +
            "(?<![_\\w])_(?!\\s)(.+?)(?<!\\s)_(?![_\\w])|`([^`]+)`|!?\\[([^\\]]*)]\\(([^)]*)\\)"
    )

    fun render(raw: String): RenderedMarkdown {
        val out = StringBuilder(raw.length)
        val styles = ArrayList<StyleRun>()
        var inFence = false
        for (line in raw.split('\n')) {
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (inFence) {
                val start = out.length
                out.append(line)
                if (line.isNotEmpty()) styles += StyleRun(start, out.length, StyleRun.Kind.CODE)
                out.append('\n')
                continue
            }
            if (rule.matches(line)) {
                out.append("⸻\n")
                continue
            }
            val h = heading.find(line)
            if (h != null) {
                val start = out.length
                appendInline(h.groupValues[2], out, styles)
                val kind = when (h.groupValues[1].length) {
                    1 -> StyleRun.Kind.HEADING1
                    2 -> StyleRun.Kind.HEADING2
                    else -> StyleRun.Kind.HEADING3
                }
                if (out.length > start) styles += StyleRun(start, out.length, kind)
                out.append('\n')
                continue
            }
            var body = line
            val b = bullet.find(body)
            if (b != null) {
                out.append(b.groupValues[1]).append("• ")
                body = body.substring(b.range.last + 1)
            } else if (body.startsWith(">")) {
                out.append("▎ ")
                body = body.removePrefix(">").trimStart()
            }
            appendInline(body, out, styles)
            out.append('\n')
        }
        return RenderedMarkdown(out.toString(), styles)
    }

    private fun appendInline(source: String, out: StringBuilder, styles: MutableList<StyleRun>) {
        var cursor = 0
        for (m in inline.findAll(source)) {
            out.append(source, cursor, m.range.first)
            cursor = m.range.last + 1
            val g = m.groupValues
            val start = out.length
            when {
                g[1].isNotEmpty() || g[2].isNotEmpty() -> {
                    appendInline(g[1].ifEmpty { g[2] }, out, styles)
                    styles += StyleRun(start, out.length, StyleRun.Kind.BOLD)
                }
                g[3].isNotEmpty() || g[4].isNotEmpty() -> {
                    appendInline(g[3].ifEmpty { g[4] }, out, styles)
                    styles += StyleRun(start, out.length, StyleRun.Kind.ITALIC)
                }
                g[5].isNotEmpty() -> {
                    out.append(g[5])
                    styles += StyleRun(start, out.length, StyleRun.Kind.CODE)
                }
                else -> out.append(g[6].ifEmpty { g[7] })
            }
        }
        out.append(source, cursor, source.length)
    }
}
