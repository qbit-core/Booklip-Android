package com.qbitcore.booklip.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.settings.ReadingSettings
import com.qbitcore.booklip.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
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
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val bookId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    val settings: StateFlow<ReadingSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingSettings())

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
}

class ReaderViewModelFactory(
    private val repository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val bookId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReaderViewModel(repository, settingsRepository, bookId) as T
}
