package com.qbitcore.booklip.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.model.Book
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A book in the grid. Every card is the same size whatever the cover's shape:
 * a 2:3 cover on top and a fixed strip with a one-line title and author.
 */
@Composable
fun BookCard(book: Book, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
                BookCover(book, showTitle = true, modifier = Modifier.fillMaxSize())
                Text(
                    book.format.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp).height(46.dp)) {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.progress > 0) {
                    Spacer(Modifier.height(4.dp))
                    BookProgress(book.progress)
                }
            }
        }
    }
}

/** A book in list view: thumbnail, title, author, progress. */
@Composable
fun BookRow(book: Book, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookCover(book, showTitle = false, modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.progress > 0) BookProgress(book.progress)
        }
        Text(
            "${(book.progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookProgress(progress: Double) {
    LinearProgressIndicator(
        progress = { progress.toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
    )
}

/** The book's cover image, or a coloured placeholder (with the title on grid cards, the format's initial in rows). */
@Composable
fun BookCover(book: Book, showTitle: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as BooklipApplication
    val cover by produceState<Bitmap?>(initialValue = book.coverFileName?.let { CoverCache.peek(it) }, book.coverFileName) {
        val name = book.coverFileName
        value = if (name == null) null else CoverCache.peek(name) ?: withContext(Dispatchers.IO) {
            CoverCache.load(name, app.repository.coverFile(name))
        }
    }
    val bitmap = cover
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = book.title, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(placeholderColor(book.title))) {
            if (showTitle) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(12.dp),
                )
            } else {
                Text(
                    book.format.displayName.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private val PLACEHOLDER_COLORS = listOf(
    Color(0xFF5856D6), Color(0xFF30B0C7), Color(0xFFFF9500), Color(0xFFFF2D55),
    Color(0xFFAF52DE), Color(0xFF34C759), Color(0xFF007AFF),
)

private fun placeholderColor(title: String): Color = PLACEHOLDER_COLORS[Math.floorMod(title.hashCode(), PLACEHOLDER_COLORS.size)]

/** Cover thumbnails, decoded off the main thread at grid size rather than at full resolution. */
private object CoverCache {
    private const val TARGET_WIDTH = 360
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(name: String): Bitmap? = cache.get(name)

    fun load(name: String, file: File): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(name, bitmap)
        return bitmap
    }
}
