package com.qbitcore.booklip.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reading_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("fontSize")
        val LINE_SPACING = floatPreferencesKey("lineSpacing")
        val PRESET = stringPreferencesKey("presetId")
    }

    val settings: Flow<ReadingSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (ReadingSettings) -> ReadingSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.FONT] = updated.fontFamily.name
            prefs[Keys.FONT_SIZE] = updated.fontSize
            prefs[Keys.LINE_SPACING] = updated.lineSpacing
            prefs[Keys.PRESET] = updated.presetId
        }
    }

    private fun Preferences.toSettings(): ReadingSettings = ReadingSettings(
        fontFamily = this[Keys.FONT]?.let { name -> ReaderFont.entries.firstOrNull { it.name == name } }
            ?: ReaderFont.SERIF,
        fontSize = this[Keys.FONT_SIZE] ?: 18f,
        lineSpacing = this[Keys.LINE_SPACING] ?: 8f,
        presetId = this[Keys.PRESET] ?: "default",
    )
}
