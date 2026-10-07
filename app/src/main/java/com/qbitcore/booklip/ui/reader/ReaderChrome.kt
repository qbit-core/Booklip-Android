package com.qbitcore.booklip.ui.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Colours of the reader's bars, derived from the reading theme so they belong to the page. */
data class BarColors(val container: Color, val content: Color) {
    companion object {
        /**
         * The page colour nudged towards the text colour, slightly translucent
         * so the page stays faintly visible behind the bars, as on iOS.
         */
        fun of(background: Color, text: Color): BarColors =
            BarColors(lerp(background, text, 0.07f).copy(alpha = 0.97f), text)
    }
}

val LocalBarColors = staticCompositionLocalOf<BarColors?> { null }

@Composable
private fun barColors(): BarColors = LocalBarColors.current
    ?: BarColors(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.96f), MaterialTheme.colorScheme.onSurface)

@Composable
fun ReaderTopBar(
    title: String,
    author: String,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = barColors()
    Surface(color = colors.container, contentColor = colors.content, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (author.isNotBlank() && author != "Unknown") {
                    Text(
                        author,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
fun ReaderBottomBar(
    progress: Double,
    marks: List<Double>,
    onSeek: (Double) -> Unit,
    /** "Page 3 / 120", or a note while pages are being counted. Empty hides the line. */
    pageLabel: String,
    isCounting: Boolean,
    modifier: Modifier = Modifier,
    searchBar: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = barColors()
    Surface(color = colors.container, contentColor = colors.content, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            searchBar?.invoke()
            ReadingProgressBar(progress, marks, onSeek, Modifier.padding(horizontal = 16.dp).padding(top = 6.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pageLabel.isNotEmpty()) {
                    Text(
                        pageLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (isCounting) FontWeight.Normal else FontWeight.Medium,
                        color = LocalContentColor.current.copy(alpha = if (isCounting) 0.65f else 1f),
                    )
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = LocalContentColor.current.copy(alpha = 0.65f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false, activeTint: Color? = null) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) activeTint ?: MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/**
 * Reading progress with bookmark ticks. Dragging only moves the thumb; the
 * book seeks once, when the finger lifts.
 */
@Composable
fun ReadingProgressBar(progress: Double, marks: List<Double>, onSeek: (Double) -> Unit, modifier: Modifier = Modifier) {
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val shown = dragProgress ?: progress.toFloat()
    val dragging = dragProgress != null
    val trackHeight by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "track")
    val thumbRadius by animateDpAsState(if (dragging) 10.dp else 0.dp, label = "thumb")
    val track = LocalContentColor.current.copy(alpha = 0.18f)
    val fill = MaterialTheme.colorScheme.primary
    val seek by rememberUpdatedState(onSeek)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f).toDouble()) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragProgress = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragProgress?.let { seek(it.toDouble()) }
                        dragProgress = null
                    },
                    onDragCancel = { dragProgress = null },
                ) { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        val h = trackHeight.toPx()
        val y = (size.height - h) / 2
        val radius = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), radius)
        drawRoundRect(fill, Offset(0f, y), Size(size.width * shown.coerceIn(0f, 1f), h), radius)
        val tick = Size(3.dp.toPx(), 12.dp.toPx())
        for (mark in marks) {
            val x = size.width * mark.toFloat().coerceIn(0f, 1f) - tick.width / 2
            drawRoundRect(BOOKMARK_TICK, Offset(x, (size.height - tick.height) / 2), tick, CornerRadius(1.dp.toPx()))
        }
        if (thumbRadius > 0.dp) {
            drawCircle(fill, thumbRadius.toPx(), Offset(size.width * shown.coerceIn(0f, 1f), size.height / 2))
        }
    }
}

private val BOOKMARK_TICK = Color(0xFFFF9500)

@Composable
fun ReaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    /** "3 / 17", "No matches", … */
    status: String,
    isSearching: Boolean,
    canStep: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClear: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search in book…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = LocalContentColor.current,
                unfocusedTextColor = LocalContentColor.current,
                focusedLeadingIconColor = LocalContentColor.current.copy(alpha = 0.65f),
                unfocusedLeadingIconColor = LocalContentColor.current.copy(alpha = 0.65f),
                focusedPlaceholderColor = LocalContentColor.current.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = LocalContentColor.current.copy(alpha = 0.5f),
            ),
            modifier = Modifier.weight(1f),
        )
        if (isSearching) {
            CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).width(18.dp).height(18.dp), strokeWidth = 2.dp)
        } else if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = LocalContentColor.current.copy(alpha = 0.65f),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onPrevious, enabled = canStep) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match") }
            IconButton(onClick = onNext, enabled = canStep) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match") }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Cancel, contentDescription = "Clear search", tint = LocalContentColor.current.copy(alpha = 0.65f))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * Taps and horizontal swipes on the reading surface.
 *
 * [onTap] gets the tap's x as a fraction of the width. A touch that a child
 * handled — a scroll, or anything on a selectable text view in highlight
 * mode — is neither: the reader must not turn a page while text is being
 * selected. [onSwipe] gets +1 for a right-to-left swipe (next page), -1 for
 * left-to-right.
 */
fun Modifier.readerGestures(onTap: (Float) -> Unit, onSwipe: (Int) -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)
    val slop = LocalViewConfiguration.current.touchSlop
    pointerInput(Unit) {
        val swipeDistance = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val handledByChild = down.isConsumed
            var interrupted = false
            var end: Offset? = null
            var upTime = 0L
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    interrupted = true
                    break
                }
                if (event.changes.size > 1) interrupted = true
                if (change.changedToUpIgnoreConsumed()) {
                    end = change.position
                    upTime = change.uptimeMillis
                    break
                }
                // Someone else took the drag: the list is scrolling, or a
                // selection handle is being moved.
                if (change.isConsumed) interrupted = true
            }
            val up = end
            if (interrupted || up == null) return@awaitEachGesture
            val dx = up.x - down.position.x
            val dy = up.y - down.position.y
            if (abs(dx) > swipeDistance && abs(dx) > abs(dy) * 1.5f) {
                swipe(if (dx < 0) 1 else -1)
            } else if (!handledByChild && abs(dx) < slop && abs(dy) < slop && upTime - down.uptimeMillis < 400) {
                tap((down.position.x / size.width).coerceIn(0f, 1f))
            }
        }
    }
}
