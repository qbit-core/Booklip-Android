package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.model.Highlight
import java.text.DateFormat
import java.util.Date

private enum class ContentsTab(val label: String) { CONTENTS("Contents"), BOOKMARKS("Bookmarks"), HIGHLIGHTS("Highlights") }

/** Table of contents / bookmarks / highlights — the iOS "Navigate" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsPanel(
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    /** null hides the Highlights tab (PDF). */
    highlights: List<Highlight>?,
    currentProgress: Double,
    /** 1-based page number for a progress value, when page numbers are known. */
    pageNumber: (Double) -> Int?,
    /** Books with a scrambled anti-copy font are only legible in it — snippets included. */
    snippetFont: FontFamily?,
    onDismiss: () -> Unit,
    onJump: (Double) -> Unit,
    onJumpToHighlight: (Highlight) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ContentsTab.CONTENTS) }
    val tabs = ContentsTab.entries.filter { it != ContentsTab.HIGHLIGHTS || highlights != null }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                tabs.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = tab == item,
                        onClick = { tab = item },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        icon = {},
                    ) { Text(item.label, maxLines = 1) }
                }
            }
            when (tab) {
                ContentsTab.CONTENTS -> ChapterList(chapters, currentProgress, onJump)
                ContentsTab.BOOKMARKS -> BookmarkList(bookmarks, pageNumber, snippetFont, onJump, onDeleteBookmark)
                ContentsTab.HIGHLIGHTS -> HighlightList(highlights.orEmpty(), snippetFont, onJumpToHighlight, onDeleteHighlight)
            }
        }
    }
}

@Composable
private fun ChapterList(chapters: List<Chapter>, currentProgress: Double, onJump: (Double) -> Unit) {
    if (chapters.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.List, "No Chapters", "This book has no table of contents.")
        return
    }
    // The chapter being read: the last one that starts at or before the position.
    val current = chapters.indexOfLast { it.progress <= currentProgress + 1e-9 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(chapters) { index, chapter ->
            val isCurrent = index == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onJump(chapter.progress) }
                    .padding(start = (20 + chapter.level * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chapter.title,
                    style = if (chapter.level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        chapter.level == 0 -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(chapter.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<Bookmark>,
    pageNumber: (Double) -> Int?,
    snippetFont: FontFamily?,
    onJump: (Double) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        EmptyState(Icons.Filled.Bookmark, "No Bookmarks", "Tap the bookmark button while reading to add one.")
        return
    }
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    LazyColumn(Modifier.fillMaxSize()) {
        items(bookmarks, key = { it.id }) { mark ->
            Row(
                Modifier.fillMaxWidth().clickable { onJump(mark.progress) }.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        mark.snippet.ifEmpty { "Bookmark" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = snippetFont,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val page = pageNumber(mark.progress)?.let { "p. $it  ·  " }.orEmpty()
                    Text(
                        "$page${(mark.progress * 100).toInt()}%  ·  ${dateFormat.format(Date(mark.date))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(mark) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun HighlightList(
    highlights: List<Highlight>,
    snippetFont: FontFamily?,
    onJump: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit,
) {
    if (highlights.isEmpty()) {
        EmptyState(Icons.Filled.BorderColor, "No Highlights", "Turn on the highlighter, select text, and choose a color.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(highlights, key = { it.id }) { highlight ->
            Row(
                Modifier.fillMaxWidth().clickable { onJump(highlight) }.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(highlight.color.argb).copy(alpha = 0.7f)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        highlight.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = snippetFont,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${(highlight.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(highlight) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete highlight", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
