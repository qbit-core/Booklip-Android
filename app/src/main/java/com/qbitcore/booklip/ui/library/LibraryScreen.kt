package com.qbitcore.booklip.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.ui.reader.EmptyState

private enum class LibraryTab(val label: String) { ALL("All Books"), FOLDERS("Folders") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenBook: (Book) -> Unit,
    /** null = the Unfiled entry. */
    onOpenFolder: (String?) -> Unit,
    onOpenStats: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(LibraryTab.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var addMenuOpen by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<BookFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<BookFolder?>(null) }

    // Any file type is offered: providers report .epub / .md under assorted
    // MIME types, and the extension is what decides the format.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importBooks(uris.map { it to (displayName(it, context) ?: it.lastPathSegment.orEmpty()) })
    }
    val browseFiles = { importLauncher.launch(arrayOf("*/*")) }

    BackHandler(enabled = vm.isSelecting) { vm.selecting(false) }

    val isSearching = query.isNotBlank()
    val matchingBooks = vm.filter(state.books, query)
    // What "All" selects: the books on screen. The Folders tab lists books only while searching.
    val visibleBooks = if (tab == LibraryTab.ALL || isSearching) matchingBooks else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    if (vm.isSelecting) {
                        TextButton(onClick = { vm.selecting(false) }) { Text("Done") }
                    } else {
                        ViewOptionsMenu(state, vm::setViewMode, vm::setSortOption)
                        IconButton(onClick = onOpenStats) { Icon(Icons.Filled.BarChart, contentDescription = "Reading stats") }
                        IconButton(onClick = { vm.selecting(true) }, enabled = state.books.isNotEmpty()) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Select")
                        }
                        if (tab == LibraryTab.FOLDERS) {
                            IconButton(onClick = { showNewFolder = true }) { Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder") }
                        }
                        Box {
                            IconButton(onClick = { addMenuOpen = true }) { Icon(Icons.Filled.Add, contentDescription = "Add books") }
                            DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Browse Files") },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                    onClick = {
                                        addMenuOpen = false
                                        browseFiles()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Cloud Storage…") },
                                    leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                                    onClick = {
                                        addMenuOpen = false
                                        onOpenCloud()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { if (vm.isSelecting) SelectionBar(vm, state, visibleBooks) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                SearchField(query, { query = it }, "Search books", Modifier.padding(horizontal = 16.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LibraryTab.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = tab == item,
                            onClick = { tab = item },
                            enabled = !vm.isSelecting,
                            shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size),
                            icon = {},
                        ) { Text(item.label) }
                    }
                }
                when (tab) {
                    LibraryTab.ALL -> when {
                        !state.isLoaded -> Box(Modifier.fillMaxSize())
                        state.books.isEmpty() -> EmptyState(
                            Icons.AutoMirrored.Filled.MenuBook, "No Books Yet", "Tap + to import .txt, .epub, .pdf, or .md files.",
                        ) { Button(onClick = browseFiles, modifier = Modifier.padding(top = 8.dp)) { Text("Import Book") } }
                        matchingBooks.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "No Results", "No books match “$query”.")
                        else -> BooksCollection(matchingBooks, vm, state, onOpenBook, Modifier.fillMaxSize())
                    }
                    LibraryTab.FOLDERS -> {
                        // While searching: folders whose name matches or that hold a
                        // match (counts reflect the query), Unfiled if it holds one,
                        // then every matching book so a hit is one tap away.
                        val folders = vm.filterFolders(state, query).map { FolderEntry(it, vm.filter(state.booksIn(it.id), query).size) }
                        val unfiled = vm.filter(state.unfiled, query)
                        val entries = if (unfiled.isEmpty()) folders else folders + FolderEntry(null, unfiled.size)
                        when {
                            !state.isLoaded -> Box(Modifier.fillMaxSize())
                            isSearching && entries.isEmpty() && matchingBooks.isEmpty() ->
                                EmptyState(Icons.Filled.SearchOff, "No Results", "Nothing matches “$query”.")
                            entries.isEmpty() -> EmptyState(Icons.Filled.Folder, "No Folders", "Tap the folder+ button to create one.")
                            else -> BooksCollection(
                                books = if (isSearching) matchingBooks else emptyList(),
                                vm = vm,
                                state = state,
                                onOpenBook = onOpenBook,
                                modifier = Modifier.fillMaxSize(),
                                leading = entries,
                                onOpenFolder = { if (!vm.isSelecting) onOpenFolder(it.folder?.id) },
                                onRenameFolder = { folderToRename = it },
                                onDeleteFolder = { folderToDelete = it },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = vm.importsInFlight > 0,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            ) {
                Surface(shape = RoundedCornerShape(50), tonalElevation = 6.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            if (vm.importsInFlight > 1) "Importing ${vm.importsInFlight} books…" else "Importing…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    vm.importError?.let { message ->
        AlertDialog(
            onDismissRequest = vm::clearImportError,
            title = { Text("Import Error") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::clearImportError) { Text("OK") } },
        )
    }
    if (showNewFolder) {
        NameDialog("New Folder", "", "Create", onDismiss = { showNewFolder = false }) { name ->
            vm.createFolder(name)
            showNewFolder = false
        }
    }
    folderToRename?.let { folder ->
        NameDialog("Rename Folder", folder.name, "Rename", onDismiss = { folderToRename = null }) { name ->
            vm.renameFolder(folder, name)
            folderToRename = null
        }
    }
    folderToDelete?.let { folder ->
        ConfirmDeleteDialog(
            title = "Delete “${folder.name}”?",
            message = "The folder is removed. Its books stay in your library, under Unfiled.",
            onDismiss = { folderToDelete = null },
        ) {
            vm.deleteFolder(folder)
            folderToDelete = null
        }
    }
}

private fun displayName(uri: Uri, context: Context): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
