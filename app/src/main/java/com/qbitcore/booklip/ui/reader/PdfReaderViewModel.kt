package com.qbitcore.booklip.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.settings.ReadingSettings
import com.qbitcore.booklip.tts.TtsState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A page's size in PDF points — known cheaply, before any rendering. */
data class PdfPageSize(val width: Int, val height: Int)

/** Matches of [query] in the document text; [pages] holds the page of each match, in order. */
class PdfSearchState(val query: String, val pages: IntArray, val index: Int)

/**
 * PDFs are shown as rendered pages. `PdfRenderer` allows one open page at a
 * time and is not thread-safe, so every use goes through [rendererMutex].
 *
 * Reading the text layer (for text-to-speech and search) needs Android 15;
 * on older versions those two controls are simply not offered.
 */
class PdfReaderViewModel(private val app: BooklipApplication, private val bookId: String) : ViewModel() {
    private val repository = app.repository
    private val tts = app.tts

    var book by mutableStateOf<Book?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var pageSizes by mutableStateOf<List<PdfPageSize>>(emptyList())
        private set

    var pageIndex by mutableIntStateOf(0)
        private set
    /** [SeekRequest.offset] is a page index here. */
    var seek by mutableStateOf(SeekRequest(0, 0))
        private set

    /** Rendered pages near the visible ones. */
    val bitmaps = mutableStateMapOf<Int, Bitmap>()
    /** Search-match rectangles per page, in PDF points. */
    val matchRects = mutableStateMapOf<Int, List<RectF>>()

    val textSupported: Boolean = Build.VERSION.SDK_INT >= 35
    /** The document's text has been extracted: speech and search are ready. */
    var textReady by mutableStateOf(false)
        private set
    var search by mutableStateOf<PdfSearchState?>(null)
        private set
    var isSearching by mutableStateOf(false)
        private set

    val settings: StateFlow<ReadingSettings?> = app.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ttsState: StateFlow<TtsState> = tts.state

    private val rendererMutex = Mutex()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var renderJob: Job? = null
    private var renderWidth = 0
    private var saveJob: Job? = null
    private var sessionStart: Long? = null
    private var lastSavedPage = -1

    private var fullText = ""
    /** Offset in [fullText] where each page's text starts. */
    private var pageOffsets = IntArray(0)

    val pageCount: Int get() = pageSizes.size
    val progress: Double get() = progressOf(pageIndex)

    fun progressOf(page: Int): Double = if (pageCount > 1) page.toDouble() / (pageCount - 1) else 0.0
    fun pageOf(progress: Double): Int =
        if (pageCount > 1) (progress.coerceIn(0.0, 1.0) * (pageCount - 1)).roundToInt() else 0

    init {
        viewModelScope.launch {
            val loaded = repository.getBook(bookId)
            if (loaded == null) {
                errorMessage = "Book not found"
                isLoading = false
                return@launch
            }
            book = loaded
            tts.setNowPlaying(loaded.title, loaded.author)
            try {
                val sizes = withContext(Dispatchers.IO) {
                    val fd = ParcelFileDescriptor.open(repository.bookFile(loaded), ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdf = try {
                        PdfRenderer(fd)
                    } catch (e: Throwable) {
                        fd.close()
                        throw e
                    }
                    descriptor = fd
                    renderer = pdf
                    (0 until pdf.pageCount).map { i -> pdf.openPage(i).use { PdfPageSize(it.width, it.height) } }
                }
                if (sizes.isEmpty()) throw IllegalStateException("This PDF has no pages.")
                pageSizes = sizes
                val start = (if (loaded.charIndex >= 0) loaded.charIndex else pageOf(loaded.progress)).coerceIn(0, sizes.size - 1)
                pageIndex = start
                lastSavedPage = start
                seek = SeekRequest(start, 1)
                if (textSupported) extractText()
            } catch (e: SecurityException) {
                errorMessage = "This PDF is password-protected."
            } catch (e: Throwable) {
                errorMessage = e.message ?: "Cannot load PDF document."
            }
            isLoading = false
        }
    }

    // MARK: - Rendering

    /** Renders the pages in [range] at [widthPx] and drops the ones far outside it. */
    fun setVisibleWindow(range: IntRange, widthPx: Int) {
        if (widthPx <= 0 || pageSizes.isEmpty()) return
        if (widthPx != renderWidth) {
            renderWidth = widthPx
            bitmaps.clear()
        }
        // Dropped, not recycled: a frame may still be drawing them.
        bitmaps.keys.filter { it !in range }.forEach { bitmaps.remove(it) }
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            // Nearest the reading position first.
            for (index in range.sortedBy { kotlin.math.abs(it - pageIndex) }) {
                if (bitmaps.containsKey(index) || renderWidth != widthPx) continue
                val bitmap = withContext(Dispatchers.IO) { rendererMutex.withLock { renderPage(index, widthPx) } } ?: continue
                if (renderWidth == widthPx) bitmaps[index] = bitmap
            }
        }
        if (search != null) loadMatchRects(range)
    }

