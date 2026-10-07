package com.qbitcore.booklip.ui.reader

import android.widget.TextView
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qbitcore.booklip.settings.ReadingSettings
import kotlinx.coroutines.flow.Flow

/**
 * "Vertical Slide": the whole book as one continuous scroll. The text is laid
 * out lazily, one block ([ReaderDocument.segmentStarts]) per list item, so a
 * multi-megabyte book opens instantly and never lays out more than a few
 * screens of text.
 */
@Composable
fun ScrollReader(
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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val topPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
        val bottomPx = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
        val sidePx = with(density) {
            contentPadding.calculateStartPadding(direction).roundToPx() + contentPadding.calculateEndPadding(direction).roundToPx()
        }
        val spacingPx = with(density) { settings.lineSpacing.dp.toPx() }
        val spec = remember(typeface, settings.fontSize, settings.lineSpacing, constraints.maxWidth, constraints.maxHeight, sidePx, topPx, bottomPx) {
            LayoutSpec(
                typeface = typeface,
                textSizePx = with(density) { settings.fontSize.sp.toPx() },
                lineSpacingPx = spacingPx,
                widthPx = constraints.maxWidth - sidePx,
                heightPx = constraints.maxHeight - topPx - bottomPx,
                density = density.density,
            )
        }
        LaunchedEffect(spec, doc) { vm.setLayoutSpec(spec) }

        val listState = remember(doc) { LazyListState(doc.segmentIndex(vm.charIndex)) }
        val views = remember(doc) { HashMap<Int, TextView>() }
        // False until the first seek has landed: the list's provisional
        // position must not be reported back as the reading position.
        var settled by remember(doc) { mutableStateOf(false) }

        fun blockOffsetPx(index: Int, offset: Int): Int =
            ReaderLayout.lineTopInBlock(doc, doc.segmentStarts[index], displayEnd(doc, index), offset, spec)

        // A seek (restore, contents, search, progress bar) or a layout change
        // (font, size, rotation): put the line holding the position at the top.
        LaunchedEffect(vm.seek.token, spec) {
            // Until the list has been laid out at the new place (and, after a
            // font change, with the new metrics), what it reports is not where
            // the reader is.
            settled = false
            val offset = vm.charIndex
            val index = doc.segmentIndex(offset)
            listState.scrollToItem(index, blockOffsetPx(index, offset))
            withFrameNanos { }
            settled = true
        }

        LaunchedEffect(listState, spec) {
            snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, settled) }
                .collect { (index, scroll, isSettled) ->
                    if (!isSettled) return@collect
                    val start = doc.segmentStarts.getOrNull(index) ?: return@collect
                    val layout = views[index]?.layout
                    if (layout == null) {
                        vm.onPositionChanged(start)
                    } else {
                        // The first line that is fully below the top edge.
                        var line = layout.getLineForVertical(scroll)
                        if (layout.getLineTop(line) < scroll - 1 && line + 1 < layout.lineCount) line++
                        vm.onPositionChanged(start + layout.getLineStart(line))
                    }
                }
        }

        // Edge taps: one screen up / down, less a line so the eye keeps its place.
        LaunchedEffect(listState, spec) {
            pageCommands.collect { step ->
                val distance = (spec.heightPx - spec.textSizePx * 1.5f - spec.lineSpacingPx).coerceAtLeast(spec.heightPx * 0.5f)
                listState.animateScrollBy(step * distance)
            }
        }

        val speedPx = with(density) { settings.autoScrollSpeed.dp.toPx() }
        val onFinished by rememberUpdatedState(onAutoScrollFinished)
        LaunchedEffect(autoScrolling, speedPx, listState) {
            if (!autoScrolling) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val delta = speedPx * (now - last) / 1_000_000_000f
                last = now
                // Raw deltas don't take the scroll mutex, so a finger on the
                // screen can still scroll while this runs.
                listState.dispatchRawDelta(delta)
                if (!listState.canScrollForward) {
                    onFinished()
                    break
                }
            }
        }

        // Keep the sentence being spoken on screen.
        val spoken = decorations.spoken
        LaunchedEffect(spoken?.start, listState, spec) {
            val start = spoken?.start ?: return@LaunchedEffect
            if (!settled || listState.isScrollInProgress) return@LaunchedEffect
            val index = doc.segmentIndex(start)
            val lineTop = blockOffsetPx(index, start)
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val y = item?.let { it.offset + lineTop }
            val lineHeight = spec.textSizePx * 1.3f + spec.lineSpacingPx
            if (y == null || y < 0 || y + lineHeight * 2 > spec.heightPx) {
                // A little below the top edge, so the previous line stays visible.
                val lead = (spec.heightPx * 0.12f).toInt()
                if (lineTop >= lead) listState.animateScrollToItem(index, lineTop - lead)
                else listState.animateScrollToItem(index, lineTop)
            }
        }

        val bottomGap = with(density) { settings.lineSpacing.dp }
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            items(count = doc.segmentStarts.size, key = { it }) { index ->
                val start = doc.segmentStarts[index]
                val end = displayEnd(doc, index)
                AndroidView(
                    factory = { newReaderTextView(it) },
                    update = { view ->
                        views[index] = view
                        view.bind(doc, start, end, spec, textColor, selectable, decorations, actions)
                    },
                    onRelease = { view -> views.remove(index, view) },
                    // Line spacing is not added after a view's last line; keep
                    // the gap between blocks equal to the gap between lines.
                    modifier = Modifier.fillMaxWidth().padding(bottom = bottomGap),
                )
            }
        }
    }
}

/** A block's text ends before the line break that separates it from the next block. */
private fun displayEnd(doc: ReaderDocument, index: Int): Int {
    val start = doc.segmentStarts[index]
    val end = doc.segmentEnd(index)
    return if (end > start && doc.text[end - 1] == '\n') end - 1 else end
}
