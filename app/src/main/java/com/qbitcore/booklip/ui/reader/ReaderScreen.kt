package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbitcore.booklip.model.HighlightColor
import com.qbitcore.booklip.settings.ReaderFont
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val scope = rememberCoroutineScope()

    var showAppearance by remember { mutableStateOf(false) }
    var showTts by remember { mutableStateOf(false) }
    var showNavigate by remember { mutableStateOf(false) }
    var highlightTargetIndex by remember { mutableStateOf<Int?>(null) }

    val listState = remember(uiState.book?.id) { LazyListState(uiState.book?.charIndex ?: 0) }

    // Keyed on listState itself — it's a new instance each time the book id
    // changes (see above), and an unkeyed remember here would permanently
    // capture the very first (pre-load placeholder) LazyListState.
    val currentParagraph by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
    val bookmarkedParagraphs = remember(bookmarks, uiState.paragraphs.size) {
        bookmarks.map { viewModel.paragraphIndexOf(it.progress) }.toSet()
    }
    val highlightByParagraph = remember(highlights) { highlights.associateBy { it.paragraphIndex } }

    // Save progress a moment after scrolling settles, not on every frame.
    LaunchedEffect(uiState.paragraphs) {
        if (uiState.paragraphs.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(800)
            .collect { index -> viewModel.saveProgress(index) }
    }

    // Follow the currently-spoken paragraph while TTS is playing.
    LaunchedEffect(ttsState.currentParagraphIndex) {
        val target = ttsState.currentParagraphIndex
        if (target != null && ttsState.isPlaying) {
            scope.launch { listState.animateScrollToItem(target) }
        }
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
                    IconButton(onClick = { viewModel.toggleBookmark(currentParagraph) }) {
                        Icon(
                            if (bookmarkedParagraphs.contains(currentParagraph)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                        )
                    }
                    IconButton(onClick = { showTts = true }) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Text to speech")
                    }
                    IconButton(onClick = { showNavigate = true }) {
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
                uiState.isLoading -> Text("Loading…", modifier = Modifier.padding(24.dp), color = textColor)
                uiState.errorMessage != null -> Text(
                    uiState.errorMessage.orEmpty(),
                    modifier = Modifier.padding(24.dp),
                    color = textColor,
                )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(uiState.paragraphs) { index, paragraph ->
                        val highlight = highlightByParagraph[index]
                        val isSpeaking = ttsState.currentParagraphIndex == index
                        val displayText = if (isSpeaking && ttsState.spokenRange != null) {
                            buildAnnotatedString {
                                val range = ttsState.spokenRange!!
                                val start = range.first.coerceIn(0, paragraph.length)
                                val end = (range.last + 1).coerceIn(start, paragraph.length)
                                append(paragraph.substring(0, start))
                                withStyle(SpanStyle(background = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))) {
                                    append(paragraph.substring(start, end))
                                }
                                append(paragraph.substring(end))
                            }
                        } else {
                            buildAnnotatedString { append(paragraph) }
                        }
                        Text(
                            text = displayText,
                            color = textColor,
                            fontFamily = fontFamily,
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize + settings.lineSpacing).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .then(
                                    if (highlight != null) {
                                        Modifier.background(Color(highlight.color.argb).copy(alpha = 0.35f))
                                    } else {
                                        Modifier
                                    }
                                )
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { highlightTargetIndex = index },
                                )
                                .padding(bottom = settings.lineSpacing.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAppearance) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showAppearance = false }, sheetState = sheetState) {
            AppearanceSheet(settings = settings, onChange = { viewModel.updateSettings(it) })
        }
    }

    if (showTts) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showTts = false }, sheetState = sheetState) {
            TtsSheet(
                state = ttsState,
                onTogglePlayPause = { viewModel.toggleTtsPlayPause(currentParagraph) },
                onStop = { viewModel.stopTts() },
                onVoiceSelected = { viewModel.setTtsVoice(it) },
                onRateChange = { viewModel.setTtsRate(it) },
                onPitchChange = { viewModel.setTtsPitch(it) },
                onSleepTimerChange = { viewModel.setTtsSleepTimer(it) },
            )
        }
    }

    if (showNavigate) {
        NavigateDialog(
            chapters = uiState.chapters,
            bookmarks = bookmarks,
            highlights = highlights,
            onDismiss = { showNavigate = false },
            onJumpToProgress = { progress ->
                showNavigate = false
                val target = (progress * uiState.paragraphs.size).toInt().coerceIn(0, (uiState.paragraphs.size - 1).coerceAtLeast(0))
                scope.launch { listState.scrollToItem(target) }
            },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onDeleteHighlight = { /* handled via clearHighlight to keep one source of truth */
                viewModel.clearHighlight(it.paragraphIndex)
            },
        )
    }

    highlightTargetIndex?.let { index ->
        HighlightPickerDialog(
            currentColor = highlightByParagraph[index]?.color,
            onDismiss = { highlightTargetIndex = null },
            onPick = { color -> viewModel.setHighlight(index, color); highlightTargetIndex = null },
            onClear = { viewModel.clearHighlight(index); highlightTargetIndex = null },
        )
    }
}

@Composable
private fun HighlightPickerDialog(
    currentColor: HighlightColor?,
    onDismiss: () -> Unit,
    onPick: (HighlightColor) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HighlightColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color.argb))
                            .clickable { onPick(color) },
                    )
                }
            }
        },
        confirmButton = {
            if (currentColor != null) {
                TextButton(onClick = onClear) { Text("Remove") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
