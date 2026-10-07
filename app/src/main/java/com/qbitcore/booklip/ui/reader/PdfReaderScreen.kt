package com.qbitcore.booklip.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.settings.PageEffect
import com.qbitcore.booklip.ui.theme.BooklipTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

private const val RENDER_RADIUS = 2

@Composable
fun PdfReaderScreen(vm: PdfReaderViewModel, onClose: () -> Unit) {
    val settings = vm.settings.collectAsState().value
    if (settings == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val preset = settings.preset
    val bookmarks by vm.bookmarks.collectAsState()
    val ttsState by vm.ttsState.collectAsState()
    val focusManager = LocalFocusManager.current

    var showBars by rememberSaveable { mutableStateOf(true) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf("") }
    var showAppearance by remember { mutableStateOf(false) }
    var showTts by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    val pageCommands = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    ReadingSession(onStart = vm::onSessionStart, onEnd = vm::onSessionEnd)

    // Follow the page being read aloud.
    val spokenPage = vm.spokenPage(ttsState)
    LaunchedEffect(spokenPage) {
        if (spokenPage != null && spokenPage != vm.pageIndex) vm.seekToPage(spokenPage)
    }

    BooklipTheme(useDarkTheme = preset.isDark) {
        SystemBarIcons(darkBackground = preset.isDark)
        val barColors = remember(preset) { BarColors.of(Color(preset.background), Color(preset.text)) }
        CompositionLocalProvider(LocalBarColors provides barColors) {
            Box(Modifier.fillMaxSize().background(Color(preset.background))) {
                val error = vm.errorMessage
                when {
                    vm.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> EmptyState(Icons.Filled.Warning, "Cannot Open Book", error)
                    else -> {
                        val gestures = Modifier.readerGestures(
                            onTap = { x ->
                                when {
                                    showBars -> {
                                        showBars = false
                                        focusManager.clearFocus()
                                    }
                                    x < 0.3f -> pageCommands.tryEmit(-1)
                                    x > 0.7f -> pageCommands.tryEmit(1)
                                    else -> showBars = true
                                }
                            },
                            // The pager turns pages by swipe itself; the scrolling view pages like the text reader.
                            onSwipe = { step -> if (settings.pageEffect != PageEffect.PAPER) pageCommands.tryEmit(step) },
                        )
                        if (settings.pageEffect == PageEffect.PAPER) PdfPagedView(vm, pageCommands, gestures)
                        else PdfScrollView(vm, pageCommands, gestures)
                    }
                }

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
                    visible = showBars && vm.pageCount > 0,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
                ) {
                    ReaderBottomBar(
                        progress = vm.progress,
                        marks = bookmarks.map { it.progress },
                        onSeek = vm::seekToProgress,
                        pageLabel = "Page ${vm.pageIndex + 1} / ${vm.pageCount}",
                        isCounting = false,
                        searchBar = if (showSearch) {
                            {
                                val search = vm.search
                                ReaderSearchBar(
                                    query = searchInput,
                                    onQueryChange = { searchInput = it },
                                    onSubmit = {
                                        if (search != null && search.query == searchInput) vm.stepSearch(1) else vm.search(searchInput)
                                        focusManager.clearFocus()
                                    },
                                    status = when {
                                        search == null -> ""
                                        search.pages.isEmpty() -> "No matches"
                                        else -> "${search.index + 1} / ${search.pages.size}"
                                    },
                                    isSearching = vm.isSearching,
                                    canStep = search != null && search.pages.isNotEmpty(),
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
                        // Speech and search read the PDF's text layer, which the
                        // platform only exposes from Android 15.
                        if (vm.textSupported) {
                            if (vm.textReady) {
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
                            } else {
                                CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).then(Modifier.padding(4.dp)), strokeWidth = 2.dp)
                            }
                        }
                        BarButton(Icons.AutoMirrored.Filled.List, "Bookmarks", { showContents = true })
                        Spacer(Modifier.weight(1f))
                        BarButton(Icons.Filled.TextFormat, "Appearance", { showAppearance = true })
                    }
                }
            }

        }

        if (showAppearance) {
            AppearanceSheet(settings, textOptions = false, hasEmbeddedFont = false, onChange = vm::updateSettings, onDismiss = { showAppearance = false })
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
                chapters = emptyList(),
                bookmarks = bookmarks,
                highlights = null,
                currentProgress = vm.progress,
                pageNumber = { p -> vm.pageOf(p) + 1 },
                snippetFont = null,
                onDismiss = { showContents = false },
                onJump = { p ->
                    showContents = false
                    vm.seekToProgress(p)
                },
                onJumpToHighlight = {},
                onDeleteBookmark = vm::deleteBookmark,
                onDeleteHighlight = {},
            )
        }
    }
}

