package com.qbitcore.booklip.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.data.ReadingStats
import com.qbitcore.booklip.data.ReadingStatsRepository
import com.qbitcore.booklip.model.Book

private class Stat(val title: String, val value: String, val icon: ImageVector, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(statsRepository: ReadingStatsRepository, books: List<Book>, onClose: () -> Unit) {
    val stats by statsRepository.stats.collectAsState(initial = ReadingStats())
    val seconds = stats.totalSeconds.toLong()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val cards = listOf(
        Stat("Time Read", if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m", Icons.Filled.Schedule, Color(0xFF007AFF)),
        Stat("Day Streak", "${stats.currentStreak}", Icons.Filled.LocalFireDepartment, Color(0xFFFF9500)),
        Stat("Books", "${books.size}", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF5856D6)),
        Stat("Reading", "${books.count { it.progress > 0 && it.progress < 0.95 }}", Icons.Filled.AutoStories, Color(0xFF30B0C7)),
        Stat("Finished", "${books.count { it.progress >= 0.95 }}", Icons.Filled.Verified, Color(0xFF34C759)),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Stats") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            items(cards) { stat ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(stat.tint.copy(alpha = 0.14f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(stat.icon, contentDescription = null, tint = stat.tint, modifier = Modifier.size(28.dp))
                    Text(stat.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stat.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
