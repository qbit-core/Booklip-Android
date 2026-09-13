package com.qbitcore.booklip.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.SortOption
import com.qbitcore.booklip.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val folders: List<BookFolder> = emptyList(),
    val sortOption: SortOption = SortOption.DATE_ADDED,
    val viewMode: ViewMode = ViewMode.MEDIUM_GRID,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
)

class LibraryViewModel(private val repository: BookRepository) : ViewModel() {
    private val sortOption = MutableStateFlow(SortOption.DATE_ADDED)
    private val viewMode = MutableStateFlow(ViewMode.MEDIUM_GRID)
    private val importState = MutableStateFlow(false to null as String?)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.books,
        repository.folders,
        sortOption,
        viewMode,
        importState,
    ) { books, folders, sort, mode, (importing, error) ->
        LibraryUiState(sortBooks(books, sort), folders, sort, mode, importing, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    private fun sortBooks(books: List<Book>, option: SortOption): List<Book> = when (option) {
        SortOption.DATE_ADDED -> books.sortedByDescending { it.dateAdded }
        SortOption.TITLE -> books.sortedBy { it.title.lowercase() }
        SortOption.AUTHOR -> books.sortedBy { it.author.lowercase() }
        SortOption.PROGRESS -> books.sortedByDescending { it.progress }
        SortOption.FORMAT -> books.sortedBy { it.format.displayName }
    }

    fun setSortOption(option: SortOption) {
        sortOption.value = option
    }

    fun setViewMode(mode: ViewMode) {
        viewMode.value = mode
    }

    fun importBook(uri: Uri, displayName: String, folderId: String? = null) {
        importState.value = true to null
        viewModelScope.launch {
            val result = runCatching { repository.importBook(uri, displayName, folderId) }
            importState.value = false to result.exceptionOrNull()?.message
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch { repository.deleteBook(book) }
    }

    fun moveBook(book: Book, folder: BookFolder?) {
        viewModelScope.launch { repository.moveBook(book, folder?.id) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repository.createFolder(name) }
    }

    fun deleteFolder(folder: BookFolder) {
        viewModelScope.launch { repository.deleteFolder(folder) }
    }

    fun clearError() {
        importState.value = importState.value.first to null
    }
}

class LibraryViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(repository) as T
}