    private fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val pdf = renderer ?: return null
        val size = pageSizes.getOrNull(index) ?: return null
        val width = widthPx.coerceIn(1, MAX_RENDER_WIDTH)
        val height = (size.height.toFloat() / size.width * width).toInt().coerceAtLeast(1)
        return runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            pdf.openPage(index).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            bitmap
        }.getOrNull()
    }

    // MARK: - Position

    fun onPageChanged(page: Int) {
        val clamped = page.coerceIn(0, maxOf(0, pageCount - 1))
        if (clamped == pageIndex) return
        pageIndex = clamped
        scheduleSave()
    }

    fun seekToPage(page: Int) {
        if (pageCount == 0) return
        pageIndex = page.coerceIn(0, pageCount - 1)
        seek = SeekRequest(pageIndex, seek.token + 1)
        scheduleSave()
    }

    fun seekToProgress(progress: Double) = seekToPage(pageOf(progress))

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            saveProgress()
        }
    }

    private suspend fun saveProgress() {
        if (pageCount == 0 || pageIndex == lastSavedPage) return
        lastSavedPage = pageIndex
        repository.updateProgress(bookId, progress, pageIndex)
    }

    fun onSessionStart() {
        if (sessionStart == null) sessionStart = System.currentTimeMillis()
    }

    fun onSessionEnd() {
        saveJob?.cancel()
        val started = sessionStart
        sessionStart = null
        app.appScope.launch {
            saveProgress()
            if (started != null) app.statsRepository.record((System.currentTimeMillis() - started) / 1000.0)
        }
    }

    // MARK: - Bookmarks

    fun currentBookmark(bookmarks: List<Bookmark>): Bookmark? = bookmarks.firstOrNull { pageOf(it.progress) == pageIndex }

    fun toggleBookmark() {
        if (pageCount == 0) return
        val existing = currentBookmark(bookmarks.value)
        viewModelScope.launch {
            if (existing != null) repository.deleteBookmark(existing)
            else repository.addBookmark(bookId, progress, pageSnippet(pageIndex))
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    private fun pageSnippet(page: Int): String {
        if (textReady && page < pageOffsets.size) {
            val end = if (page + 1 < pageOffsets.size) pageOffsets[page + 1] else fullText.length
            val text = fullText.substring(pageOffsets[page], minOf(end, pageOffsets[page] + 120)).replace('\n', ' ').trim().take(60)
            if (text.isNotEmpty()) return text
        }
        return "Page ${page + 1}"
    }

    // MARK: - Text (Android 15+)

    private fun extractText() {
        viewModelScope.launch {
            val count = pageCount
            val offsets = IntArray(count)
            val text = withContext(Dispatchers.IO) {
                val builder = StringBuilder()
                for (index in 0 until count) {
                    offsets[index] = builder.length
                    // One page per lock, so page rendering is never held up for long.
                    val pageText = rendererMutex.withLock { readPageText(index) }
                    builder.append(pageText).append("\n\n")
                }
                builder.toString()
            }
            fullText = text
            pageOffsets = offsets
            textReady = true
        }
    }

    @RequiresApi(35)
    private fun readPageTextApi35(index: Int): String =
        renderer?.openPage(index)?.use { page -> page.textContents.joinToString("\n") { it.text } }.orEmpty()

    private fun readPageText(index: Int): String =
        if (Build.VERSION.SDK_INT >= 35) runCatching { readPageTextApi35(index) }.getOrDefault("") else ""

    @RequiresApi(35)
    private fun findRectsApi35(index: Int, query: String): List<RectF> =
        renderer?.openPage(index)?.use { page -> page.searchText(query).flatMap { it.bounds } }.orEmpty()

    private fun pageOfOffset(offset: Int): Int = indexOfLastAtMost(pageOffsets, offset)

    // MARK: - Search

    fun search(query: String) {
        if (!textReady) return
        if (query.isBlank()) {
            clearSearch()
            return
        }
        isSearching = true
        val from = pageIndex
        viewModelScope.launch {
            val pages = withContext(Dispatchers.Default) {
                val found = ArrayList<Int>()
                var i = fullText.indexOf(query, 0, ignoreCase = true)
                while (i >= 0 && found.size < 20_000) {
                    found += pageOfOffset(i)
                    i = fullText.indexOf(query, i + query.length, ignoreCase = true)
                }
                found.toIntArray()
            }
            // Start at the first match on or after the page being read.
            val index = pages.indexOfFirst { it >= from }.coerceAtLeast(0)
            matchRects.clear()
            search = PdfSearchState(query, pages, index)
            isSearching = false
            pages.getOrNull(index)?.let { seekToPage(it) }
        }
    }

    fun stepSearch(delta: Int) {
        val current = search ?: return
        if (current.pages.isEmpty()) return
        val index = Math.floorMod(current.index + delta, current.pages.size)
        search = PdfSearchState(current.query, current.pages, index)
        seekToPage(current.pages[index])
    }

    fun clearSearch() {
        isSearching = false
        search = null
        matchRects.clear()
    }

    private fun loadMatchRects(range: IntRange) {
        val query = search?.query ?: return
        val wanted = search?.pages?.toSet().orEmpty()
        viewModelScope.launch {
            for (index in range) {
                if (index !in wanted || matchRects.containsKey(index)) continue
                val rects = withContext(Dispatchers.IO) {
                    rendererMutex.withLock {
                        if (Build.VERSION.SDK_INT >= 35) runCatching { findRectsApi35(index, query) }.getOrDefault(emptyList()) else emptyList()
                    }
                }
                if (search?.query == query) matchRects[index] = rects
            }
        }
    }

    // MARK: - Text to speech

    fun toggleTts() {
        if (!textReady) return
        tts.prepare()
        tts.togglePlayPause(fullText, pageOffsets.getOrElse(pageIndex) { 0 })
    }

    /** The page holding the sentence being spoken, if it is this document's. */
    fun spokenPage(state: TtsState): Int? = state.spokenRange?.takeIf { textReady }?.let { pageOfOffset(it.start) }

    fun stopTts() = tts.stop()
    fun setTtsRate(rate: Float) = tts.setRate(rate)
    fun setTtsPitch(pitch: Float) = tts.setPitch(pitch)
    fun setTtsVoice(name: String?) = tts.setVoice(name)
    fun setTtsSleepTimer(minutes: Int?) = tts.setSleepTimer(minutes)

    fun updateSettings(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { app.settingsRepository.update(transform) }
    }

    override fun onCleared() {
        tts.stop()
        onSessionEnd()
        val pdf = renderer
        val fd = descriptor
        renderer = null
        descriptor = null
        // After any render still in flight has let go of the renderer.
        app.appScope.launch(Dispatchers.IO) {
            rendererMutex.withLock {
                runCatching { pdf?.close() }
                runCatching { fd?.close() }
            }
        }
        super.onCleared()
    }

    class Factory(private val app: BooklipApplication, private val bookId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PdfReaderViewModel(app, bookId) as T
    }

    private companion object {
        const val MAX_RENDER_WIDTH = 2400
    }
}
