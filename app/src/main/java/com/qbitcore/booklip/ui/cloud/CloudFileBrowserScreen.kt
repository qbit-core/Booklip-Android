package com.qbitcore.booklip.ui.cloud

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.data.cloud.CloudFile
import com.qbitcore.booklip.ui.reader.EmptyState

/**
 * File browser shared by Dropbox, OneDrive and Google Drive. Tapping a book
 * imports it; Select mode picks several files and whole folders (a folder is
 * imported as a library folder of the same name, subfolders flattened into it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudFileBrowserScreen(viewModel: CloudViewModel, onExit: () -> Unit, onImported: () -> Unit) {
    val importing = viewModel.importProgress != null
    val goBack = {
        when {
            importing -> Unit
            viewModel.isSelecting -> viewModel.selecting(false)
            !viewModel.goBack() -> onExit()
        }
    }
    BackHandler(onBack = goBack)

    // Return to the library once an import that started on this screen is done.
    val completedAtEntry = remember { viewModel.importsCompleted }
    LaunchedEffect(viewModel.importsCompleted) {
        if (viewModel.importsCompleted != completedAtEntry) onImported()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.folderStack.lastOrNull()?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = goBack, enabled = !importing) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (viewModel.selectableFiles.isNotEmpty()) {
                        TextButton(onClick = { viewModel.selecting(!viewModel.isSelecting) }, enabled = !importing) {
                            Text(if (viewModel.isSelecting) "Done" else "Select")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (viewModel.isSelecting) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${viewModel.selected.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::toggleSelectAll) { Text(if (viewModel.allSelected) "None" else "All") }
                        Button(onClick = viewModel::importSelected, enabled = viewModel.selected.isNotEmpty() && !importing) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                            Text("Import")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val loadError = viewModel.loadError
            when {
                viewModel.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loadError != null -> EmptyState(Icons.Filled.Warning, "Error", loadError) {
                    TextButton(onClick = viewModel::reload) { Text("Try Again") }
                }
                viewModel.files.isEmpty() -> EmptyState(Icons.Filled.FolderOff, "No Supported Files", "This folder has no .txt, .epub, .pdf, or .md files.")
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(viewModel.files, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            isSelecting = viewModel.isSelecting,
                            isSelected = file.id in viewModel.selected,
                            onClick = {
                                when {
                                    viewModel.isSelecting -> viewModel.toggle(file)
                                    file.isFolder -> viewModel.openFolder(file)
                                    file.isSupportedBook -> viewModel.importSingle(file)
                                }
                            },
                        )
                        HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }

            viewModel.importProgress?.let { progress ->
                // Blocks the list while files download; the import itself continues even if the app is left.
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(progress, color = Color.White)
                }
            }
        }
    }

    viewModel.importError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportError,
            title = { Text("Import") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearImportError) { Text("OK") } },
        )
    }
}

@Composable
private fun FileRow(file: CloudFile, isSelecting: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val selectable = file.isSelectable
    val primary = if (selectable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = selectable, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isSelecting && selectable) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            when {
                file.isFolder -> Icons.Filled.Folder
                else -> when (file.name.substringAfterLast('.', "").lowercase()) {
                    "epub" -> Icons.AutoMirrored.Filled.MenuBook
                    "pdf" -> Icons.Filled.PictureAsPdf
                    "txt" -> Icons.Filled.Description
                    "md", "markdown" -> Icons.AutoMirrored.Filled.Notes
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
            },
            contentDescription = null,
            tint = when {
                file.isFolder -> Color(0xFFF4B400)
                selectable -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, color = primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = when {
                !file.isFolder && file.size != null -> Formatter.formatShortFileSize(context, file.size)
                file.isFolder && isSelecting -> "Import as a folder"
                else -> null
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            file.isFolder && !isSelecting -> Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            !selectable -> Text("Unsupported", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
