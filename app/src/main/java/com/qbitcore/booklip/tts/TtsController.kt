package com.qbitcore.booklip.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.qbitcore.booklip.parser.ParsedBook
import com.qbitcore.booklip.settings.SettingsRepository
import com.qbitcore.booklip.settings.TtsPrefs
import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Half-open character range `[start, end)` in the book's text. */
data class CharRange(val start: Int, val end: Int) {
    val length: Int get() = end - start
    operator fun contains(index: Int) = index in start until end
}

data class TtsVoice(val name: String, val label: String)

data class TtsState(
    val isReady: Boolean = false,
    /** The device has no usable text-to-speech engine. */
    val unavailable: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    val voices: List<TtsVoice> = emptyList(),
    /** null = automatic: Korean voice for Korean text, English otherwise. */
    val selectedVoiceName: String? = null,
    val sleepMinutes: Int? = null,
    /** The sentence being spoken, in the book's text. null when stopped. */
    val spokenRange: CharRange? = null,
    val title: String = "Booklip",
    val author: String = "",
) {
    val isActive: Boolean get() = isPlaying || isPaused
}

/**
 * App-wide text-to-speech player (the counterpart of the iOS TTSManager).
 *
 * Text is spoken in short chunks — whole sentences packed up to
 * [MAX_CHUNK_LENGTH] — so the highlight stays close to the audio and a rate or
 * voice change takes effect quickly. A sentence is never cut; only text with
 * no sentence punctuation for [MAX_SENTENCE_LENGTH] characters is split, at
 * whitespace. A couple of chunks are always queued ahead so playback is
 * gapless. [TtsPlaybackService] keeps the process alive and shows the media
 * notification while this is active.
 *
 * `TextToSpeech` cannot pause in place, so [pause] stops the engine and
 * [resume] restarts at the sentence that was being spoken.
 */
