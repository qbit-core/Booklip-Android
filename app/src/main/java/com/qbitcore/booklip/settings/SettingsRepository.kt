package com.qbitcore.booklip.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbitcore.booklip.model.SortOption
import com.qbitcore.booklip.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reading_settings")

data class LibraryPrefs(
    val sortOption: SortOption = SortOption.DATE_ADDED,
    val viewMode: ViewMode = ViewMode.MEDIUM_GRID,
)

data class TtsPrefs(val voiceName: String? = null, val rate: Float = 1f, val pitch: Float = 1f)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("fontSize")
        val LINE_SPACING = floatPreferencesKey("lineSpacing")
        val PRESET = stringPreferencesKey("presetId")
        val PAGE_EFFECT = stringPreferencesKey("pageEffect")
        val USE_EMBEDDED_FONT = booleanPreferencesKey("useEmbeddedFont")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("autoScrollSpeed")
        val SORT = stringPreferencesKey("sortOption")
        val VIEW_MODE = stringPreferencesKey("viewMode")
        val TTS_VOICE = stringPreferencesKey("ttsVoice")
        val TTS_RATE = floatPreferencesKey("ttsRate")
        val TTS_PITCH = floatPreferencesKey("ttsPitch")
    }

    val settings: Flow<ReadingSettings> = context.dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    val libraryPrefs: Flow<LibraryPrefs> = context.dataStore.data.map { prefs ->
        LibraryPrefs(
            sortOption = enumOf(prefs[Keys.SORT], SortOption.DATE_ADDED),
            viewMode = enumOf(prefs[Keys.VIEW_MODE], ViewMode.MEDIUM_GRID),
        )
    }.distinctUntilChanged()

    val ttsPrefs: Flow<TtsPrefs> = context.dataStore.data.map { prefs ->
        TtsPrefs(prefs[Keys.TTS_VOICE], prefs[Keys.TTS_RATE] ?: 1f, prefs[Keys.TTS_PITCH] ?: 1f)
    }.distinctUntilChanged()

    suspend fun update(transform: (ReadingSettings) -> ReadingSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.FONT] = s.font.name
            prefs[Keys.FONT_SIZE] = s.fontSize
            prefs[Keys.LINE_SPACING] = s.lineSpacing
            prefs[Keys.PRESET] = s.presetId
            prefs[Keys.PAGE_EFFECT] = s.pageEffect.name
            prefs[Keys.USE_EMBEDDED_FONT] = s.useEmbeddedFont
            prefs[Keys.AUTO_SCROLL_SPEED] = s.autoScrollSpeed
        }
    }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { it[Keys.SORT] = option.name }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setTtsPrefs(prefs: TtsPrefs) {
        context.dataStore.edit {
            if (prefs.voiceName != null) it[Keys.TTS_VOICE] = prefs.voiceName else it.remove(Keys.TTS_VOICE)
            it[Keys.TTS_RATE] = prefs.rate
            it[Keys.TTS_PITCH] = prefs.pitch
        }
    }

    private fun Preferences.toSettings(): ReadingSettings = ReadingSettings(
        font = enumOf(this[Keys.FONT], ReaderFont.SERIF),
        fontSize = (this[Keys.FONT_SIZE] ?: 18f).coerceIn(ReadingSettings.FONT_SIZE_RANGE),
        lineSpacing = (this[Keys.LINE_SPACING] ?: 8f).coerceIn(ReadingSettings.LINE_SPACING_RANGE),
        presetId = this[Keys.PRESET] ?: "default",
        pageEffect = enumOf(this[Keys.PAGE_EFFECT], PageEffect.VERTICAL_SLIDE),
        useEmbeddedFont = this[Keys.USE_EMBEDDED_FONT] ?: true,
        autoScrollSpeed = (this[Keys.AUTO_SCROLL_SPEED] ?: 40f).coerceIn(ReadingSettings.AUTO_SCROLL_RANGE),
    )

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
