package com.qbitcore.booklip.parser

import android.util.Xml
import com.qbitcore.booklip.model.Chapter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * A deliberately simplified port of the iOS EPUBParser: unzip, walk the
 * OPF manifest/spine, strip each chapter's HTML to plain text, and record a
 * chapter offset per spine item for the table of contents. Embedded-font
 * de-obfuscation and inline images are out of scope for this first pass —
 * Android renders with system fonts, so a book's own font isn't needed to
 * read it correctly the way it is on iOS (Korean glyph coverage aside).
 */
object EpubParser : BookParser {

    private val TAG_HEAD = Regex("(?is)<head[^>]*>.*?</head>")
    private val TAG_SCRIPT = Regex("(?is)<script[^>]*>.*?</script>")
    private val TAG_STYLE = Regex("(?is)<style[^>]*>.*?</style>")
    private val TAG_BLOCK = Regex("(?i)</?(p|div|br|h[1-6]|li|tr)[^>]*>")
    private val TAG_ANY = Regex("<[^>]+>")
    private val BLANK_LINES = Regex("\n{3,}")
    private val HEADING = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>")
    private val HEX_ENTITY = Regex("&#x([0-9a-fA-F]+);")
    private val DEC_ENTITY = Regex("&#(\\d+);")

    override fun parse(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList().associateBy { it.name }
            fun findEntry(path: String) = entries[path]
                ?: entries.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
            fun readEntryText(path: String): String? =
                findEntry(path)?.let { zip.getInputStream(it).readBytes().toString(Charsets.UTF_8) }

            val containerXml = readEntryText("META-INF/container.xml")
                ?: throw EpubException("Missing META-INF/container.xml")
            val opfPath = extractOpfPath(containerXml)
                ?: throw EpubException("Malformed container.xml")
            val opfXml = readEntryText(opfPath) ?: throw EpubException("Missing OPF at $opfPath")
            val opfBase = opfPath.substringBeforeLast('/', "")
            val opf = parseOpf(opfXml)

            val spineHrefs = opf.spine.mapNotNull { opf.manifest[it]?.href }

            val parts = StringBuilder()
            val marks = mutableListOf<Pair<String, Int>>()
            var offset = 0
            for ((index, href) in spineHrefs.withIndex()) {
                val path = resolvePath(href, opfBase)
                val html = readEntryText(path) ?: continue
                val stripped = stripHtml(html)
                val title = firstHeading(html) ?: fileNameTitle(href) ?: "Chapter ${index + 1}"
                marks += title to offset
                if (stripped.isNotEmpty()) {
                    parts.append(stripped).append("\n\n")
                    offset += stripped.length + 2
                }
            }
            val fullText = parts.toString()
            val totalLen = maxOf(1, offset)
            val chapters = marks.map { (title, off) ->
                Chapter(title = title, progress = off.toDouble() / totalLen)
            }

            val cover = opf.coverHref?.let { href ->
                findEntry(resolvePath(href, opfBase))?.let { zip.getInputStream(it).readBytes() }
            }

            return ParsedBook(
                title = opf.title.ifBlank { file.nameWithoutExtension },
                author = opf.author.ifBlank { "Unknown" },
                plainText = fullText,
                chapters = chapters,
                coverImage = cover,
            )
        }
    }

    private fun extractOpfPath(containerXml: String): String? =
        Regex("full-path=\"([^\"]+)\"").find(containerXml)?.groupValues?.get(1)

    // Resolve an href (possibly with ../) relative to a directory inside the zip.
    private fun resolvePath(href: String, base: String): String {
        var s = href.substringBefore('#')
        s = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
        if (s.startsWith("/")) return s.removePrefix("/")

        val components = if (base.isEmpty()) mutableListOf() else base.split("/").toMutableList()
        for (part in s.split("/")) {
            when (part) {
                "", "." -> continue
                ".." -> if (components.isNotEmpty()) components.removeAt(components.size - 1)
                else -> components.add(part)
            }
        }
        return components.joinToString("/")
    }

    private fun firstHeading(html: String): String? {
        val match = HEADING.find(html) ?: return null
        val text = stripHtml(match.groupValues[1]).trim()
        return text.ifBlank { null }?.take(80)
    }

    private fun fileNameTitle(href: String): String? =
        href.substringAfterLast('/').substringBeforeLast('.').ifBlank { null }

    private fun stripHtml(html: String): String {
        var s = html
        s = TAG_HEAD.replace(s, "")
        s = TAG_SCRIPT.replace(s, "")
        s = TAG_STYLE.replace(s, "")
        s = TAG_BLOCK.replace(s, "\n")
        s = TAG_ANY.replace(s, "")
        s = decodeEntities(s)
        s = BLANK_LINES.replace(s, "\n\n")
        return s.trim()
    }

    private fun decodeEntities(input: String): String {
        var s = input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
        s = HEX_ENTITY.replace(s) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: ""
        }
        s = DEC_ENTITY.replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: ""
        }
        return s
    }

    // MARK: - OPF parsing

    private data class ManifestItem(val href: String, val mediaType: String)
    private data class OpfInfo(
        val title: String,
        val author: String,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>,
        val coverHref: String?,
    )

    private fun parseOpf(xml: String): OpfInfo {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())

        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()
        var coverImageHref: String? = null
        var metaCoverId: String? = null
        val imageHrefs = mutableListOf<String>()
        var capturing: String? = null
        val buffer = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val local = parser.name.substringAfter(':').lowercase()
                    when (local) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                            if (id != null && href != null) {
                                manifest[id] = ManifestItem(href, mediaType)
                                if (mediaType.startsWith("image/")) imageHrefs += href
                                val props = parser.getAttributeValue(null, "properties") ?: ""
                                if (props.contains("cover-image")) coverImageHref = href
                            }
                        }
                        "itemref" -> parser.getAttributeValue(null, "idref")?.let { spine += it }
                        "meta" -> {
                            if (parser.getAttributeValue(null, "name")?.lowercase() == "cover") {
                                metaCoverId = parser.getAttributeValue(null, "content")
                            }
                        }
                        "title", "creator" -> {
                            capturing = local
                            buffer.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> if (capturing != null) buffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val local = parser.name.substringAfter(':').lowercase()
                    if (local == capturing) {
                        val value = buffer.toString().trim()
                        if (local == "title" && title.isEmpty()) title = value
                        if (local == "creator" && author.isEmpty()) author = value
                        capturing = null
                    }
                }
            }
            event = parser.next()
        }

        val coverHref = coverImageHref
            ?: metaCoverId?.let { manifest[it]?.href }
            ?: imageHrefs.firstOrNull { it.contains("cover", ignoreCase = true) }
            ?: imageHrefs.firstOrNull()

        return OpfInfo(title, author, manifest, spine, coverHref)
    }
}

class EpubException(message: String) : Exception(message)