class TtsController(context: Context, private val settings: SettingsRepository) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state

    private var engine: TextToSpeech? = null
    private var engineVoices: List<Voice> = emptyList()
    private var sleepJob: Job? = null

    private var text: String = ""
    /** Bumped on every (re)start so callbacks from a flushed queue are ignored. */
    private var generation = 0
    private val queue = ArrayDeque<Chunk>()
    /** Where the next chunk to enqueue starts. */
    private var cursor = 0
    /** Where [resume] restarts. */
    private var resumeOffset = 0
    private var resumeOnFocusGain = false
    private var noisyRegistered = false

    private class Chunk(val id: String, val range: CharRange, val sentences: List<CharRange>)

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AUDIO_ATTRIBUTES)
        .setOnAudioFocusChangeListener({ change -> main.post { onFocusChange(change) } }, main)
        .build()

    // Headphones unplugged: don't carry on through the speaker.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (_state.value.isPlaying) pause()
        }
    }

    /** Starts the engine if needed. Cheap to call repeatedly. */
    fun prepare() {
        if (engine != null) return
        scope.launch {
            val prefs = settings.ttsPrefs.first()
            _state.update { it.copy(rate = prefs.rate, pitch = prefs.pitch, selectedVoiceName = prefs.voiceName) }
        }
        engine = TextToSpeech(appContext) { status ->
            main.post {
                val tts = engine ?: return@post
                if (status != TextToSpeech.SUCCESS) {
                    _state.update { it.copy(unavailable = true) }
                    return@post
                }
                tts.setOnUtteranceProgressListener(listener)
                tts.setAudioAttributes(AUDIO_ATTRIBUTES)
                refreshVoices()
                _state.update { it.copy(isReady = true, unavailable = false) }
            }
        }
    }

    private fun refreshVoices() {
        val all = runCatching { engine?.voices }.getOrNull().orEmpty()
        // English and Korean, like the iOS app; offline voices only.
        engineVoices = all.filter { voice ->
            val tag = voice.locale.toLanguageTag().lowercase()
            (tag.startsWith("en-us") || tag.startsWith("ko")) && !voice.isNetworkConnectionRequired &&
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }.sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
        val counters = HashMap<String, Int>()
        val voices = engineVoices.map { voice ->
            val language = voice.locale.getDisplayName(Locale.getDefault())
            val n = counters.merge(language, 1, Int::plus)
            TtsVoice(voice.name, "$language · Voice $n")
        }
        _state.update { s ->
            s.copy(voices = voices, selectedVoiceName = s.selectedVoiceName?.takeIf { name -> voices.any { it.name == name } })
        }
    }

    /** What the media notification / lock screen shows while speaking. */
    fun setNowPlaying(title: String, author: String) {
        _state.update { it.copy(title = title, author = author) }
    }

    fun togglePlayPause(text: String, currentOffset: Int) {
        val s = _state.value
        when {
            s.isPlaying -> pause()
            s.isPaused && this.text === text -> resume()
            else -> speak(text, currentOffset)
        }
    }

    fun speak(text: String, from: Int) {
        prepare()
        this.text = text
        start(from.coerceIn(0, text.length))
    }

    private fun start(offset: Int) {
        val tts = engine ?: return
        if (!_state.value.isReady) {
            // Engine still binding: try again shortly.
            main.postDelayed({ if (text.isNotEmpty() && !_state.value.unavailable) start(offset) }, 200)
            return
        }
        generation++
        queue.clear()
        tts.stop()
        cursor = offset
        resumeOffset = offset
        audioManager.requestAudioFocus(focusRequest)
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(
                appContext, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
        fillQueue(flush = true)
        if (queue.isEmpty()) {
            stop()
            return
        }
        _state.update { it.copy(isPlaying = true, isPaused = false) }
        TtsPlaybackService.start(appContext)
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        generation++
        queue.clear()
        engine?.stop()
        _state.update { it.copy(isPlaying = false, isPaused = true) }
    }

    fun resume() {
        if (!_state.value.isPaused || text.isEmpty()) return
        start(resumeOffset)
    }

    fun stop() {
        generation++
        queue.clear()
        engine?.stop()
        text = ""
        resumeOnFocusGain = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (noisyRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        _state.update { it.copy(isPlaying = false, isPaused = false, spokenRange = null) }
    }

    fun setRate(rate: Float) = applyVoiceChange { it.copy(rate = rate) }
    fun setPitch(pitch: Float) = applyVoiceChange { it.copy(pitch = pitch) }
    fun setVoice(name: String?) = applyVoiceChange { it.copy(selectedVoiceName = name) }

    private fun applyVoiceChange(change: (TtsState) -> TtsState) {
        _state.update(change)
        val s = _state.value
        scope.launch { settings.setTtsPrefs(TtsPrefs(s.selectedVoiceName, s.rate, s.pitch)) }
        // Already-queued utterances carry the old parameters: requeue from the
        // sentence being spoken so the change is heard right away.
        if (s.isPlaying) start(resumeOffset)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _state.update { it.copy(sleepMinutes = minutes) }
        if (minutes == null) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            stop()
            _state.update { it.copy(sleepMinutes = null) }
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (_state.value.isPlaying) pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // A call or another app's prompt: pause, and pick up again after.
                if (_state.value.isPlaying) {
                    resumeOnFocusGain = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                resumeOnFocusGain = false
                resume()
            }
        }
    }

    // MARK: - Chunking

    /** The next chunk at or after [from], or null at the end of the text. */
    private fun nextChunkRange(from: Int): CharRange? {
        var start = from
        while (start < text.length && isSkippable(text[start])) start++
        if (start >= text.length) return null
        var paragraphEnd = text.indexOf('\n', start)
        if (paragraphEnd < 0) paragraphEnd = text.length
        if (paragraphEnd - start <= MAX_CHUNK_LENGTH) return CharRange(start, paragraphEnd)

        // Long paragraph: pack whole sentences, looking only as far ahead as needed.
        val windowEnd = minOf(paragraphEnd, start + MAX_SENTENCE_LENGTH)
        val sentences = sentenceRanges(text.substring(start, windowEnd), sentenceLocale(start))
        var end = start
        for ((i, sentence) in sentences.withIndex()) {
            val sentenceEnd = start + sentence.end
            // The window's last "sentence" may be cut by the window, not by punctuation.
            val truncated = i == sentences.lastIndex && windowEnd < paragraphEnd
            if (sentenceEnd - start > MAX_CHUNK_LENGTH && end > start) break
            if (truncated && end > start) break
            end = if (truncated) cutAtWhitespace(start, windowEnd) else sentenceEnd
            if (end - start >= MAX_CHUNK_LENGTH) break
        }
        return CharRange(start, if (end > start) end else windowEnd)
    }

    private fun isSkippable(c: Char) = c.isWhitespace() || c == ParsedBook.IMAGE_CHAR

    /** Last whitespace before [limit], so no word is split; [limit] itself for unspaced text. */
    private fun cutAtWhitespace(start: Int, limit: Int): Int {
        var i = limit
        while (i > start + 1 && !text[i - 1].isWhitespace()) i--
        if (i <= start + 1) return if (Character.isLowSurrogate(text[limit])) limit - 1 else limit
        return i
    }

    private fun sentenceLocale(offset: Int): Locale =
        if (containsHangul(text, offset, minOf(text.length, offset + 200))) Locale.KOREAN else Locale.ENGLISH

    private fun sentenceRanges(s: String, locale: Locale): List<CharRange> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(s)
        val result = ArrayList<CharRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (end > start) result += CharRange(start, end)
            start = end
            end = iterator.next()
        }
        if (result.isEmpty() && s.isNotEmpty()) result += CharRange(0, s.length)
        return result
    }

    // MARK: - Playback

    /** Hands chunks to the engine until [QUEUE_DEPTH] are queued (or the text runs out). */
    private fun fillQueue(flush: Boolean = false) {
        val tts = engine ?: return
        var first = flush
        while (queue.size < QUEUE_DEPTH) {
            val range = nextChunkRange(cursor) ?: break
            cursor = range.end
            val raw = text.substring(range.start, range.end)
            val locale = if (containsHangul(raw, 0, raw.length)) Locale.KOREAN else Locale.US
            val sentences = sentenceRanges(raw, locale).map { CharRange(range.start + it.start, range.start + it.end) }
            // Newlines and image placeholders become spaces 1:1 so the engine's
            // range callbacks stay aligned with the original offsets.
            val spoken = raw.replace('\n', ' ').replace(ParsedBook.IMAGE_CHAR, ' ')
            val chunk = Chunk("$generation:${range.start}", range, sentences)
            applyVoice(tts, locale)
            val result = tts.speak(spoken, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), chunk.id)
            first = false
            if (result != TextToSpeech.SUCCESS) break
            queue.addLast(chunk)
        }
    }

    private fun applyVoice(tts: TextToSpeech, locale: Locale) {
        val s = _state.value
        tts.setSpeechRate(s.rate)
        tts.setPitch(s.pitch)
        val voice = s.selectedVoiceName?.let { name -> engineVoices.firstOrNull { it.name == name } }
        runCatching {
            if (voice != null) tts.voice = voice
            else if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        }
    }

    private fun chunkFor(utteranceId: String?): Chunk? {
        if (utteranceId == null || !utteranceId.startsWith("$generation:")) return null
        return queue.firstOrNull { it.id == utteranceId }
    }

    private fun onChunkStarted(utteranceId: String?) {
        val chunk = chunkFor(utteranceId) ?: return
        val sentence = chunk.sentences.firstOrNull() ?: chunk.range
        resumeOffset = sentence.start
        _state.update { it.copy(spokenRange = sentence) }
    }

    private fun onRange(utteranceId: String?, start: Int) {
        val chunk = chunkFor(utteranceId) ?: return
        val offset = chunk.range.start + start
        val sentence = chunk.sentences.firstOrNull { offset in it } ?: return
        if (sentence == _state.value.spokenRange) return
        resumeOffset = sentence.start
        _state.update { it.copy(spokenRange = sentence) }
    }

    private fun onChunkFinished(utteranceId: String?) {
        val chunk = chunkFor(utteranceId) ?: return
        queue.remove(chunk)
        resumeOffset = chunk.range.end
        fillQueue()
        if (queue.isEmpty()) stop()   // reached the end of the book
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post { onChunkStarted(utteranceId) }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            main.post { onRange(utteranceId, start) }
        }

        override fun onDone(utteranceId: String?) {
            main.post { onChunkFinished(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            // Skip the chunk the engine could not speak rather than stalling.
            main.post { onChunkFinished(utteranceId) }
        }
    }

    companion object {
        private const val MAX_CHUNK_LENGTH = 240
        private const val MAX_SENTENCE_LENGTH = 2000
        private const val QUEUE_DEPTH = 3

        private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        fun containsHangul(s: CharSequence, start: Int, end: Int): Boolean {
            for (i in start until end) {
                val c = s[i]
                if (c in '가'..'힣' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏') return true
            }
            return false
        }
    }
}
