package com.qbitcore.booklip.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.model.Book
import com.qbitcore.booklip.model.BookFolder
import com.qbitcore.booklip.ui.reader.EmptyState

/** A folder on the Folders tab; [folder] null is the "Unfiled" entry. */
class FolderEntry(val folder: BookFolder?, val count: Int) {
    val key: String get() = folder?.id ?: "unfiled"
    val name: String get() = folder?.name ?: "Unfiled"
}

private fun countLabel(count: Int) = "$count book${if (count == 1) "" else "s"}"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItem(
    entry: FolderEntry,
    asRow: Boolean,
    onOpen: (FolderEntry) -> Unit,
    onRename: (BookFolder) -> Unit,
    onDelete: (BookFolder) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val icon = if (entry.folder == null) Icons.Filled.Inbox else Icons.Filled.Folder
    Box {
        val base = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (asRow) 10.dp else 12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (asRow) 0.06f else 0.08f))
            .combinedClickable(onClick = { onOpen(entry) }, onLongClick = { if (entry.folder != null) menuOpen = true })
        if (asRow) {
            Row(base.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(countLabel(entry.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        } else {
            Column(base.height(140.dp).padding(16.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.weight(1f))
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(countLabel(entry.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val folder = entry.folder
        if (folder != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRename(folder)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete(folder)
                    },
                )
            }
        }
    }
}

/** The books of one folder ([folderId] null = Unfiled), with its own search, sort / view menu and select mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(vm: LibraryViewModel, folderId: String?, onBack: () -> Unit, onOpenBook: (Book) -> Unit) {
    val state by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val folder = state.folders.firstOrNull { it.id == folderId }
    val name = if (folderId == null) "Unfiled" else folder?.name.orEmpty()
    val all = state.booksIn(folderId)
    val books = vm.filter(all, query)

    // Leaving the folder ends select mode, as popping the screen does on iOS.
    DisposableEffect(Unit) { onDispose { vm.selecting(false) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (vm.isSelecting) {
                        TextButton(onClick = { vm.selecting(false) }) { Text("Done") }
                    } else {
                        ViewOptionsMenu(state, vm::setViewMode, vm::setSortOption)
                        IconButton(onClick = { vm.selecting(true) }, enabled = all.isNotEmpty()) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Select")
                        }
                    }
                },
            )
        },
        bottomBar = { if (vm.isSelecting) SelectionBar(vm, state, books) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (all.isNotEmpty()) {
                SearchField(query, { query = it }, "Search in $name", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            when {
                // The folder was deleted while open (or the id is stale).
                folderId != null && folder == null && state.isLoaded -> EmptyState(Icons.Filled.Folder, "No Folder", "This folder no longer exists.")
                all.isEmpty() -> EmptyState(Icons.Filled.Folder, "No Books", "Select books and choose Move to put them here.")
                books.isEmpty() -> EmptyState(Icons.Filled.SearchOff, "No Results", "No books match “$query”.")
                else -> BooksCollection(books, vm, state, onOpenBook, Modifier.fillMaxSize())
            }
        }
    }
}
