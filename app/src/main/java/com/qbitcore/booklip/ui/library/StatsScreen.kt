package com.qbitcore.booklip.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.data.ReadingStats
import com.qbitcore.booklip.data.ReadingStatsRepository
import com.qbitcore.booklip.model.Book

private data class StatCard(val title: String, val value: String, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(statsRepository: ReadingStatsRepository, books: List<Book>, onClose: () -> Unit) {
    val stats by statsRepository.stats.collectAsState(initial = ReadingStats())

    val total = books.size
    val finished = books.count { it.progress >= 0.95 }
    val reading = books.count { it.progress > 0.0 && it.progress < 0.95 }
    val totalSeconds = stats.totalSeconds.toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val timeLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val cards = listOf(
        StatCard("Time Read", timeLabel, Color(0xFF3A5A9F)),
        StatCard("Day Streak", "${stats.currentStreak}", Color(0xFFE08A2E)),
        StatCard("Books", "$total", Color(0xFF5A4FCF)),
        StatCard("Reading", "$reading", Color(0xFF2E9E8F)),
        StatCard("Finished", "$finished", Color(0xFF3E9E4F)),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Stats") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            items(cards) { card -> StatCardView(card) }
        }
    }
}

@Composable
private fun StatCardView(card: StatCard) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(card.tint.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(card.value, style = MaterialTheme.typography.headlineMedium, color = card.tint)
        Text(card.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
