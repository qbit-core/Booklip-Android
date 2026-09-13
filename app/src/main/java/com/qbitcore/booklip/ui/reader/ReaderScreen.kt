package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.settings.ReaderFont
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var showAppearance by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }

    // Keyed on the book id (null while loading, then the real id) so the saved
    // charIndex is picked up once — plain rememberLazyListState would freeze
    // its initial index at 0 from the first (pre-load) composition.
    val listState = remember(uiState.book?.id) { LazyListState(uiState.book?.charIndex ?: 0) }

    // Save progress a moment after scrolling settles, not on every frame.
    LaunchedEffect(uiState.paragraphs) {
        if (uiState.paragraphs.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(800)
            .collect { index -> viewModel.saveProgress(index) }
    }

    val preset = settings.preset
    val background = Color(preset.background)
    val textColor = Color(preset.text)
    val fontFamily = when (settings.fontFamily) {
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS_SERIF -> FontFamily.SansSerif
        ReaderFont.MONOSPACE -> FontFamily.Monospace
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        uiState.book?.title.orEmpty(),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showContents = true }) {
                        Icon(ListIcon, contentDescription = "Contents")
                    }
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Filled.TextFields, contentDescription = "Appearance")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(background),
        ) {
            when {
                uiState.isLoading -> Text(
                    "Loading…",
                    modifier = Modifier.padding(24.dp),
                    color = textColor,
                )
                uiState.errorMessage != null -> Text(
                    uiState.errorMessage.orEmpty(),
                    modifier = Modifier.padding(24.dp),
                    color = textColor,
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(20.dp),
                ) {
                    itemsIndexed(uiState.paragraphs) { _, paragraph ->
                        Text(
                            text = paragraph,
                            color = textColor,
                            fontFamily = fontFamily,
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize + settings.lineSpacing).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = settings.lineSpacing.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAppearance) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAppearance = false },
            sheetState = sheetState,
        ) {
            AppearanceSheet(
                settings = settings,
                onChange = { viewModel.updateSettings(it) },
            )
        }
    }

    if (showContents) {
        ContentsDialog(
            chapters = uiState.chapters,
            totalParagraphs = uiState.paragraphs.size,
            onDismiss = { showContents = false },
            onSelect = { paragraphIndex ->
                showContents = false
                scope.launch { listState.scrollToItem(paragraphIndex) }
            },
        )
    }
}

@Composable
private fun ContentsDialog(
    chapters: List<Chapter>,
    totalParagraphs: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contents") },
        text = {
            Column {
                if (chapters.isEmpty()) {
                    Text("No table of contents for this book.")
                } else {
                    chapters.forEach { chapter ->
                        TextButton(onClick = { onSelect((chapter.progress * totalParagraphs).toInt()) }) {
                            Text(chapter.title, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
