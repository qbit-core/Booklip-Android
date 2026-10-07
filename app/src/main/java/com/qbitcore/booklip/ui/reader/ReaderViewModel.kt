package com.qbitcore.booklip.ui.reader

import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.model.Highlight
import com.qbitcore.booklip.model.HighlightColor
import com.qbitcore.booklip.parser.ParsedBook
import com.qbitcore.booklip.settings.ReadingSettings
import com.qbitcore.booklip.tts.TtsState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A request for the reader view to show [offset]; [token] makes repeated
 * requests for the same offset distinct. [exact] puts the offset itself at
 * the top (a saved page start, a chapter); otherwise the reader shows the
 * line / page that contains it (a search match, a point on the progress bar).
 */
data class SeekRequest(val offset: Int, val token: Int, val exact: Boolean = true)

class SearchState(val query: String, val matches: IntArray, val index: Int, val truncated: Boolean) {
    val length: Int get() = query.length
    val current: Int? get() = matches.getOrNull(index)
}

class ReaderViewModel(private val app: BooklipApplication, private val bookId: String) : ViewModel() {
    private val repository = app.repository
    private val tts = app.tts

    var book by mutableStateOf<Book?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var document by mutableStateOf<ReaderDocument?>(null)
        private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    /** The book's own font, when it embeds one that Android can load. */
    var embeddedTypeface by mutableStateOf<Typeface?>(null)
        private set

    /** Current reading position: the offset at the top of the screen / start of the page. */
    var charIndex by mutableIntStateOf(0)
        private set
    var seek by mutableStateOf(SeekRequest(0, 0))
        private set

    /** Start offset of every page for the current layout; null until computed. */
    var pageStarts by mutableStateOf<IntArray?>(null)
        private set
    /** 0…1 while paginating, null when idle. */
    var paginationProgress by mutableStateOf<Float?>(null)
        private set

    var search by mutableStateOf<SearchState?>(null)
        private set
    var isSearching by mutableStateOf(false)
        private set