/** Paper Book: one page per screen, turned by swiping or tapping the edges. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfPagedView(vm: PdfReaderViewModel, pageCommands: Flow<Int>, modifier: Modifier) {
    val pagerState = rememberPagerState(initialPage = vm.pageIndex) { vm.pageCount }
    LaunchedEffect(vm.seek.token) {
        if (pagerState.currentPage != vm.pageIndex) pagerState.scrollToPage(vm.pageIndex)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.onPageChanged(it) }
    }
    LaunchedEffect(pagerState) {
        pageCommands.collect { step ->
            val target = (pagerState.currentPage + step).coerceIn(0, vm.pageCount - 1)
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        LaunchedEffect(pagerState, widthPx, vm.pageCount) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                vm.setVisibleWindow((page - RENDER_RADIUS).coerceAtLeast(0)..(page + RENDER_RADIUS).coerceAtMost(vm.pageCount - 1), widthPx)
            }
        }
        val insets = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondBoundsPageCount = 1) { page ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                PdfPage(vm.pageSizes[page], vm.bitmaps[page], vm.matchRects[page], Modifier)
            }
        }
    }
}

/** Vertical Slide: every page in one continuous scroll. */
@Composable
private fun PdfScrollView(vm: PdfReaderViewModel, pageCommands: Flow<Int>, modifier: Modifier) {
    val listState = remember { LazyListState(vm.pageIndex) }
    LaunchedEffect(vm.seek.token) {
        if (listState.firstVisibleItemIndex != vm.pageIndex) listState.scrollToItem(vm.pageIndex)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        LaunchedEffect(listState, widthPx, vm.pageCount) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.collect { visible ->
                if (visible.isEmpty()) return@collect
                vm.setVisibleWindow(
                    (visible.first() - RENDER_RADIUS).coerceAtLeast(0)..(visible.last() + RENDER_RADIUS).coerceAtMost(vm.pageCount - 1),
                    widthPx,
                )
            }
        }
        // The page being read is the one covering the middle of the screen.
        LaunchedEffect(listState, heightPx) {
            snapshotFlow {
                val middle = heightPx / 2
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset <= middle && it.offset + it.size > middle }?.index
            }.collect { page -> if (page != null) vm.onPageChanged(page) }
        }
        LaunchedEffect(listState, heightPx) {
            pageCommands.collect { step -> listState.animateScrollBy(step * heightPx * 0.85f) }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = vm.pageCount, key = { it }) { page ->
                PdfPage(vm.pageSizes[page], vm.bitmaps[page], vm.matchRects[page], Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PdfPage(size: PdfPageSize, bitmap: Bitmap?, matches: List<RectF>?, modifier: Modifier) {
    Box(modifier.aspectRatio(size.width.toFloat() / size.height).background(Color.White), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(color = Color.Gray, strokeWidth = 2.dp)
        }
        if (!matches.isNullOrEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                // Match rectangles are in PDF points, origin top-left.
                val scale = this.size.width / size.width
                for (rect in matches) {
                    drawRoundRect(
                        color = Color(0xFFFFD60A).copy(alpha = 0.45f),
                        topLeft = Offset(rect.left * scale, rect.top * scale),
                        size = Size(maxOf(rect.width() * scale, 4f), maxOf(rect.height() * scale, 4f)),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
        }
    }
}
