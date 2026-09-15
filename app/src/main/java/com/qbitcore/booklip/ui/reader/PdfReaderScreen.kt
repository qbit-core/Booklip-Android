package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val RENDER_RADIUS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(viewModel: PdfReaderViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val pageBitmaps by viewModel.pageBitmaps.collectAsState()
    val listState = remember(uiState.book?.id) { LazyListState(uiState.book?.charIndex ?: 0) }
    val density = LocalDensity.current

    LaunchedEffect(uiState.pageSizes) {
        if (uiState.pageSizes.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(800)
            .collect { index -> viewModel.saveProgress(index) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.book?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.isLoading -> Text("Loading…", modifier = Modifier.padding(24.dp))
                uiState.errorMessage != null -> Text(uiState.errorMessage.orEmpty(), modifier = Modifier.padding(24.dp))
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)

                    LaunchedEffect(listState, widthPx, uiState.pageSizes.size) {
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.map { it.index }
                        }
                            .map { visible ->
                                if (visible.isEmpty()) null
                                else (visible.min() - RENDER_RADIUS).coerceAtLeast(0)..
                                    (visible.max() + RENDER_RADIUS).coerceAtMost(uiState.pageSizes.size - 1)
                            }
                            .distinctUntilChanged()
                            .collect { range -> if (range != null) viewModel.setVisibleWindow(range, widthPx) }
                    }

                    LazyColumn(state = listState, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                        itemsIndexed(uiState.pageSizes) { index, size ->
                            val aspect = size.width.toFloat() / size.height
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                                    .padding(vertical = 4.dp)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bitmap = pageBitmaps[index]
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
