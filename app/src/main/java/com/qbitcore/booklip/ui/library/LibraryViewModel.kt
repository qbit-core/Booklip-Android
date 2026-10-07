package com.qbitcore.booklip.ui.library

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.SortOption
import com.qbitcore.booklip.model.ViewMode
import com.qbitcore.booklip.settings.LibraryPrefs
import java.text.Collator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** All books, sorted by [sortOption]. */
    val books: List<Book> = emptyList(),
    val folders: List<BookFolder> = emptyList(),
    val sortOption: SortOption = SortOption.DATE_ADDED,
    val viewMode: ViewMode = ViewMode.MEDIUM_GRID,
    val isLoaded: Boolean = false,
) {
    fun booksIn(folderId: String?): List<Book> = books.filter { it.folderId == folderId }
    val unfiled: List<Book> get() = booksIn(null)
}

class LibraryViewModel(private val app: BooklipApplication) : ViewModel() {
    private val repository = app.repository

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.books,
        repository.folders,
        app.settingsRepository.libraryPrefs,
    ) { books, folders, prefs: LibraryPrefs ->
        LibraryUiState(sortBooks(books, prefs.sortOption), folders, prefs.sortOption, prefs.viewMode, isLoaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    /** Number of imports still running. */
    var importsInFlight by mutableIntStateOf(0)
        private set
    var importError by mutableStateOf<String?>(null)
        private set

    // Multi-select
    var isSelecting by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun sortBooks(books: List<Book>, option: SortOption): List<Book> {
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        return when (option) {
            SortOption.DATE_ADDED -> books.sortedByDescending { it.dateAdded }
            SortOption.TITLE -> books.sortedWith(compareBy(collator) { it.title })
            SortOption.AUTHOR -> books.sortedWith(compareBy(collator) { it.author })
            SortOption.PROGRESS -> books.sortedByDescending { it.progress }
            SortOption.FORMAT -> books.sortedBy { it.format.displayName }
        }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { app.settingsRepository.setSortOption(option) }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { app.settingsRepository.setViewMode(mode) }
    }

    // MARK: - Search

    /** Title/author match; an empty query keeps everything. */
    fun filter(list: List<Book>, search: String): List<Book> {
        val q = search.trim()
        if (q.isEmpty()) return list
        return list.filter { it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
    }

    /** Folders to show for a query: name matches, or the folder holds a matching book. */
    fun filterFolders(state: LibraryUiState, search: String): List<BookFolder> {
        val q = search.trim()
        if (q.isEmpty()) return state.folders
        return state.folders.filter { f -> f.name.contains(q, ignoreCase = true) || filter(state.booksIn(f.id), q).isNotEmpty() }
    }

    // MARK: - Selection

    fun selecting(on: Boolean) {
        isSelecting = on
        if (!on) selectedIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun allSelected(visible: List<Book>): Boolean = visible.isNotEmpty() && visible.all { it.id in selectedIds }

    /** Selects every book on screen, or clears them when they are all selected already. */
    fun toggleSelectAll(visible: List<Book>) {
        val ids = visible.map { it.id }.toSet()
        selectedIds = if (allSelected(visible)) selectedIds - ids else selectedIds + ids
    }

    fun moveSelected(folder: BookFolder?) {
        val ids = selectedIds
        selecting(false)
        viewModelScope.launch { repository.moveBooks(ids, folder?.id) }
    }

    fun deleteSelected() {
        val ids = selectedIds
        selecting(false)
        app.appScope.launch { repository.deleteBooks(ids) }
    }

    // MARK: - Books & folders

    /** Imports the picked files one after another; they keep importing if the screen goes away. */
    fun importBooks(files: List<Pair<Uri, String>>, folderId: String? = null) {
        if (files.isEmpty()) return
        importsInFlight += files.size
        app.appScope.launch {
            for ((uri, name) in files) {
                try {
                    repository.importBook(uri, name, folderId)
                } catch (e: Throwable) {
                    importError = "$name: ${e.message ?: "could not be imported."}"
                }
                importsInFlight--
            }
        }
    }

    fun clearImportError() {
        importError = null
    }

    fun deleteBook(book: Book) {
        app.appScope.launch { repository.deleteBooks(listOf(book.id)) }
    }

    fun moveBook(book: Book, folder: BookFolder?) {
        viewModelScope.launch { repository.moveBooks(listOf(book.id), folder?.id) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repository.createFolder(name) }
    }

    fun renameFolder(folder: BookFolder, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameFolder(folder, name.trim()) }
    }

    fun deleteFolder(folder: BookFolder) {
        viewModelScope.launch { repository.deleteFolder(folder) }
    }

    class Factory(private val app: BooklipApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(app) as T
    }
}
