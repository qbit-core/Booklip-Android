package com.qbitcore.booklip.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qbitcore.booklip.settings.ReadingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** The text shown on one page: `[start, end)` of the book's text. */
private data class PageRange(val start: Int, val end: Int)

/**
 * "Paper Book": one page per screen, turned by tapping the edges or swiping.
 * A page ends with the last line that fits completely — a line the bottom
 * edge would cut becomes the first line of the next page.
 */
@Composable
fun PaperReader(
    vm: ReaderViewModel,
    doc: ReaderDocument,
    settings: ReadingSettings,
    typeface: android.graphics.Typeface,
    textColor: Int,
    decorations: Decorations,
    selectable: Boolean,
    autoScrolling: Boolean,
    onAutoScrollFinished: () -> Unit,
    pageCommands: Flow<Int>,
    actions: SelectionActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val spec = remember(typeface, settings.fontSize, settings.lineSpacing, size) {
        LayoutSpec(
            typeface = typeface,
            textSizePx = with(density) { settings.fontSize.sp.toPx() },
            lineSpacingPx = with(density) { settings.lineSpacing.dp.toPx() },
            widthPx = size.width,
            heightPx = size.height,
            density = density.density,
        )
    }
    var page by remember(doc) { mutableStateOf<PageRange?>(null) }
    // +1 / -1 while a turn animates in that direction, 0 for a jump.
    var turn by remember { mutableIntStateOf(0) }

    fun pageAt(start: Int) = PageRange(start, ReaderLayout.pageEnd(doc, start, spec))

    /** The page that contains [offset]: from the page table when there is one, else by paging from its paragraph. */
    fun pageContaining(offset: Int): PageRange {
        vm.pageStarts?.let { starts -> return pageAt(starts[indexOfLastAtMost(starts, offset)]) }
        val lineBreak = doc.text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0))
        var candidate = pageAt(ReaderLayout.skipLineBreaks(doc, if (offset == 0) 0 else lineBreak + 1).coerceAtMost(offset))
        repeat(MAX_PROBE_PAGES) {
            if (offset < candidate.end || candidate.end >= doc.length) return candidate
            candidate = pageAt(ReaderLayout.skipLineBreaks(doc, candidate.end))
        }
        return pageAt(offset)
    }

    fun nextPage(from: PageRange): PageRange? {
        val start = ReaderLayout.skipLineBreaks(doc, from.end)
        return if (start >= doc.length) null else pageAt(start)
    }

    fun previousPage(from: PageRange): PageRange? {
        if (from.start <= ReaderLayout.skipLineBreaks(doc, 0)) return null
        val starts = vm.pageStarts
        if (starts != null) {
            val index = indexOfLastAtMost(starts, from.start)
            if (index > 0 && starts[index] == from.start) return pageAt(starts[index - 1])
        }
        // Off the page grid (or no grid yet): build the page that ends where
        // this one starts, and end it there so turning forward returns here.
        val start = ReaderLayout.pageStartBefore(doc, from.start, spec)
        return PageRange(start, minOf(ReaderLayout.pageEnd(doc, start, spec), from.start))
    }

    fun show(target: PageRange, direction: Int) {
        turn = direction
        page = target
        vm.onPositionChanged(target.start)
    }

    LaunchedEffect(spec, doc) { vm.setLayoutSpec(spec) }

    // A seek, or a layout change (font, size, rotation): rebuild the page
    // around the current position.
    LaunchedEffect(vm.seek.token, spec) {
        if (!spec.isValid) return@LaunchedEffect
        val target = if (vm.seek.exact) pageAt(vm.charIndex) else pageContaining(vm.charIndex)
        turn = 0
        page = target
    }

    LaunchedEffect(spec) {
        pageCommands.collect { step ->
            val current = page ?: return@collect
            val target = if (step > 0) nextPage(current) else previousPage(current)
            if (target != null) show(target, step)
        }
    }

    // Auto-scroll turns the page after the time it takes to scroll one screen.
    val onFinished by rememberUpdatedState(onAutoScrollFinished)
    LaunchedEffect(autoScrolling, page, settings.autoScrollSpeed) {
        val current = page ?: return@LaunchedEffect
        if (!autoScrolling) return@LaunchedEffect
        val heightDp = spec.heightPx / density.density
        delay((heightDp / settings.autoScrollSpeed * 1000).toLong().coerceAtLeast(1500))
        val target = nextPage(current)
        if (target != null) show(target, 1) else onFinished()
    }

    // Follow the sentence being spoken onto the next page.
    val spokenStart = decorations.spoken?.start
    LaunchedEffect(spokenStart) {
        val offset = spokenStart ?: return@LaunchedEffect
        val current = page ?: return@LaunchedEffect
        if (offset in current.start until current.end) return@LaunchedEffect
        val next = nextPage(current)
        if (next != null && offset in next.start until next.end) show(next, 1)
        else show(pageContaining(offset), 0)
    }

    Box(modifier.fillMaxSize().padding(contentPadding)) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }.clipToBounds()) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val duration = 280
                    when {
                        turn > 0 -> slideInHorizontally(tween(duration)) { it } togetherWith slideOutHorizontally(tween(duration)) { -it }
                        turn < 0 -> slideInHorizontally(tween(duration)) { -it } togetherWith slideOutHorizontally(tween(duration)) { it }
                        else -> fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    }
                },
                label = "page",
            ) { shown ->
                if (shown != null) {
                    // Trailing line breaks would lay out as empty lines below the page.
                    var end = shown.end
                    while (end > shown.start && doc.text[end - 1] == '\n') end--
                    AndroidView(
                        factory = { newReaderTextView(it) },
                        update = { view -> view.bind(doc, shown.start, end, spec, textColor, selectable, decorations, actions) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

private const val MAX_PROBE_PAGES = 400
