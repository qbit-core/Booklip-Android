package com.qbitcore.booklip.parser

import java.util.Locale

/**
 * Detects a book's main language from its text, on-device and offline (the
 * counterpart of the iOS `LanguageDetector`, same codes).
 *
 * Returns a BCP-47 code: "ko", "ja", "zh-Hans", "zh-Hant", "en", "fr", …, or
 * [UNDETERMINED] when there is no usable text (a scanned PDF).
 *
 * CJK is decided by Unicode script counts, as on iOS: Hangul means Korean, any
 * real share of kana means Japanese (Japanese prose is never kana-free), Han
 * without kana means Chinese, split into Simplified / Traditional by
 * characters that only one of the two scripts uses. Android has no
 * `NLLanguageRecognizer`, so the other scripts map to their main language and
 * Latin-script text is scored against each language's most common words.
 */
object LanguageDetector {
    const val UNDETERMINED = "und"

    /**
     * Characters examined per window; three windows (start, middle, late) so
     * an English copyright page doesn't decide a Korean novel.
     */
    private const val WINDOW_LENGTH = 4000

    fun detect(text: String): String {
        val sample = sample(text)
        var hangul = 0
        var kana = 0
        var han = 0
        var latin = 0
        var otherLetters = 0
        val scripts = HashMap<Character.UnicodeScript, Int>()
        var i = 0
        while (i < sample.length) {
            val cp = sample.codePointAt(i)
            i += Character.charCount(cp)
            when (cp) {
                in 0xAC00..0xD7A3, in 0x1100..0x11FF, in 0x3130..0x318F -> hangul++
                in 0x3040..0x309F, in 0x30A0..0x30FF, in 0x31F0..0x31FF, in 0xFF66..0xFF9F -> kana++
                in 0x4E00..0x9FFF, in 0x3400..0x4DBF, in 0xF900..0xFAFF -> han++
                else -> if (Character.isLetter(cp)) {
                    val script = Character.UnicodeScript.of(cp)
                    if (script == Character.UnicodeScript.LATIN) latin++ else otherLetters++
                    scripts.merge(script, 1, Int::plus)
                }
            }
        }
        val cjk = hangul + kana + han
        val nonCjk = latin + otherLetters
        if (cjk + nonCjk == 0) return UNDETERMINED

        // One CJK character carries roughly a word; Latin needs ~4-5 letters
        // for the same. Weighting keeps a Korean book with English terms Korean.
        if (cjk * 4 >= nonCjk) {
            if (hangul >= kana && hangul * 10 >= cjk * 3) return "ko"
            if (kana * 10 >= cjk) return "ja"
            if (han > 0) return chineseScript(sample)
            return if (hangul > 0) "ko" else "ja"
        }

        val script = scripts.maxByOrNull { it.value }?.key ?: return UNDETERMINED
        return when (script) {
            Character.UnicodeScript.LATIN -> latinLanguage(sample)
            Character.UnicodeScript.CYRILLIC -> if (sample.any { it in "іїєґІЇЄҐ" }) "uk" else "ru"
            Character.UnicodeScript.GREEK -> "el"
            Character.UnicodeScript.ARABIC -> "ar"
            Character.UnicodeScript.HEBREW -> "he"
            Character.UnicodeScript.THAI -> "th"
            Character.UnicodeScript.DEVANAGARI -> "hi"
            else -> UNDETERMINED
        }
    }

    /** Simplified vs Traditional, by the common characters that differ between them. */
    private fun chineseScript(sample: String): String {
        var simplified = 0
        var traditional = 0
        for (c in sample) {
            if (c in SIMPLIFIED_ONLY) simplified++ else if (c in TRADITIONAL_ONLY) traditional++
        }
        return if (traditional > simplified) "zh-Hant" else "zh-Hans"
    }

    /** The language whose most common words cover the largest share of the sample's words. */
    private fun latinLanguage(sample: String): String {
        val words = sample.lowercase(Locale.ROOT).split(WORD_SPLIT).filter { it.isNotEmpty() }
        if (words.isEmpty()) return UNDETERMINED
        var best = UNDETERMINED
        var bestScore = 0
        for ((code, stopWords) in STOP_WORDS) {
            val score = words.count { it in stopWords }
            if (score > bestScore) {
                best = code
                bestScore = score
            }
        }
        // Too few common words to tell (a word list, code, names): don't guess.
        return if (bestScore * 20 >= words.size) best else UNDETERMINED
    }

    private fun sample(text: String): String {
        if (text.length <= WINDOW_LENGTH * 3) return text
        return listOf(0.05, 0.5, 0.8).joinToString("\n") { fraction ->
            var start = ((text.length - WINDOW_LENGTH) * fraction).toInt()
            var end = start + WINDOW_LENGTH
            // Don't split a surrogate pair.
            if (Character.isLowSurrogate(text[start])) start++
            if (end < text.length && Character.isLowSurrogate(text[end])) end++
            text.substring(start, end)
        }
    }

    /** Name for a detected code, in the device language ("Korean" / "한국어"). */
    fun displayName(code: String): String {
        if (code == UNDETERMINED) return "Unknown"
        val locale = Locale.forLanguageTag(code)
        val name = locale.getDisplayName(Locale.getDefault())
        return name.ifBlank { code }.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    /** Language part only: "zh-Hant" → "zh". */
    fun baseCode(code: String): String = code.substringBefore('-')

    private val WORD_SPLIT = Regex("[^\\p{L}']+")

    private val STOP_WORDS: Map<String, Set<String>> = mapOf(
        "en" to "the and of to a in is that it was he for with you his had not but as on at she her they be".words(),
        "fr" to "le la les et des est une que dans pas il du pour qui sur au elle je ne se ce un avec mais nous vous".words(),
        "de" to "der die und das ist nicht ich sie es ein zu den mit sich auf dem auch eine war er wie aber noch".words(),
        "es" to "el la que de y los en se del las por un una con no es su para al lo como más pero sus".words(),
        "it" to "il che di la non per un una del della sono gli le è con si ma come anche nel alla ho mi".words(),
        "pt" to "o a que de e não um uma os do da em para com é se as mas ao dos das ele ela foi".words(),
        "nl" to "de het een en van is dat niet ik je op te zijn met voor hij ze er maar die".words(),
        "sv" to "och att det som en på är av för med till den har jag inte om ett var hon han".words(),
        "da" to "og at det som en på er af for med til den har jeg ikke om et var hun han".words(),
        "pl" to "i w nie na się z że do to jest jak co ale o tak po jego od za".words(),
        "tr" to "ve bir bu da de için ile çok ne ama gibi daha o ben sen var olarak".words(),
        "id" to "yang dan di itu dengan untuk tidak ini dari dalam akan pada ke ada saya".words(),
        "vi" to "và của là có không một những được trong cho người này với đã các".words(),
    )

    private fun String.words(): Set<String> = split(' ').toSet()

    private const val SIMPLIFIED_ONLY =
        "们这个说时来国为会对发经没过还进现么爱长实问开关头间见东车书门马鸟龙话认让应学气听将样从与无动当实" +
            "边买卖钱飞机电脑语读写觉纸红绿蓝云阳阴岁诗词员"
    private const val TRADITIONAL_ONLY =
        "們這個說時來國為會對發經沒過還進現麼愛長實問開關頭間見東車書門馬鳥龍話認讓應學氣聽將樣從與無動當實" +
            "邊買賣錢飛機電腦語讀寫覺紙紅綠藍雲陽陰歲詩詞員"
}
