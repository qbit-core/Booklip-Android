package com.qbitcore.booklip.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.model.SortOption
import com.qbitcore.booklip.model.ViewMode

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Cancel, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = modifier.fillMaxWidth(),
    )
}

/** View mode + sort order in one toolbar menu, each with a check on the current choice. */
@Composable
fun ViewOptionsMenu(state: LibraryUiState, onViewMode: (ViewMode) -> Unit, onSort: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (state.viewMode == ViewMode.LIST) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                contentDescription = "View and sort",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuHeader("View")
            ViewMode.entries.forEach { mode ->
                CheckedMenuItem(mode.label, mode == state.viewMode) {
                    onViewMode(mode)
                    expanded = false
                }
            }
            HorizontalDivider()
            MenuHeader("Sort by")
            SortOption.entries.forEach { option ->
                CheckedMenuItem(option.label, option == state.sortOption) {
                    onSort(option)
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CheckedMenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { if (checked) Icon(Icons.Filled.Check, contentDescription = "Selected") else Spacer(Modifier.size(24.dp)) },
        onClick = onClick,
    )
}

/** Books as an adaptive grid or a list, with selection checkmarks in select mode. */
@Composable
fun BooksCollection(
    books: List<Book>,
    vm: LibraryViewModel,
    state: LibraryUiState,
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
    /** Folder cards / rows shown before the books (Folders tab). */
    leading: List<FolderEntry> = emptyList(),
    onOpenFolder: (FolderEntry) -> Unit = {},
    onRenameFolder: (BookFolder) -> Unit = {},
    onDeleteFolder: (BookFolder) -> Unit = {},
) {
    val minWidth = state.viewMode.minCellWidthDp
    if (minWidth != null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minWidth.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier,
        ) {
            items(leading, key = { "folder:" + it.key }) { entry ->
                FolderItem(entry, asRow = false, onOpenFolder, onRenameFolder, onDeleteFolder)
            }
            items(books, key = { it.id }) { book -> BookItem(book, asRow = false, vm, state, onOpenBook) }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            items(leading, key = { "folder:" + it.key }) { entry ->
                FolderItem(entry, asRow = true, onOpenFolder, onRenameFolder, onDeleteFolder)
            }
            items(books, key = { it.id }) { book -> BookItem(book, asRow = true, vm, state, onOpenBook) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookItem(book: Book, asRow: Boolean, vm: LibraryViewModel, state: LibraryUiState, onOpenBook: (Book) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = book.id in vm.selectedIds
    Box {
        val click = Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(
            onClick = { if (vm.isSelecting) vm.toggleSelection(book.id) else onOpenBook(book) },
            onLongClick = { if (!vm.isSelecting) menuOpen = true },
        )
        if (asRow) BookRow(book, click) else BookCard(book, click)
        if (vm.isSelecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(2.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Move to Folder") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    moving = true
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    confirmDelete = true
                },
            )
        }
    }
    if (moving) {
        FolderPickerDialog(state.folders, current = book.folderId, onDismiss = { moving = false }) { folder ->
            vm.moveBook(book, folder)
            moving = false
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete “${book.title}”?",
            message = "This will permanently delete the book and its file. This cannot be undone.",
            onDismiss = { confirmDelete = false },
        ) {
            vm.deleteBook(book)
            confirmDelete = false
        }
    }
}

/** "No Folder" plus every folder; picking one moves the book(s) there. */
@Composable
fun FolderPickerDialog(folders: List<BookFolder>, current: String?, onDismiss: () -> Unit, onPick: (BookFolder?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder") },
        text = {
            LazyColumn {
                item { FolderChoice("No Folder", selected = false) { onPick(null) } }
                items(folders, key = { it.id }) { folder -> FolderChoice(folder.name, selected = folder.id == current) { onPick(folder) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Current folder", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // The whole name starts selected, so typing replaces it and the keyboard is up at once.
    var name by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val submit = { if (name.text.isNotBlank()) onConfirm(name.text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = name.text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The bar shown at the bottom in select mode: count, All / None, Move, Delete. */
@Composable
fun SelectionBar(vm: LibraryViewModel, state: LibraryUiState, visible: List<Book>, modifier: Modifier = Modifier) {
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val count = vm.selectedIds.size
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count selected", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (visible.isNotEmpty()) {
                TextButton(onClick = { vm.toggleSelectAll(visible) }) { Text(if (vm.allSelected(visible)) "None" else "All") }
            }
            TextButton(onClick = { moving = true }, enabled = count > 0) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                Text("Move")
            }
            TextButton(onClick = { confirmDelete = true }, enabled = count > 0) {
                val tint = if (count > 0) MaterialTheme.colorScheme.error else Color.Unspecified
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(18.dp), tint = if (count > 0) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                Text("Delete", color = tint)
            }
        }
    }
    if (moving) {
        FolderPickerDialog(state.folders, current = null, onDismiss = { moving = false }) { folder ->
            vm.moveSelected(folder)
            moving = false
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete $count Book${if (count == 1) "" else "s"}?",
            message = "This will permanently delete the selected books and their files. This cannot be undone.",
            onDismiss = { confirmDelete = false },
        ) {
            vm.deleteSelected()
            confirmDelete = false
        }
    }
}
