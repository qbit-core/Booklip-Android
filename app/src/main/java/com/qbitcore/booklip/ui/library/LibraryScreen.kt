package com.qbitcore.booklip.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.data.BookRepository
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.SortOption
import com.qbitcore.booklip.model.ViewMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    repository: BookRepository,
    onOpenBook: (Book) -> Unit,
    onOpenStats: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var bookForAction by remember { mutableStateOf<Book?>(null) }
    var bookToMove by remember { mutableStateOf<Book?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(uri, context) ?: uri.lastPathSegment.orEmpty()
            viewModel.importBook(uri, name, selectedFolder)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val visibleBooks = uiState.books.filter { it.folderId == selectedFolder }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Booklip") },
                actions = {
                    IconButton(onClick = onOpenCloud) {
                        Icon(Icons.Filled.Cloud, contentDescription = "Cloud import")
                    }
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Reading stats")
                    }
                    IconButton(onClick = { showViewModeMenu = true }) {
                        Icon(Icons.Filled.GridView, contentDescription = "View mode")
                    }
                    DropdownMenu(expanded = showViewModeMenu, onDismissRequest = { showViewModeMenu = false }) {
                        ViewMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = { viewModel.setViewMode(mode); showViewModeMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { viewModel.setSortOption(option); showSortMenu = false },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                importLauncher.launch(
                    arrayOf(
                        "text/plain",
                        "text/markdown",
                        "application/epub+zip",
                        "application/pdf",
                        "application/octet-stream",
                    )
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Import book")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FolderRow(
                folders = uiState.folders,
                selectedFolderId = selectedFolder,
                onSelect = { selectedFolder = it },
                onNewFolder = { showNewFolderDialog = true },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (visibleBooks.isEmpty()) {
                    Text(
                        text = "No books yet. Tap + to import a .txt, .md, .epub, or .pdf file.",
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (uiState.viewMode == ViewMode.LIST) {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        items(visibleBooks, key = { it.id }) { book ->
                            BookRow(
                                book = book,
                                onClick = { onOpenBook(book) },
                                onLongClick = { bookForAction = book },
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(uiState.viewMode.columns),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(visibleBooks, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                repository = repository,
                                onClick = { onOpenBook(book) },
                                onLongClick = { bookForAction = book },
                            )
                        }
                    }
                }

                if (uiState.isImporting) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }

    bookForAction?.let { book ->
        BookActionSheet(
            book = book,
            onDismiss = { bookForAction = null },
            onDelete = { viewModel.deleteBook(book); bookForAction = null },
            onMove = { bookToMove = book; bookForAction = null },
        )
    }

    bookToMove?.let { book ->
        MoveBookDialog(
            folders = uiState.folders,
            onDismiss = { bookToMove = null },
            onMove = { folder -> viewModel.moveBook(book, folder); bookToMove = null },
        )
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name -> viewModel.createFolder(name); showNewFolderDialog = false },
        )
    }
}

@Composable
private fun FolderRow(
    folders: List<BookFolder>,
    selectedFolderId: String?,
    onSelect: (String?) -> Unit,
    onNewFolder: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selectedFolderId == null, onClick = { onSelect(null) }, label = { Text("All") })
        folders.forEach { folder ->
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onSelect(folder.id) },
                label = { Text(folder.name) },
            )
        }
        IconButton(onClick = onNewFolder) {
            Icon(Icons.Filled.Add, contentDescription = "New folder")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(book: Book, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${book.author} · ${book.format.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onLongClick) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
        }
    }
}

@Composable
private fun BookActionSheet(book: Book, onDismiss: () -> Unit, onDelete: () -> Unit, onMove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = { Text("Choose an action for this book.") },
        confirmButton = { TextButton(onClick = onMove) { Text("Move to folder") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } },
    )
}

@Composable
private fun MoveBookDialog(folders: List<BookFolder>, onDismiss: () -> Unit, onMove: (BookFolder?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                TextButton(onClick = { onMove(null) }) { Text("No folder") }
                folders.forEach { folder ->
                    TextButton(onClick = { onMove(folder) }) { Text(folder.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun queryDisplayName(uri: Uri, context: android.content.Context): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index)
    }
    return null
}
