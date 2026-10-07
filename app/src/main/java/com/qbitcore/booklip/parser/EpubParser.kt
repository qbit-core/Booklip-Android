package com.qbitcore.booklip.parser

import android.graphics.BitmapFactory
import android.util.Xml
import com.qbitcore.booklip.model.Chapter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Port of the iOS EPUBParser. The text it produces is index-for-index what the
 * reader renders: `stripped + "\n\n"` per text block and
 * [ParsedBook.IMAGE_PLACEHOLDER] per inline image, so chapter offsets, saved
 * positions, highlights and TTS ranges all share one index space.
 */
object EpubParser : BookParser {

    private val RE_SCRIPT = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val RE_STYLE = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    // <head> (and a stray <title> outside it) carry no body text; without this the
    // document title would be prepended to every chapter's text.
    private val RE_HEAD = Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE)
    private val RE_TITLE = Regex("<title[^>]*>[\\s\\S]*?</title>", RegexOption.IGNORE_CASE)
    private val RE_COMMENT = Regex("<!--[\\s\\S]*?-->")
    private val RE_BLOCK = Regex("</?(p|div|br|h[1-6]|li|tr)(\\s[^>]*)?/?>", RegexOption.IGNORE_CASE)
    private val RE_TAGS = Regex("<[^>]+>")
    // HTML whitespace collapsing: runs of source whitespace (including the line
    // wrapping inside <p>) are a single space. Real line breaks come only from
    // block elements / <br> via RE_BLOCK.
    private val RE_SOURCE_WS = Regex("[ \\t\\r\\n]+")
    private val RE_LINE_EDGE_WS = Regex("[ \\t]*\\n[ \\t]*")
    private val RE_BLANK_LINES = Regex("\\n{3,}")
    private val RE_NUM_ENTITY = Regex("&#(x[0-9a-fA-F]+|\\d+);", RegexOption.IGNORE_CASE)
    private val RE_NAMED_ENTITY = Regex("&([a-zA-Z]{2,8});")
    private val RE_IMG_TAG = Regex(
        "<(?:img|image)\\b[^>]*?(?:src|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val RE_HEADING = Regex("<h[1-3][^>]*>([\\s\\S]*?)</h[1-3]>", RegexOption.IGNORE_CASE)
    private val RE_NAV_TOKEN = Regex(
        "<ol\\b|</ol>|<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>",
        RegexOption.IGNORE_CASE,
    )
    private val RE_NAV_TOC = Regex(
        "<nav\\b[^>]*epub:type\\s*=\\s*[\"'][^\"']*\\btoc\\b[^\"']*[\"'][^>]*>([\\s\\S]*?)</nav>",
        RegexOption.IGNORE_CASE,
    )
    private val RE_FONT_FACE = Regex("@font-face\\s*\\{([^}]*)\\}", RegexOption.IGNORE_CASE)
    private val RE_CSS_URL = Regex("url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)", RegexOption.IGNORE_CASE)
    private val RE_ENCRYPTION = Regex("Algorithm\\s*=\\s*[\"']([^\"']+)[\"'][\\s\\S]*?URI\\s*=\\s*[\"']([^\"']+)[\"']")

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to "\u00A0", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "copy" to "©",
        "reg" to "®", "trade" to "™", "middot" to "·", "bull" to "•", "deg" to "°",
        "times" to "×", "shy" to "", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    )

    // Elements a TOC fragment may split a chapter on. Splitting inserts a block
    // boundary ("\n\n") at the cut, which is what these tags produce anyway; an
    // inline anchor (<a id>, <span id>) mid-paragraph must not be cut.
    private val SPLITTABLE_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "section", "article", "aside",
        "li", "table", "tr", "blockquote", "figure", "header", "hgroup", "nav",
        "ol", "ul", "dl", "dt", "dd", "pre", "hr", "body",
    )

    private class TocEntry(val title: String, val href: String, val level: Int)

    private sealed interface Segment {
        var anchorId: String?
        class Text(val html: String, override var anchorId: String? = null) : Segment {
            var stripped: String = ""
        }
        class Image(val src: String, override var anchorId: String? = null) : Segment
    }

    private class ChapterRaw(
        val hrefKey: String,
        val chapterDir: String,
        val titleHint: String?,
        val segments: List<Segment>,
    )

    private class Archive(val zip: ZipFile) {
        private val index: Map<String, ZipEntry> = HashMap<String, ZipEntry>().apply {
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                putIfAbsent(e.name, e)
            }
        }
        private val lowerIndex: Map<String, String> by lazy { index.keys.associateBy { it.lowercase() } }

        val paths: Set<String> get() = index.keys

        /** The archive's own spelling of [path] (case-insensitive fallback), or null. */
        fun resolve(path: String): String? = if (index.containsKey(path)) path else lowerIndex[path.lowercase()]

        fun bytes(path: String): ByteArray? {
            val entry = index[resolve(path) ?: return null] ?: return null
            return runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull()
        }

        fun text(path: String): String? = bytes(path)?.let { String(it, Charsets.UTF_8).removePrefix("\uFEFF") }
    }

    override fun parse(file: File): ParsedBook = parse(file, null)

    fun parse(file: File, assetsDir: File?): ParsedBook {
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw EpubException("Cannot open EPUB archive.")
        }
        zip.use { return parse(Archive(it), file, assetsDir) }
    }

    private fun parse(archive: Archive, file: File, assetsDir: File?): ParsedBook {
        val containerXml = archive.text("META-INF/container.xml") ?: throw EpubException("Missing entry: META-INF/container.xml")
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(containerXml)?.groupValues?.get(1)
            ?: throw EpubException("Malformed container.xml")
        val opfXml = archive.text(opfPath) ?: throw EpubException("Missing entry: $opfPath")
        val opfBase = opfPath.substringBeforeLast('/', "")
        val opf = parseOpf(opfXml)

        assetsDir?.mkdirs()
        val fontCount = if (assetsDir != null) extractFonts(opf, opfBase, archive, assetsDir) else 0
        val cover = opf.coverHref?.let { archive.bytes(resolvePath(it, opfBase)) }?.takeIf { it.isNotEmpty() }

        // The TOC is parsed up front so that entries pointing INTO a file
        // ("chapter.xhtml#ch3") can be split out as their own segments — every
        // chapter of a single-file EPUB otherwise mapped to offset 0.
        val tocEntries = parseToc(opf, opfBase, archive)
        val anchorsByFile = HashMap<String, MutableList<String>>()
        for (entry in tocEntries) {
            val fragment = entry.href.substringAfter('#', "")
            if (fragment.isEmpty()) continue
            anchorsByFile.getOrPut(fileKey(entry.href)) { ArrayList() }.add(decode(fragment))
        }

        // Phase 1 (serial): pull every chapter's HTML off the zip and cut it into
        // text / image segments.
        val chapterRaws = ArrayList<ChapterRaw>(opf.spineHrefs.size)
        for (href in opf.spineHrefs) {
            // resolvePath percent-decodes and collapses "../" — raw concatenation
            // silently drops chapters whose manifest href is "Text/Ch%201.xhtml".
            val entryPath = resolvePath(href, opfBase)
            val html = archive.text(entryPath) ?: continue
            val decodedHref = decode(href.substringBefore('#'))
            val titleHint = firstHeading(html)
                ?: decodedHref.substringAfterLast('/').replace(".xhtml", "").replace(".html", "")
            val key = decodedHref.substringAfterLast('/')
            val segments = ArrayList<Segment>()
            for ((anchorId, pieceHtml) in splitHtml(html, anchorsByFile[key].orEmpty())) {
                var first = true
                for (segment in segmentsOf(pieceHtml)) {
                    if (first) {
                        segment.anchorId = anchorId
                        first = false
                    }
                    segments += segment
                }
            }
            chapterRaws += ChapterRaw(key, entryPath.substringBeforeLast('/', ""), titleHint.ifEmpty { null }, segments)
        }

        // Phase 2 (parallel): stripHtml is a pure function, and by far the most
        // expensive step on a book with hundreds of chapters.
        chapterRaws.flatMap { it.segments }.filterIsInstance<Segment.Text>()
            .parallelStream().forEach { it.stripped = stripHtml(it.html) }

        // Phase 3 (serial): assemble in spine order so offsets are correct.
        val text = StringBuilder()
        val chapterMarks = ArrayList<Pair<String, Int>>()
        val hrefToOffset = HashMap<String, Int>()
        val anchorToOffset = HashMap<String, Int>()
        var imageCount = 0
        for ((i, chapter) in chapterRaws.withIndex()) {
            hrefToOffset.putIfAbsent(chapter.hrefKey, text.length)
            chapterMarks += (chapter.titleHint ?: "Chapter ${i + 1}") to text.length
            for (segment in chapter.segments) {
                segment.anchorId?.let { anchorToOffset.putIfAbsent(chapter.hrefKey + "#" + it, text.length) }
                when (segment) {
                    is Segment.Text -> if (segment.stripped.isNotEmpty()) {
                        text.append(segment.stripped).append("\n\n")
                    }
                    is Segment.Image -> {
                        if (assetsDir == null) continue
                        val data = archive.bytes(resolvePath(segment.src, chapter.chapterDir)) ?: continue
                        if (!isDecodableImage(data)) continue
                        File(assetsDir, BookAssets.imageName(imageCount)).writeBytes(data)
                        imageCount++
                        text.append(ParsedBook.IMAGE_PLACEHOLDER)
                    }
                }
            }
        }
        val totalLength = maxOf(1, text.length)

        // Prefer a real TOC (NCX/nav) for proper titles + nesting; fall back to
        // per-spine headings.
        var chapters = tocEntries.mapNotNull { entry ->
            val key = fileKey(entry.href)
            val fragment = entry.href.substringAfter('#', "")
            val offset = (if (fragment.isNotEmpty()) anchorToOffset[key + "#" + decode(fragment)] else null)
                ?: hrefToOffset[key]
                ?: return@mapNotNull null
            Chapter(title = entry.title, progress = offset.toDouble() / totalLength, level = entry.level)
        }
        if (chapters.isEmpty()) {
            chapters = chapterMarks.map { (title, offset) -> Chapter(title, offset.toDouble() / totalLength) }
        }

        return ParsedBook(
            title = opf.title.ifBlank { file.nameWithoutExtension },
            author = opf.author.ifBlank { "Unknown" },
            plainText = text.toString(),
            chapters = chapters,
            coverImage = cover,
            imageCount = imageCount,
            fontCount = fontCount,
        )
    }

    private fun isDecodableImage(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /** Last path component of an href, fragment dropped, percent-decoded — how files are keyed. */
    private fun fileKey(href: String): String = decode(href.substringBefore('#')).substringAfterLast('/')

    // MARK: - Table of contents

    private fun parseToc(opf: OpfInfo, base: String, archive: Archive): List<TocEntry> {
        opf.ncxHref?.let { archive.text(resolvePath(it, base)) }?.let { xml ->
            val entries = parseNcx(xml)
            if (entries.isNotEmpty()) return entries
        }
        opf.navHref?.let { archive.text(resolvePath(it, base)) }?.let { html ->
            return parseNavHtml(html)
        }
        return emptyList()
    }

    /** Parses an NCX navMap into ordered, level-tagged TOC entries. */
    private fun parseNcx(xml: String): List<TocEntry> {
        val entries = ArrayList<TocEntry>()
        var depth = 0
        var inNavLabel = false
        var capturing = false
        var pendingTitle: String? = null
        val buffer = StringBuilder()
        runCatching {
            val parser = newParser(xml)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (localName(parser)) {
                        "navpoint" -> {
                            depth++
                            pendingTitle = null
                        }
                        "navlabel" -> inNavLabel = true
                        // Only a navLabel's <text> is a chapter title — the NCX's
                        // mandatory <docTitle><text> must not become a TOC entry.
                        "text" -> if (inNavLabel && depth > 0) {
                            capturing = true
                            buffer.clear()
                        }
                        "content" -> {
                            val src = parser.getAttributeValue(null, "src")
                            val title = pendingTitle
                            if (src != null && title != null) {
                                entries += TocEntry(title, src, maxOf(0, depth - 1))
                                pendingTitle = null
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (capturing) buffer.append(parser.text)
                    XmlPullParser.END_TAG -> when (localName(parser)) {
                        "text" -> if (capturing) {
                            capturing = false
                            val t = buffer.toString().replace(RE_SOURCE_WS, " ").trim()
                            if (pendingTitle == null && t.isNotEmpty()) pendingTitle = t
                        }
                        "navlabel" -> inNavLabel = false
                        "navpoint" -> depth = maxOf(0, depth - 1)
                    }
                }
                event = parser.next()
            }
        }
        return entries
    }

    /** `<a href>label</a>` entries of the EPUB3 toc nav, with `<ol>` nesting depth as the level. */
    private fun parseNavHtml(html: String): List<TocEntry> {
        val scope = RE_NAV_TOC.find(html)?.groupValues?.get(1) ?: html
        val entries = ArrayList<TocEntry>()
        var level = 0
        for (m in RE_NAV_TOKEN.findAll(scope)) {
            val token = m.value.lowercase()
            when {
                token.startsWith("<ol") -> level++
                token.startsWith("</ol") -> level = maxOf(0, level - 1)
                else -> {
                    val label = stripHtml(m.groupValues[2]).replace('\n', ' ').trim()
                    if (label.isNotEmpty()) entries += TocEntry(label, m.groupValues[1], maxOf(0, level - 1))
                }
            }
        }
        return entries
    }

    private fun firstHeading(html: String): String? {
        val match = RE_HEADING.find(html) ?: return null
        return stripHtml(match.groupValues[1]).replace('\n', ' ').trim().take(80).ifEmpty { null }
    }

    // MARK: - Embedded fonts (+ EPUB font de-obfuscation)

    /**
     * Font discovery, in priority order (deduplicated by archive path):
     *  1. `@font-face src:url(...)` in the book's stylesheets — what the book
     *     actually renders with, so it wins.
     *  2. Manifest items with a font media-type / extension.
     *  3. Any archive entry with a font extension (last resort).
     * Trusting the manifest alone is not enough: some publishers ship an OPF
     * listing a font that is not in the archive while the real (scrambled-
     * codepoint anti-copy) font is referenced only from the stylesheet.
     */
    private fun extractFonts(opf: OpfInfo, base: String, archive: Archive, assetsDir: File): Int {
        val paths = LinkedHashSet<String>()
        fun add(path: String) {
            // Android's Typeface loads TrueType/OpenType only — no WOFF.
            val resolved = archive.resolve(path) ?: return
            val lower = resolved.lowercase()
            if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")) paths += resolved
        }
        for (css in opf.cssHrefs) {
            val cssPath = resolvePath(css, base)
            val cssText = archive.text(cssPath) ?: continue
            val cssDir = cssPath.substringBeforeLast('/', "")
            for (block in RE_FONT_FACE.findAll(cssText)) {
                for (url in RE_CSS_URL.findAll(block.groupValues[1])) {
                    val raw = url.groupValues[1].trim()
                    if (raw.startsWith("data:", true) || raw.startsWith("http", true)) continue
                    add(resolvePath(raw, cssDir))
                }
            }
        }
        opf.fontHrefs.forEach { add(resolvePath(it, base)) }
        archive.paths.sorted().forEach { add(it) }
        if (paths.isEmpty()) return 0

        // Which font paths are obfuscated, and by which algorithm?
        val obfuscation = archive.text("META-INF/encryption.xml")?.let { xml ->
            RE_ENCRYPTION.findAll(xml).associate { decode(it.groupValues[2]) to it.groupValues[1] }
        }.orEmpty()

        var count = 0
        for (path in paths) {
            var data = archive.bytes(path)?.takeIf { it.isNotEmpty() } ?: continue
            // Match by suffix: encryption.xml URIs may be root-relative.
            val algorithm = obfuscation.entries.firstOrNull { path.endsWith(it.key) || it.key.endsWith(path) }?.value
            val uid = opf.uniqueIdentifier
            if (algorithm != null && uid != null) data = deobfuscate(data, uid, algorithm)
            File(assetsDir, BookAssets.fontName(count)).writeBytes(data)
            count++
        }
        return count
    }

    private fun deobfuscate(data: ByteArray, uid: String, algorithm: String): ByteArray {
        val key: ByteArray
        val prefixLength: Int
        when {
            algorithm.contains("idpf") -> {
                // IDPF: SHA-1 of the UID with all whitespace removed.
                key = MessageDigest.getInstance("SHA-1").digest(uid.filterNot { it.isWhitespace() }.toByteArray())
                prefixLength = 1040
            }
            algorithm.contains("adobe") -> {
                // Adobe: 16 bytes from the UID's UUID hex digits.
                val hex = uid.replace("urn:uuid:", "").replace("-", "")
                key = hex.chunked(2).mapNotNull { it.takeIf { h -> h.length == 2 }?.toIntOrNull(16)?.toByte() }.toByteArray()
                prefixLength = 1024
            }
            else -> return data
        }
        if (key.isEmpty()) return data
        val out = data.copyOf()
        for (i in 0 until minOf(prefixLength, out.size)) out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }

    // MARK: - HTML segmentation

    /** Text runs and `<img src>` / SVG `<image xlink:href>` references, in document order. */
    private fun segmentsOf(html: String): List<Segment> {
        val result = ArrayList<Segment>()
        var cursor = 0
        for (match in RE_IMG_TAG.findAll(html)) {
            if (match.range.first > cursor) result += Segment.Text(html.substring(cursor, match.range.first))
            result += Segment.Image(match.groupValues[1])
            cursor = match.range.last + 1
        }
        if (cursor < html.length) result += Segment.Text(html.substring(cursor))
        return if (result.isEmpty()) listOf(Segment.Text(html)) else result
    }

    /**
     * Cuts [html] in front of the block element carrying each fragment id so
     * the element starts its own segment. Pieces come back in document order;
     * the first carries no id. Ids that cannot be located (or sit on an inline
     * element with no block tag directly before it) are simply not cut.
     */
    private fun splitHtml(html: String, ids: List<String>): List<Pair<String?, String>> {
        if (ids.isEmpty()) return listOf(null to html)
        val cuts = ids.mapNotNull { id -> anchorTagLocation(id, html)?.let { it to id } }.sortedBy { it.first }
        if (cuts.isEmpty()) return listOf(null to html)
        val pieces = ArrayList<Pair<String?, String>>()
        var cursor = 0
        var currentId: String? = null
        for ((location, id) in cuts) {
            // An empty piece is kept on purpose: two ids on the same element must
            // both resolve to that element's offset.
            pieces += currentId to html.substring(cursor, maxOf(cursor, location))
            cursor = maxOf(cursor, location)
            currentId = id
        }
        pieces += currentId to html.substring(cursor)
        return pieces
    }

    /** Index of the "<" opening the block element that carries [id], or null. */
    private fun anchorTagLocation(id: String, html: String): Int? {
        var attr = html.indexOf("id=\"$id\"")
        if (attr < 0) attr = html.indexOf("id='$id'")
        if (attr <= 0) return null
        // Must be the `id` attribute itself, not the tail of e.g. `data-id=`.
        if (!html[attr - 1].isWhitespace()) return null
        val open = html.lastIndexOf('<', attr)
        if (open < 0) return null
        if (tagName(open, html) in SPLITTABLE_TAGS) return open
        // Inline anchor: accept it when a block open tag sits directly before it
        // (`<h2><a id="x"></a>Title</h2>`), cutting in front of that block tag.
        var probe = open
        while (probe > 0 && html[probe - 1].isWhitespace()) probe--
        if (probe == 0 || html[probe - 1] != '>') return null
        val prevOpen = html.lastIndexOf('<', probe - 1)
        if (prevOpen < 0 || html.getOrNull(prevOpen + 1) == '/') return null
        return if (tagName(prevOpen, html) in SPLITTABLE_TAGS) prevOpen else null
    }

    private fun tagName(location: Int, html: String): String {
        var end = location + 1
        while (end < html.length && html[end].isLetterOrDigit()) end++
        return html.substring(location + 1, end).lowercase()
    }

    /** Resolves an href (possibly with ../) relative to a directory inside the zip. */
    private fun resolvePath(href: String, dir: String): String {
        val s = decode(href.substringBefore('#').substringBefore('?'))
        if (s.startsWith("/")) return s.removePrefix("/")
        val components = if (dir.isEmpty()) ArrayList() else ArrayList(dir.split("/"))
        for (part in s.split("/")) {
            when (part) {
                "", "." -> continue
                ".." -> if (components.isNotEmpty()) components.removeAt(components.size - 1)
                else -> components.add(part)
            }
        }
        return components.joinToString("/")
    }

    private fun stripHtml(html: String): String {
        var s = html
        s = RE_HEAD.replace(s, "")
        s = RE_TITLE.replace(s, "")
        s = RE_SCRIPT.replace(s, "")
        s = RE_STYLE.replace(s, "")
        s = RE_COMMENT.replace(s, "")
        // Collapse source whitespace (HTML semantics) before deriving line breaks.
        s = RE_SOURCE_WS.replace(s, " ")
        s = RE_BLOCK.replace(s, "\n")
        s = RE_TAGS.replace(s, "")
        // Drop the spaces that collapsing left next to block breaks.
        s = RE_LINE_EDGE_WS.replace(s, "\n")
        s = decodeEntities(s)
        s = RE_BLANK_LINES.replace(s, "\n\n")
        return s.trim()
    }

    /**
     * Numeric and named entities first, `&amp;` last: a source containing the
     * literal text "&amp;lt;" means the author wants to display "&lt;", and
     * decoding &amp; before the others would wrongly collapse it to "<".
     */
    private fun decodeEntities(input: String): String {
        if (input.indexOf('&') < 0) return input
        var s = RE_NUM_ENTITY.replace(input) { m ->
            val inner = m.groupValues[1]
            val codePoint = if (inner[0] == 'x' || inner[0] == 'X') inner.substring(1).toIntOrNull(16) else inner.toIntOrNull()
            // A decoded CR is dropped: EPUB line breaks come from block elements.
            if (codePoint == null || codePoint == 0x0D || codePoint == 0 || !Character.isValidCodePoint(codePoint)) ""
            else String(Character.toChars(codePoint))
        }
        s = RE_NAMED_ENTITY.replace(s) { m -> NAMED_ENTITIES[m.groupValues[1]] ?: m.value }
        return s.replace("&amp;", "&")
    }

    // MARK: - OPF

    private class OpfInfo(
        val title: String,
        val author: String,
        val spineHrefs: List<String>,
        val fontHrefs: List<String>,
        val cssHrefs: List<String>,
        val uniqueIdentifier: String?,
        val coverHref: String?,
        val ncxHref: String?,
        val navHref: String?,
    )

    private fun newParser(xml: String): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // Real-world OPF/NCX files use HTML entities and other things a strict
        // XML parser rejects.
        runCatching { setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        setInput(xml.reader())
    }

    private fun localName(parser: XmlPullParser): String = parser.name.substringAfter(':').lowercase()

    private fun parseOpf(xml: String): OpfInfo {
        var title = ""
        var author = ""
        val manifest = LinkedHashMap<String, String>()
        val spine = ArrayList<String>()
        val fontHrefs = ArrayList<String>()
        val cssHrefs = ArrayList<String>()
        val imageHrefs = ArrayList<String>()
        val identifiers = LinkedHashMap<String, String>()
        var uniqueIdRef: String? = null
        var coverImageHref: String? = null
        var metaCoverId: String? = null
        var ncxHref: String? = null
        var navHref: String? = null
        var capturing: String? = null
        var capturingIdKey: String? = null
        val buffer = StringBuilder()

        // A malformed tail must not lose what was already read.
        runCatching {
            val parser = newParser(xml)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (val local = localName(parser)) {
                        "package" -> uniqueIdRef = parser.getAttributeValue(null, "unique-identifier")
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                manifest[id] = href
                                val media = parser.getAttributeValue(null, "media-type")?.lowercase().orEmpty()
                                val lower = href.lowercase()
                                if (media.contains("font") || lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")) {
                                    fontHrefs += href
                                }
                                if (media == "text/css" || lower.endsWith(".css")) cssHrefs += href
                                if (media.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                                    lower.endsWith(".png") || lower.endsWith(".gif")
                                ) imageHrefs += href
                                val props = parser.getAttributeValue(null, "properties").orEmpty()
                                if (props.contains("cover-image")) coverImageHref = href
                                if (media.contains("dtbncx") || lower.endsWith(".ncx")) ncxHref = href
                                if (props.split(' ').contains("nav")) navHref = href
                            }
                        }
                        "itemref" -> parser.getAttributeValue(null, "idref")?.let { spine += it }
                        "meta" -> if (parser.getAttributeValue(null, "name")?.lowercase() == "cover") {
                            metaCoverId = parser.getAttributeValue(null, "content")
                        }
                        "title", "creator", "identifier" -> {
                            capturing = local
                            capturingIdKey = parser.getAttributeValue(null, "id")
                            buffer.clear()
                        }
                    }
                    XmlPullParser.TEXT -> if (capturing != null) buffer.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        val local = localName(parser)
                        if (local == capturing) {
                            val value = buffer.toString().trim()
                            if (value.isNotEmpty()) when (local) {
                                "title" -> if (title.isEmpty()) title = value
                                "creator" -> if (author.isEmpty()) author = value
                                "identifier" -> identifiers[capturingIdKey ?: "_${identifiers.size}"] = value
                            }
                            capturing = null
                        }
                    }
                }
                event = parser.next()
            }
        }

        // Spine idrefs → manifest hrefs in reading order; with no spine, every
        // (x)html manifest item in document order.
        var hrefs = spine.mapNotNull { manifest[it] }
        if (hrefs.isEmpty()) {
            hrefs = manifest.values.filter {
                val lower = it.lowercase()
                lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm")
            }
        }
        // Cover: EPUB3 marker → EPUB2 meta id → an image named "cover" → first image.
        val coverHref = coverImageHref
            ?: metaCoverId?.let { manifest[it] }
            ?: imageHrefs.firstOrNull { it.contains("cover", ignoreCase = true) }
            ?: imageHrefs.firstOrNull()

        return OpfInfo(
            title = title,
            author = author,
            spineHrefs = hrefs,
            fontHrefs = fontHrefs,
            cssHrefs = cssHrefs,
            uniqueIdentifier = uniqueIdRef?.let { identifiers[it] } ?: identifiers.values.firstOrNull(),
            coverHref = coverHref,
            ncxHref = ncxHref,
            navHref = navHref,
        )
    }
}

class EpubException(message: String) : Exception(message)
