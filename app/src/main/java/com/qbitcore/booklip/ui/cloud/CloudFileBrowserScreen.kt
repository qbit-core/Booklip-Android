package com.qbitcore.booklip.ui.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.qbitcore.booklip.data.cloud.CloudFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudFileBrowserScreen(viewModel: CloudViewModel, onExit: () -> Unit, onImported: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.folderStack.joinToString(" / ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.goBack()) onExit() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                uiState.files.isEmpty() -> Text("This folder is empty.", modifier = Modifier.padding(24.dp))
                else -> LazyColumn {
                    items(uiState.files, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                when {
                                    file.isFolder -> viewModel.openFolder(file)
                                    file.isSupportedBook -> viewModel.importFile(file, folderId = null, onImported = onImported)
                                }
                            },
                        )
                    }
                }
            }
            if (uiState.isImporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: CloudFile, onClick: () -> Unit) {
    val isActionable = file.isFolder || file.isSupportedBook
    ListItem(
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (!file.isFolder && !file.isSupportedBook) Text("Unsupported format")
        },
        leadingContent = {
            Icon(
                if (file.isFolder) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (isActionable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        modifier = Modifier.clickable(enabled = isActionable, onClick = onClick),
    )
}
