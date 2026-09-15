package com.qbitcore.booklip.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TtsUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val availableVoices: List<Voice> = emptyList(),
    val selectedVoiceName: String? = null,
    val sleepMinutes: Int? = null,
    /** Paragraph index currently being spoken, or null when stopped. */
    val currentParagraphIndex: Int? = null,
    /** Character range within that paragraph's text currently being spoken, if the engine reports it. */
    val spokenRange: IntRange? = null,
)

/**
 * Android has no direct equivalent of AVSpeechSynthesizer's true pause/resume
 * (there is no public "pause playback in place" API on [TextToSpeech]), so
 * [pause] stops the engine and remembers the current paragraph; [resume]
 * re-speaks that paragraph from its start rather than the exact word — a
 * deliberate simplification versus the iOS TTSManager.
 */
class BooklipTts(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state

    private var engine: TextToSpeech? = null
    private var paragraphs: List<String> = emptyList()
    private var chunks: List<Chunk> = emptyList()
    private var chunkPos: Int = 0

    private data class Chunk(val paragraphIndex: Int, val text: String, val localOffset: Int)

    init {
        engine = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setOnUtteranceProgressListener(listener)
                refreshVoices()
                _state.value = _state.value.copy(isReady = true)
            }
        }
    }

    private fun refreshVoices() {
        val all = engine?.voices ?: emptySet()
        val preferred = all.filter { voice ->
            val tag = voice.locale.toLanguageTag().lowercase()
            tag.startsWith("en-us") || tag.startsWith("ko-kr")
        }.sortedBy { it.name }
        val current = _state.value
        val selected = current.selectedVoiceName?.takeIf { name -> preferred.any { it.name == name } }
            ?: preferred.firstOrNull()?.name
        _state.value = current.copy(availableVoices = preferred, selectedVoiceName = selected)
    }

    fun speak(paragraphs: List<String>, fromParagraphIndex: Int) {
        val eng = engine ?: return
        this.paragraphs = paragraphs
        chunks = buildChunks(paragraphs, fromParagraphIndex, eng.maxSpeechInputLength())
        chunkPos = 0
        eng.stop()
        if (chunks.isEmpty()) return
        _state.value.selectedVoiceName?.let { name ->
            _state.value.availableVoices.firstOrNull { it.name == name }?.let { eng.voice = it }
        }
        eng.setSpeechRate(_state.value.rate)
        eng.setPitch(_state.value.pitch)
        chunks.forEachIndexed { i, chunk ->
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            eng.speak(chunk.text, queueMode, Bundle.EMPTY, i.toString())
        }
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
    }

    fun pause() {
        engine?.stop()
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
    }

    fun resume() {
        val chunk = chunks.getOrNull(chunkPos) ?: return
        speak(paragraphs, chunk.paragraphIndex)
    }

    fun stop() {
        engine?.stop()
        chunks = emptyList()
        chunkPos = 0
        _state.value = _state.value.copy(
            isPlaying = false,
            isPaused = false,
            currentParagraphIndex = null,
            spokenRange = null,
        )
    }

    fun setRate(rate: Float) {
        _state.value = _state.value.copy(rate = rate)
        engine?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        _state.value = _state.value.copy(pitch = pitch)
        engine?.setPitch(pitch)
    }

    fun setVoice(voiceName: String) {
        _state.value = _state.value.copy(selectedVoiceName = voiceName)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _state.value = _state.value.copy(sleepMinutes = minutes)
        if (minutes == null) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            stop()
            _state.value = _state.value.copy(sleepMinutes = null)
        }
    }

    fun release() {
        sleepJob?.cancel()
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    // MARK: - Chunking

    /**
     * One paragraph → one or more chunks, hard-capped at [maxLength] (an
     * engine limit — [TextToSpeech.getMaxSpeechInputLength]) and subdivided at
     * sentence boundaries via [BreakIterator] when a paragraph is longer than
     * that, mirroring the iOS TTSManager's paragraph/sentence chunking.
     */
    private fun buildChunks(paragraphs: List<String>, fromIndex: Int, maxLength: Int): List<Chunk> {
        val cap = maxLength.coerceAtMost(3900)
        val result = mutableListOf<Chunk>()
        for (index in fromIndex until paragraphs.size) {
            val text = paragraphs[index]
            if (text.isBlank()) continue
            if (text.length <= cap) {
                result += Chunk(index, text, 0)
                continue
            }
            result += packSentences(text, cap).map { (start, end) -> Chunk(index, text.substring(start, end), start) }
        }
        return result
    }

    /**
     * Packs the sentences of [text] into ranges of at most [cap] chars each;
     * a single sentence longer than [cap] is hard-split at the cap.
     */
    private fun packSentences(text: String, cap: Int): List<Pair<Int, Int>> {
        val boundary = BreakIterator.getSentenceInstance(Locale.getDefault())
        boundary.setText(text)
        val sentences = mutableListOf<Pair<Int, Int>>()
        var start = boundary.first()
        var end = boundary.next()
        while (end != BreakIterator.DONE) {
            if (end > start) sentences += start to end
            start = end
            end = boundary.next()
        }
        if (sentences.isEmpty()) return if (text.isNotEmpty()) listOf(0 to text.length) else emptyList()

        val result = mutableListOf<Pair<Int, Int>>()
        var bufferStart = -1
        var bufferEnd = -1
        fun flush() {
            if (bufferStart in 0 until bufferEnd) result += bufferStart to bufferEnd
            bufferStart = -1
            bufferEnd = -1
        }
        for ((sentenceStart, sentenceEnd) in sentences) {
            if (sentenceEnd - sentenceStart > cap) {
                flush()
                var s = sentenceStart
                while (s < sentenceEnd) {
                    val e = minOf(s + cap, sentenceEnd)
                    result += s to e
                    s = e
                }
                continue
            }
            if (bufferStart < 0) {
                bufferStart = sentenceStart
                bufferEnd = sentenceEnd
            } else if (sentenceEnd - bufferStart <= cap) {
                bufferEnd = sentenceEnd
            } else {
                flush()
                bufferStart = sentenceStart
                bufferEnd = sentenceEnd
            }
        }
        flush()
        return result
    }

    private fun TextToSpeech.maxSpeechInputLength(): Int = try {
        TextToSpeech.getMaxSpeechInputLength()
    } catch (_: Throwable) {
        4000
    }

    // MARK: - Engine callbacks

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val i = utteranceId?.toIntOrNull() ?: return
            chunkPos = i
            val chunk = chunks.getOrNull(i) ?: return
            _state.value = _state.value.copy(currentParagraphIndex = chunk.paragraphIndex, spokenRange = null)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val i = utteranceId?.toIntOrNull() ?: return
            val chunk = chunks.getOrNull(i) ?: return
            _state.value = _state.value.copy(
                currentParagraphIndex = chunk.paragraphIndex,
                spokenRange = (chunk.localOffset + start)..(chunk.localOffset + end),
            )
        }

        override fun onDone(utteranceId: String?) {
            val i = utteranceId?.toIntOrNull() ?: return
            if (i == chunks.lastIndex) {
                _state.value = _state.value.copy(isPlaying = false, currentParagraphIndex = null, spokenRange = null)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }
}
