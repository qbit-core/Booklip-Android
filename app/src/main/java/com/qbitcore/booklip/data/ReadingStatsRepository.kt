package com.qbitcore.booklip.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.statsDataStore by preferencesDataStore(name = "reading_stats")

data class ReadingStats(
    val totalSeconds: Double = 0.0,
    val readingDays: Set<String> = emptySet(),
) {
    /** Consecutive-day reading streak ending today (or yesterday, so a session in progress still counts). */
    val currentStreak: Int
        get() {
            if (readingDays.isEmpty()) return 0
            val calendar = java.util.Calendar.getInstance()
            var streak = 0
            if (!readingDays.contains(dayKey(calendar.time))) calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            while (readingDays.contains(dayKey(calendar.time))) {
                streak++
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            return streak
        }
}

private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun dayKey(date: Date): String = dayFormat.format(date)

class ReadingStatsRepository(private val context: Context) {
    private object Keys {
        val TOTAL_SECONDS = doublePreferencesKey("totalSeconds")
        val READING_DAYS = stringSetPreferencesKey("readingDays")
    }

    val stats: Flow<ReadingStats> = context.statsDataStore.data.map { prefs ->
        ReadingStats(
            totalSeconds = prefs[Keys.TOTAL_SECONDS] ?: 0.0,
            readingDays = prefs[Keys.READING_DAYS] ?: emptySet(),
        )
    }

    /** Record a reading session of [seconds] and mark today as a reading day. */
    suspend fun record(seconds: Double) {
        if (seconds <= 1) return
        context.statsDataStore.edit { prefs ->
            val total = (prefs[Keys.TOTAL_SECONDS] ?: 0.0) + seconds
            prefs[Keys.TOTAL_SECONDS] = total
            val days = (prefs[Keys.READING_DAYS] ?: emptySet()).toMutableSet()
            days += dayKey(Date())
            prefs[Keys.READING_DAYS] = days
        }
    }
}
