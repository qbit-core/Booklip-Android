package com.qbitcore.booklip.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A page's aspect ratio, known cheaply (metadata only) before any rendering. */
data class PdfPageSize(val width: Int, val height: Int)

data class PdfUiState(
    val book: Book? = null,
    val pageSizes: List<PdfPageSize> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Android's PdfRenderer can only have one page open at a time and is not
 * safe for concurrent access, so every render/metadata call goes through
 * [renderMutex]. Pages are rendered lazily and evicted once they scroll far
 * from the visible window — mirrors the iOS PDFReaderView's renderWindow.
 */
class PdfReaderViewModel(
    application: Application,
    private val repository: BookRepository,
    private val bookId: String,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState

    private val bitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = bitmaps

    private val renderMutex = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    init {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _uiState.value = PdfUiState(isLoading = false, errorMessage = "Book not found")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val descriptor = ParcelFileDescriptor.open(
                        repository.bookFile(book),
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                    val r = PdfRenderer(descriptor)
                    val sizes = (0 until r.pageCount).map { i ->
                        r.openPage(i).use { PdfPageSize(it.width, it.height) }
                    }
                    pfd = descriptor
                    renderer = r
                    sizes
                }
            }
            _uiState.value = result.fold(
                onSuccess = { sizes -> PdfUiState(book = book, pageSizes = sizes, isLoading = false) },
                onFailure = { PdfUiState(book = book, isLoading = false, errorMessage = it.message) },
            )
        }
    }

    /** Renders (and caches) the pages in [range]; evicts everything outside it. */
    fun setVisibleWindow(range: IntRange, targetWidthPx: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = renderer ?: return@launch
            val toRender = range.filter { it in _uiState.value.pageSizes.indices && bitmaps.value[it] == null }
            for (index in toRender) {
                val bitmap = renderMutex.withLock { renderPage(r, index, targetWidthPx) } ?: continue
                bitmaps.value = bitmaps.value + (index to bitmap)
            }
            val stale = bitmaps.value.filterKeys { it !in range }
            if (stale.isNotEmpty()) {
                bitmaps.value = bitmaps.value - stale.keys
                stale.values.forEach { it.recycle() }
            }
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, targetWidthPx: Int): Bitmap? {
        val size = _uiState.value.pageSizes.getOrNull(index) ?: return null
        val width = targetWidthPx.coerceAtLeast(1)
        val height = (size.height.toFloat() / size.width * width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        renderer.openPage(index).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        return bitmap
    }

    fun saveProgress(pageIndex: Int) {
        val total = _uiState.value.pageSizes.size
        val book = _uiState.value.book ?: return
        if (total == 0) return
        viewModelScope.launch {
            repository.updateProgress(book.id, pageIndex.toDouble() / total, pageIndex)
        }
    }

    override fun onCleared() {
        bitmaps.value.values.forEach { it.recycle() }
        renderer?.close()
        pfd?.close()
        super.onCleared()
    }
}

class PdfReaderViewModelFactory(
    private val application: Application,
    private val repository: BookRepository,
    private val bookId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PdfReaderViewModel(application, repository, bookId) as T
}
