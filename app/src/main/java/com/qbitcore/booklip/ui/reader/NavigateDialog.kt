package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.model.Bookmark
import com.qbitcore.booklip.model.Chapter
import com.qbitcore.booklip.model.Highlight
import java.text.DateFormat
import java.util.Date

private val tabTitles = listOf("Contents", "Bookmarks", "Highlights")

@Composable
fun NavigateDialog(
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    onDismiss: () -> Unit,
    onJumpToProgress: (Double) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Navigate")
                TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                    }
                }
            }
        },
        text = {
            Box(modifier = Modifier.heightIn(min = 120.dp, max = 420.dp)) {
                when (tab) {
                    0 -> ContentsList(chapters, onJumpToProgress)
                    1 -> BookmarksList(bookmarks, onJumpToProgress, onDeleteBookmark)
                    else -> HighlightsList(highlights, onJumpToProgress, onDeleteHighlight)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ContentsList(chapters: List<Chapter>, onJump: (Double) -> Unit) {
    if (chapters.isEmpty()) {
        Text("This book has no table of contents.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn {
        items(chapters) { chapter ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = (chapter.level * 16).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onJump(chapter.progress) }, modifier = Modifier.weight(1f)) {
                    Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${(chapter.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookmarksList(bookmarks: List<Bookmark>, onJump: (Double) -> Unit, onDelete: (Bookmark) -> Unit) {
    if (bookmarks.isEmpty()) {
        Text(
            "No bookmarks yet. Tap the bookmark button while reading to add one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn {
        items(bookmarks, key = { it.id }) { mark ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onJump(mark.progress) }, modifier = Modifier.weight(1f)) {
                    Column {
                        Text(mark.snippet.ifBlank { "Bookmark" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${(mark.progress * 100).toInt()}%  ·  ${formatDate(mark.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onDelete(mark) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                }
            }
        }
    }
}

@Composable
private fun HighlightsList(
    highlights: List<Highlight>,
    onJump: (Double) -> Unit,
    onDelete: (Highlight) -> Unit,
) {
    if (highlights.isEmpty()) {
        Text(
            "No highlights yet. Long-press a paragraph and choose a color.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn {
        items(highlights, key = { it.id }) { highlight ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onJump(highlight.progress) }, modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, end = 8.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(highlight.color.argb)),
                        )
                        Column {
                            Text(highlight.snippet, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${(highlight.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = { onDelete(highlight) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete highlight")
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
