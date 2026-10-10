package com.qbitcore.booklip.ui.reader

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qbitcore.booklip.tts.TtsController
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One line of a dictionary entry. */
private class EntryLine(val kind: Kind, val text: String) {
    enum class Kind { LANGUAGE, PART_OF_SPEECH, TEXT }
}

/** [lines] null = the dictionary could not be reached; empty = no entry. [headword] is the form that was found. */
private class Entry(val headword: String, val source: String, val lines: List<EntryLine>?)

/**
 * The dictionary entry for [term], in a dialog over the page — the
 * counterpart of the iOS "Define" popover.
 *
 * Android has no system dictionary to present, so the entry is fetched from
 * Wiktionary and drawn here: the Korean edition for Korean text or a
 * Korean-language device (it defines Korean words and gives Korean meanings
 * for English ones), the English edition otherwise and as the fallback.
 */
@Composable
fun DefinitionDialog(term: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val korean = remember(term) { TtsController.containsHangul(term, 0, term.length) || Locale.getDefault().language == "ko" }
    val entry by produceState<Entry?>(initialValue = null, term) { value = lookUp(term, korean) }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val query = Uri.encode(entry?.headword ?: term)
    val webUrl = if (korean) "https://dict.naver.com/dict.search?query=$query" else "https://en.wiktionary.org/w/index.php?search=$query"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 520.dp).fillMaxWidth(),
        ) {
            Column {
                Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry?.headword ?: term,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { open(webUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                val found = entry
                if (found == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
                // Sized by the entry, up to about the height of the iOS popover.
                Box(Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 420.dp)) {
                    val lines = found?.lines
                    when {
                        found == null -> Unit
                        lines.isNullOrEmpty() -> Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            Text(
                                if (lines == null) "The dictionary could not be reached. Check your internet connection."
                                else "No definition found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            if (lines != null) {
                                TextButton(onClick = { open(webUrl) }) { Text(if (korean) "네이버 사전에서 찾기" else "Search Wiktionary") }
                            }
                        }
                        else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                            items(lines) { line ->
                                when (line.kind) {
                                    EntryLine.Kind.LANGUAGE -> Text(
                                        line.text,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                    )
                                    EntryLine.Kind.PART_OF_SPEECH -> Text(
                                        line.text,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                    )
                                    EntryLine.Kind.TEXT -> Text(
                                        line.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 3.dp),
                                    )
                                }
                            }
                            item {
                                Text(
                                    "${found.source} · CC BY-SA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun lookUp(term: String, korean: Boolean): Entry = withContext(Dispatchers.IO) {
    var reached = false
    val candidates = candidates(term)
    if (korean) {
        for (candidate in candidates) {
            val lines = runCatching { koreanEntry(candidate) }.onSuccess { reached = true }.getOrNull()
            if (!lines.isNullOrEmpty()) return@withContext Entry(candidate, "한국어 위키낱말사전", lines)
        }
    }
    for (candidate in candidates) {
        val lines = runCatching { englishEntry(candidate) }.onSuccess { reached = true }.getOrNull()
        if (!lines.isNullOrEmpty()) return@withContext Entry(candidate, "Wiktionary", lines)
    }
    Entry(term, "", if (reached) emptyList() else null)
}

private val STYLE_BLOCK = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
private val SPACES = Regex(" {2,}")

// Longest first, so "에서" is tried before "서".
private val KOREAN_PARTICLES = listOf(
    "에게서", "으로써", "으로서", "이라고", "에서", "에게", "으로", "까지", "부터", "처럼", "보다", "이나", "이라", "라고", "하고",
    "은", "는", "이", "가", "을", "를", "의", "에", "도", "로", "와", "과", "만", "나", "께",
)

/**
 * Spellings to try, most literal first: the selection as it is, lower-cased
 * (a word at the start of a sentence), and — for Korean — without a trailing
 * particle, since a selected word usually carries one ("마나님을" → "마나님").
 */
private fun candidates(term: String): List<String> {
    val result = LinkedHashSet<String>()
    result += term
    result += term.lowercase()
    if (TtsController.containsHangul(term, 0, term.length)) {
        for (particle in KOREAN_PARTICLES) {
            if (term.length > particle.length + 1 && term.endsWith(particle)) result += term.dropLast(particle.length)
        }
    }
    return result.toList()
}

private fun fetchJson(url: String): JSONObject? {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "Booklip-Android (ebook reader)")
        // 404 = no such entry: an answer, not a failure.
        if (connection.responseCode != 200) return null
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

/** The Korean Wiktionary entry as plain text, `== headings ==` turned into styled lines. */
private fun koreanEntry(title: String): List<EntryLine> {
    val json = fetchJson(
        "https://ko.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=1&redirects=1&format=json&titles=" + Uri.encode(title)
    ) ?: return emptyList()
    val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
    val extract = pages.keys().asSequence().mapNotNull { pages.optJSONObject(it)?.optString("extract") }.firstOrNull { it.isNotBlank() }
        ?: return emptyList()
    val lines = ArrayList<EntryLine>()
    var skipping = false
    for (raw in extract.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val level = line.takeWhile { it == '=' }.length
        if (level >= 2 && line.endsWith("=")) {
            val heading = line.trim('=', ' ')
            // Pronunciation and translation tables are noise in a quick look-up.
            skipping = heading in setOf("발음", "번역", "참고", "같이 보기")
            if (skipping) continue
            lines += EntryLine(if (level == 2) EntryLine.Kind.LANGUAGE else EntryLine.Kind.PART_OF_SPEECH, heading)
        } else if (!skipping && !line.startsWith("IPA") && !line.contains("IPA(표기)")) {
            lines += EntryLine(EntryLine.Kind.TEXT, line)
        }
    }
    // Headings alone are not a definition.
    return if (lines.any { it.kind == EntryLine.Kind.TEXT }) lines.take(60) else emptyList()
}

/** The English Wiktionary definitions, English senses first. */
private fun englishEntry(title: String): List<EntryLine> {
    val json = fetchJson("https://en.wiktionary.org/api/rest_v1/page/definition/" + Uri.encode(title.replace(' ', '_')))
        ?: return emptyList()
    val lines = ArrayList<EntryLine>()
    for (key in json.keys().asSequence().sortedBy { if (it == "en") 0 else 1 }) {
        val entries = json.optJSONArray(key) ?: continue
        var language: String? = null
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val raw = entry.optJSONArray("definitions") ?: continue
            val definitions = (0 until raw.length()).mapNotNull { j ->
                // Inline <style> blocks would otherwise come through as text.
                val html = raw.getJSONObject(j).optString("definition").replace(STYLE_BLOCK, "")
                Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replace('\n', ' ').replace(SPACES, " ").trim().ifEmpty { null }
            }.take(8)
            if (definitions.isEmpty()) continue
            if (language == null) {
                language = entry.optString("language")
                lines += EntryLine(EntryLine.Kind.LANGUAGE, language)
            }
            lines += EntryLine(EntryLine.Kind.PART_OF_SPEECH, entry.optString("partOfSpeech"))
            definitions.forEachIndexed { index, definition -> lines += EntryLine(EntryLine.Kind.TEXT, "${index + 1}. $definition") }
        }
    }
    return lines.take(60)
}
