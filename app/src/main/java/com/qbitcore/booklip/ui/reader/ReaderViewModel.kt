package com.qbitcore.booklip.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.data.ReadingStatsRepository
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.model.Highlight
import com.qbitcore.booklip.model.HighlightColor
import com.qbitcore.booklip.settings.ReadingSettings
import com.qbitcore.booklip.settings.SettingsRepository
import com.qbitcore.booklip.tts.BooklipTts
import com.qbitcore.booklip.tts.TtsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val book: Book? = null,
    val paragraphs: List<String> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class ReaderViewModel(
    application: Application,
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: ReadingStatsRepository,
    private val bookId: String,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    val settings: StateFlow<ReadingSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingSettings())

    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val highlights: StateFlow<List<Highlight>> = repository.highlights(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tts = BooklipTts(application)
    val ttsState: StateFlow<TtsUiState> = tts.state

    private val sessionStart = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState(isLoading = false, errorMessage = "Book not found")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = repository.readContent(book)
                    val chapters = repository.readChapters(book)
                    val paragraphs = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
                    paragraphs to chapters
                }
            }
            _uiState.value = result.fold(
                onSuccess = { (paragraphs, chapters) ->
                    ReaderUiState(book = book, paragraphs = paragraphs, chapters = chapters, isLoading = false)
                },
                onFailure = { ReaderUiState(book = book, isLoading = false, errorMessage = it.message) },
            )
        }
    }

    /** [paragraphIndex] is the first visible paragraph — the reader's unit of position. */
    fun saveProgress(paragraphIndex: Int) {
        val state = _uiState.value
        val total = state.paragraphs.size
        val book = state.book ?: return
        if (total == 0) return
        val progress = paragraphIndex.toDouble() / total
        viewModelScope.launch {
            repository.updateProgress(book.id, progress, paragraphIndex)
        }
    }

    fun updateSettings(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    // MARK: - Bookmarks

    fun toggleBookmark(paragraphIndex: Int) {
        val book = _uiState.value.book ?: return
        val existing = bookmarks.value.firstOrNull { paragraphIndexOf(it.progress) == paragraphIndex }
        viewModelScope.launch {
            if (existing != null) {
                repository.deleteBookmark(existing)
            } else {
                val total = _uiState.value.paragraphs.size.coerceAtLeast(1)
                repository.addBookmark(book.id, paragraphIndex.toDouble() / total, snippetFor(paragraphIndex))
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    // MARK: - Highlights

    fun highlightAt(paragraphIndex: Int): Highlight? = highlights.value.firstOrNull { it.paragraphIndex == paragraphIndex }

    fun setHighlight(paragraphIndex: Int, color: HighlightColor) {
        val book = _uiState.value.book ?: return
        // Replace, don't stack: a paragraph that's already highlighted and gets
        // a new color should end up with one highlight, not two.
        val existing = highlightAt(paragraphIndex)
        viewModelScope.launch {
            if (existing != null) repository.deleteHighlight(existing)
            val total = _uiState.value.paragraphs.size.coerceAtLeast(1)
            repository.addHighlight(book.id, paragraphIndex, color, snippetFor(paragraphIndex), paragraphIndex.toDouble() / total)
        }
    }

    fun clearHighlight(paragraphIndex: Int) {
        val existing = highlightAt(paragraphIndex) ?: return
        viewModelScope.launch { repository.deleteHighlight(existing) }
    }

    private fun snippetFor(paragraphIndex: Int): String =
        _uiState.value.paragraphs.getOrNull(paragraphIndex)?.take(80).orEmpty()

    fun paragraphIndexOf(progress: Double): Int {
        val total = _uiState.value.paragraphs.size
        if (total == 0) return 0
        return (progress * total).toInt().coerceIn(0, total - 1)
    }

    // MARK: - Text to speech

    fun toggleTtsPlayPause(fromParagraphIndex: Int) {
        val state = ttsState.value
        when {
            state.isPlaying -> tts.pause()
            state.isPaused -> tts.resume()
            else -> tts.speak(_uiState.value.paragraphs, fromParagraphIndex)
        }
    }

    fun stopTts() = tts.stop()
    fun setTtsRate(rate: Float) = tts.setRate(rate)
    fun setTtsPitch(pitch: Float) = tts.setPitch(pitch)
    fun setTtsVoice(name: String) = tts.setVoice(name)
    fun setTtsSleepTimer(minutes: Int?) = tts.setSleepTimer(minutes)

    override fun onCleared() {
        tts.release()
        val elapsedSeconds = (System.currentTimeMillis() - sessionStart) / 1000.0
        // viewModelScope is already torn down by the time onCleared() runs, so a
        // coroutine launched on it here would never actually execute — use a
        // scope of our own for this one fire-and-forget save.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { statsRepository.record(elapsedSeconds) }
        super.onCleared()
    }
}

class ReaderViewModelFactory(
    private val application: Application,
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: ReadingStatsRepository,
    private val bookId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReaderViewModel(application, repository, settingsRepository, statsRepository, bookId) as T
}