    val settings: StateFlow<ReadingSettings?> = app.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val highlights: StateFlow<List<Highlight>> = repository.highlights(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ttsState: StateFlow<TtsState> = tts.state

    private var layoutSpec: LayoutSpec? = null
    private var paginationJob: Job? = null
    private var searchJob: Job? = null
    private var saveJob: Job? = null
    private var sessionStart: Long? = null
    private var lastSavedIndex = -1

    val length: Int get() = document?.length ?: 0
    val progress: Double get() = progressOf(charIndex)

    fun progressOf(offset: Int): Double = if (length > 0) (offset.toDouble() / length).coerceIn(0.0, 1.0) else 0.0
    /** Rounded, so `offsetOf(progressOf(i)) == i` — a bookmark or chapter lands on the exact character. */
    fun offsetOf(progress: Double): Int = Math.round(progress.coerceIn(0.0, 1.0) * length).toInt().coerceIn(0, maxOf(0, length - 1))

    init {
        viewModelScope.launch {
            val loaded = repository.getBook(bookId)
            if (loaded == null) {
                errorMessage = "Book not found"
                isLoading = false
                return@launch
            }
            book = loaded
            tts.prepare()
            tts.setNowPlaying(loaded.title, loaded.author)
            try {
                val content = repository.loadContent(loaded)
                val (doc, typeface) = withContext(Dispatchers.IO) {
                    ReaderDocument.from(content) to loadEmbeddedFont(content.fontFiles, content.text)
                }
                embeddedTypeface = typeface
                chapters = content.chapters
                // The exact saved offset when there is one; otherwise the fraction.
                val start = if (loaded.charIndex >= 0) loaded.charIndex else Math.round(loaded.progress * doc.length).toInt()
                charIndex = start.coerceIn(0, maxOf(0, doc.length - 1))
                lastSavedIndex = charIndex
                seek = SeekRequest(charIndex, seek.token + 1)
                document = doc
            } catch (e: Throwable) {
                errorMessage = e.message ?: "This book could not be opened."
            }
            isLoading = false
        }
    }

    /**
     * The first embedded font that loads and covers the book's text. Books
     * that ship a scrambled-codepoint anti-copy font are only legible in it.
     */
    private fun loadEmbeddedFont(files: List<File>, text: String): Typeface? {
        if (files.isEmpty()) return null
        val sample = buildString {
            for (c in text) {
                if (!c.isWhitespace() && c != ParsedBook.IMAGE_CHAR && !c.isSurrogate()) append(c)
                if (length >= 120) break
            }
        }
        val paint = Paint()
        var fallback: Typeface? = null
        for (file in files) {
            val typeface = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: continue
            if (typeface == Typeface.DEFAULT) continue
            if (fallback == null) fallback = typeface
            if (sample.isEmpty()) return typeface
            paint.typeface = typeface
            val covered = sample.count { paint.hasGlyph(it.toString()) }
            if (covered >= sample.length * 0.8) return typeface
        }
        return fallback
    }

    // MARK: - Position

    /** Reported by the reader view as the user scrolls / turns pages. */
    fun onPositionChanged(offset: Int) {
        val clamped = offset.coerceIn(0, maxOf(0, length - 1))
        if (clamped == charIndex) return
        charIndex = clamped
        scheduleSave()
    }

    fun seekTo(offset: Int, exact: Boolean = true) {
        if (document == null) return
        charIndex = offset.coerceIn(0, maxOf(0, length - 1))
        seek = SeekRequest(charIndex, seek.token + 1, exact)
        scheduleSave()
    }

    fun seekToProgress(progress: Double, exact: Boolean = true) = seekTo(offsetOf(progress), exact)

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            saveProgress()
        }
    }

    private suspend fun saveProgress() {
        if (document == null || charIndex == lastSavedIndex) return
        lastSavedIndex = charIndex
        repository.updateProgress(bookId, progress, charIndex)
    }

    /** The reader came to the foreground: start timing the session. */
    fun onSessionStart() {
        if (sessionStart == null) sessionStart = System.currentTimeMillis()
    }

    /** The reader left the foreground (or closed): persist the position and the time read. */
    fun onSessionEnd() {
        saveJob?.cancel()
        val started = sessionStart
        sessionStart = null
        // Not viewModelScope: this also runs from onCleared, when that scope is gone.
        app.appScope.launch {
            saveProgress()
            if (started != null) app.statsRepository.record((System.currentTimeMillis() - started) / 1000.0)
        }
    }

    // MARK: - Pagination

    /** Called by the reader view whenever the page geometry or font changes. */
    fun setLayoutSpec(spec: LayoutSpec) {
        val doc = document ?: return
        if (!spec.isValid || spec == layoutSpec) return
        layoutSpec = spec
        paginationJob?.cancel()
        val key = PageCacheKey(bookId, doc.length, spec)
        pageCache.get(key)?.let {
            pageStarts = it
            paginationProgress = null
            return
        }
        pageStarts = null
        paginationJob = viewModelScope.launch {
            // Sliders in the appearance panel fire a stream of specs; wait for it to settle.
            delay(250)
            paginationProgress = 0f
            val starts = withContext(Dispatchers.Default) {
                var reported = 0f
                ReaderLayout.paginate(doc, spec) { fraction ->
                    if (fraction - reported >= 0.01f) {
                        reported = fraction
                        viewModelScope.launch { if (layoutSpec == spec) paginationProgress = fraction }
                    }
                }
            }
            if (layoutSpec == spec) {
                pageCache.put(key, starts)
                pageStarts = starts
                paginationProgress = null
            }
        }
    }

    val totalPages: Int? get() = pageStarts?.size

    /** 1-based page number of [offset], when the page count is known. */
    fun pageNumber(offset: Int): Int? = pageStarts?.let { indexOfLastAtMost(it, offset) + 1 }

    // MARK: - Bookmarks

    /** The bookmark on the page being read, if any. */
    fun currentBookmark(bookmarks: List<Bookmark>): Bookmark? {
        if (length == 0) return null
        val starts = pageStarts
        if (starts != null) {
            val page = indexOfLastAtMost(starts, charIndex)
            return bookmarks.firstOrNull { indexOfLastAtMost(starts, offsetOf(it.progress)) == page }
        }
        // Page count not known yet: "same position" = within a small fraction.
        val here = progress
        return bookmarks.minByOrNull { kotlin.math.abs(it.progress - here) }
            ?.takeIf { kotlin.math.abs(it.progress - here) < 0.0005 }
    }

    /** Removes the bookmark on this page, or adds one. */
    fun toggleBookmark() {
        if (document == null) return
        val existing = currentBookmark(bookmarks.value)
        viewModelScope.launch {
            if (existing != null) repository.deleteBookmark(existing)
            else repository.addBookmark(bookId, progress, snippet(charIndex, 60))
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    private fun snippet(offset: Int, maxLength: Int): String {
        val text = document?.text ?: return ""
        val start = offset.coerceIn(0, text.length)
        // Read a little more than needed: leading blank lines are trimmed away.
        return text.substring(start, minOf(text.length, start + maxLength * 2))
            .replace(ParsedBook.IMAGE_CHAR, ' ').replace('\n', ' ').trim().take(maxLength)
    }

    // MARK: - Highlights

    fun addHighlight(start: Int, end: Int, color: HighlightColor) {
        val text = document?.text ?: return
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        if (e <= s) return
        val replaced = highlights.value.filter { it.location < e && it.end > s }
        viewModelScope.launch {
            // Re-highlighting a passage recolours it instead of stacking a second highlight.
            replaced.forEach { repository.deleteHighlight(it) }
            val snippet = text.substring(s, minOf(e, s + 80)).replace(ParsedBook.IMAGE_CHAR, ' ').replace('\n', ' ').trim()
            repository.addHighlight(bookId, s, e - s, color, snippet, progressOf(s))
        }
    }

    fun removeHighlights(start: Int, end: Int) {
        val overlapping = highlights.value.filter { it.location < end && it.end > start }
        viewModelScope.launch { overlapping.forEach { repository.deleteHighlight(it) } }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch { repository.deleteHighlight(highlight) }
    }

    // MARK: - Search

    /** Finds [query] in the book and jumps to the first match at or after the current position. */
    fun search(query: String) {
        val doc = document ?: return
        searchJob?.cancel()
        if (query.isEmpty()) {
            clearSearch()
            return
        }
        isSearching = true
        val from = charIndex
        searchJob = viewModelScope.launch {
            val (matches, truncated) = withContext(Dispatchers.Default) {
                val found = ArrayList<Int>()
                var i = doc.text.indexOf(query, 0, ignoreCase = true)
                while (i >= 0 && found.size < MAX_MATCHES) {
                    found += i
                    i = doc.text.indexOf(query, i + query.length, ignoreCase = true)
                }
                found.toIntArray() to (i >= 0)
            }
            val index = if (matches.isEmpty()) 0 else indexOfFirstAtLeast(matches, from).let { if (it >= matches.size) 0 else it }
            search = SearchState(query, matches, index, truncated)
            isSearching = false
            matches.getOrNull(index)?.let { seekTo(it, exact = false) }
        }
    }

    fun stepSearch(delta: Int) {
        val current = search ?: return
        if (current.matches.isEmpty()) return
        val index = Math.floorMod(current.index + delta, current.matches.size)
        search = SearchState(current.query, current.matches, index, current.truncated)
        seekTo(current.matches[index], exact = false)
    }

    fun clearSearch() {
        searchJob?.cancel()
        isSearching = false
        search = null
    }

    // MARK: - Text to speech

    fun toggleTts() {
        val doc = document ?: return
        tts.togglePlayPause(doc.text, charIndex)
    }

    fun stopTts() = tts.stop()
    fun setTtsRate(rate: Float) = tts.setRate(rate)
    fun setTtsPitch(pitch: Float) = tts.setPitch(pitch)
    fun setTtsVoice(name: String?) = tts.setVoice(name)
    fun setTtsSleepTimer(minutes: Int?) = tts.setSleepTimer(minutes)

    fun updateSettings(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { app.settingsRepository.update(transform) }
    }

    override fun onCleared() {
        // Speech belongs to the open book: it ends when the reader closes
        // (never just because the app went to the background).
        tts.stop()
        onSessionEnd()
        super.onCleared()
    }

    private data class PageCacheKey(val bookId: String, val length: Int, val spec: LayoutSpec)

    class Factory(private val app: BooklipApplication, private val bookId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(app, bookId) as T
    }

    private companion object {
        const val MAX_MATCHES = 20_000
        /** Page tables survive closing and reopening a book (and flipping a setting back). */
        val pageCache = LruCache<PageCacheKey, IntArray>(12)
    }
}
