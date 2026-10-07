package com.qbitcore.booklip.ui.reader

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qbitcore.booklip.model.BookFormat
import com.qbitcore.booklip.model.HighlightColor
import com.qbitcore.booklip.settings.PageEffect
import com.qbitcore.booklip.ui.theme.BooklipTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Reader for text books (.txt, .epub, .md). PDFs use [PdfReaderScreen]. */
@Composable
fun ReaderScreen(vm: ReaderViewModel, onClose: () -> Unit) {
    val settings = vm.settings.collectAsState().value
    if (settings == null) {
        // Settings load in a few milliseconds; drawing with defaults first would flash the wrong theme.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val preset = settings.preset
    val bookmarks by vm.bookmarks.collectAsState()
    val highlights by vm.highlights.collectAsState()
    val ttsState by vm.ttsState.collectAsState()
    val focusManager = LocalFocusManager.current

    var showBars by rememberSaveable { mutableStateOf(true) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf("") }
    var highlightMode by rememberSaveable { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showTts by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var pendingHighlight by remember { mutableStateOf<IntRange?>(null) }

    // +1 / -1 page turns from taps and swipes, consumed by whichever reader is showing.
    val pageCommands = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    // Reading time counts while the reader is on screen, and the position is
    // saved whenever it leaves (home button, screen off, closing the book).
    ReadingSession(onStart = vm::onSessionStart, onEnd = vm::onSessionEnd)

    val view = LocalView.current
    DisposableEffect(autoScrolling) {
        view.keepScreenOn = autoScrolling
        onDispose { view.keepScreenOn = false }
    }

    BooklipTheme(useDarkTheme = preset.isDark) {
        SystemBarIcons(darkBackground = preset.isDark)
        val barColors = remember(preset) { BarColors.of(Color(preset.background), Color(preset.text)) }
        CompositionLocalProvider(LocalBarColors provides barColors) {
            val background = Color(preset.background)
            val textColor = Color(preset.text).toArgb()
            val spokenColor = Color(0xFF0A84FF).copy(alpha = if (preset.isDark) 0.40f else 0.26f).toArgb()

            Box(Modifier.fillMaxSize().background(background)) {
                val doc = vm.document
                val error = vm.errorMessage
                when {
                    vm.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> EmptyState(Icons.Filled.Warning, "Cannot Open Book", error)
                    doc != null && doc.length == 0 -> EmptyState(Icons.Filled.Warning, "Empty Book", "This book has no readable text.")
                    doc != null -> {
                        val typeface = (if (settings.useEmbeddedFont) vm.embeddedTypeface else null) ?: settings.font.typeface
                        val decorations = remember(highlights, vm.search, ttsState.spokenRange, spokenColor) {
                            Decorations(highlights, vm.search, ttsState.spokenRange, spokenColor)
                        }
                        val actions = remember(vm) {
                            SelectionActions(
                                onHighlight = { start, end -> pendingHighlight = start..end },
                                onRemoveHighlight = { start, end -> vm.removeHighlights(start, end) },
                                hasHighlight = { start, end -> vm.highlights.value.any { it.location < end && it.end > start } },
                            )
                        }
                        val insets = WindowInsets.statusBars.asPaddingValues()
                        val navInsets = WindowInsets.navigationBars.asPaddingValues()
                        // A page keeps a margin above and below. A scrolling column runs to the
                        // edges of the screen, so the line at the top is the line the position
                        // (and a later restore) refers to.
                        val margin = if (settings.pageEffect == PageEffect.PAPER) 28.dp else 10.dp
                        val padding = PaddingValues(
                            start = 22.dp,
                            end = 22.dp,
                            top = insets.calculateTopPadding() + margin,
                            bottom = navInsets.calculateBottomPadding() + margin,
                        )
                        val gestures = Modifier.readerGestures(
                            onTap = { x ->
                                when {
                                    // In highlight mode the bars stay: they hold the way out of it.
                                    showBars -> if (!highlightMode) {
                                        showBars = false
                                        focusManager.clearFocus()
                                    }
                                    x < 0.3f -> pageCommands.tryEmit(-1)
                                    x > 0.7f -> pageCommands.tryEmit(1)
                                    else -> showBars = true
                                }
                            },
                            onSwipe = { step -> pageCommands.tryEmit(step) },
                        )
                        val stopAutoScroll = { autoScrolling = false }
                        if (settings.pageEffect == PageEffect.PAPER) {
                            PaperReader(
                                vm, doc, settings, typeface, textColor, decorations, highlightMode,
                                autoScrolling, stopAutoScroll, pageCommands, actions, padding, gestures,
                            )
                        } else {
                            ScrollReader(
                                vm, doc, settings, typeface, textColor, decorations, highlightMode,
                                autoScrolling, stopAutoScroll, pageCommands, actions, padding, gestures,
                            )
                        }
                    }
                }

                // Scrolling text must not run under the clock or the gesture bar.
                SystemBarScrims(background)

                val book = vm.book
                AnimatedVisibility(
                    visible = showBars,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        title = book?.title.orEmpty(),
                        author = book?.author.orEmpty(),
                        isBookmarked = vm.currentBookmark(bookmarks) != null,
                        onBack = onClose,
                        onToggleBookmark = vm::toggleBookmark,
                    )
                }
                AnimatedVisibility(
                    visible = showBars && vm.document != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
                ) {
                    val counting = vm.paginationProgress
                    val pageLabel = when {
                        counting != null -> "페이지 계산 중 ${(counting * 100).toInt()}%"
                        vm.totalPages != null -> "Page ${vm.pageNumber(vm.charIndex)} / ${vm.totalPages}"
                        else -> ""
                    }
                    ReaderBottomBar(
                        progress = vm.progress,
                        marks = bookmarks.map { it.progress },
                        onSeek = { vm.seekToProgress(it, exact = false) },
                        pageLabel = pageLabel,
                        isCounting = counting != null,
                        searchBar = if (showSearch) {
                            {
                                val search = vm.search
                                ReaderSearchBar(
                                    query = searchInput,
                                    onQueryChange = { searchInput = it },
                                    onSubmit = {
                                        // Same query again → next match, as on iOS.
                                        if (search != null && search.query == searchInput) vm.stepSearch(1) else vm.search(searchInput)
                                        focusManager.clearFocus()
                                    },
                                    status = when {
                                        search == null -> ""
                                        search.matches.isEmpty() -> "No matches"
                                        else -> "${search.index + 1} / ${search.matches.size}${if (search.truncated) "+" else ""}"
                                    },
                                    isSearching = vm.isSearching,
                                    canStep = search != null && search.matches.isNotEmpty(),
                                    onPrevious = { vm.stepSearch(-1) },
                                    onNext = { vm.stepSearch(1) },
                                    onClear = {
                                        searchInput = ""
                                        vm.clearSearch()
                                    },
                                )
                            }
                        } else null,
                    ) {
                        BarButton(
                            if (ttsState.isPlaying) Icons.Filled.GraphicEq else Icons.Filled.PlayCircleOutline,
                            "Text to speech", { showTts = true }, active = ttsState.isActive,
                        )
                        BarButton(Icons.Filled.Search, "Search", {
                            showSearch = !showSearch
                            if (!showSearch) {
                                searchInput = ""
                                vm.clearSearch()
                            }
                        }, active = showSearch)
                        BarButton(Icons.AutoMirrored.Filled.ReceiptLong, "Auto-scroll", {
                            autoScrolling = !autoScrolling
                            // Get the bars out of the way of the text that is about to move.
                            if (autoScrolling && !highlightMode) showBars = false
                        }, active = autoScrolling)
                        BarButton(
                            Icons.Filled.BorderColor, "Highlight mode", { highlightMode = !highlightMode },
                            active = highlightMode, activeTint = Color(HighlightColor.YELLOW.argb),
                        )
                        BarButton(Icons.AutoMirrored.Filled.List, "Contents", { showContents = true })
                        Spacer(Modifier.weight(1f))
                        BarButton(Icons.Filled.TextFormat, "Appearance", { showAppearance = true })
                    }
                }
            }

        }

        if (showAppearance) {
            AppearanceSheet(
                settings = settings,
                textOptions = true,
                hasEmbeddedFont = vm.embeddedTypeface != null,
                onChange = vm::updateSettings,
                onDismiss = { showAppearance = false },
            )
        }
        if (showTts) {
            val requestNotifications = rememberNotificationPermissionRequest()
            TtsSheet(
                state = ttsState,
                onTogglePlayPause = {
                    if (!ttsState.isActive) requestNotifications()
                    vm.toggleTts()
                },
                onStop = vm::stopTts,
                onVoiceSelected = vm::setTtsVoice,
                onRateChange = vm::setTtsRate,
                onPitchChange = vm::setTtsPitch,
                onSleepTimerChange = vm::setTtsSleepTimer,
                onDismiss = { showTts = false },
            )
        }
        if (showContents) {
            ContentsPanel(
                chapters = vm.chapters,
                bookmarks = bookmarks,
                highlights = highlights,
                currentProgress = vm.progress,
                pageNumber = { p -> vm.pageNumber(vm.offsetOf(p)) },
                snippetFont = vm.embeddedTypeface?.takeIf { settings.useEmbeddedFont && vm.book?.format == BookFormat.EPUB }?.let { FontFamily(it) },
                onDismiss = { showContents = false },
                onJump = { p ->
                    showContents = false
                    vm.seekToProgress(p)
                },
                onJumpToHighlight = { h ->
                    showContents = false
                    vm.seekTo(h.location, exact = false)
                },
                onDeleteBookmark = vm::deleteBookmark,
                onDeleteHighlight = vm::deleteHighlight,
            )
        }
        pendingHighlight?.let { range ->
            HighlightColorDialog(
                onPick = { color ->
                    vm.addHighlight(range.first, range.last, color)
                    pendingHighlight = null
                },
                onDismiss = { pendingHighlight = null },
            )
        }
    }
}

@Composable
private fun HighlightColorDialog(onPick: (HighlightColor) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HighlightColor.entries.forEach { color ->
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(color.argb)).clickable { onPick(color) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Status-bar / navigation-bar icons that stay legible on the reading theme, restored when the reader closes. */
@Composable
fun SystemBarIcons(darkBackground: Boolean) {
    val view = LocalView.current
    DisposableEffect(darkBackground) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

/**
 * Asks for the notification permission (Android 13+) the first time speech
 * starts, so the playback controls can show. Speech works either way.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** [onStart] whenever the screen becomes visible, [onEnd] whenever it stops being so (or leaves the composition). */
@Composable
fun ReadingSession(onStart: () -> Unit, onEnd: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_STOP -> onEnd()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onEnd()
        }
    }
}

/** Page-coloured strips behind the status bar and the navigation bar. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.SystemBarScrims(color: Color) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top).background(color.copy(alpha = 0.94f)))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom).background(color.copy(alpha = 0.94f)))
}
