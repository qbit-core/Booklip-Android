package com.qbitcore.booklip.settings

import android.graphics.Typeface

data class ColorPreset(
    val id: String,
    val label: String,
    val background: Long,
    val text: Long,
    val isDark: Boolean,
)

// Same six themes, same colours as the iOS app.
val COLOR_PRESETS = listOf(
    ColorPreset("default", "Default", 0xFFFFFFFF, 0xFF000000, isDark = false),
    ColorPreset("sepia", "Sepia", 0xFFF7F0DB, 0xFF4D331A, isDark = false),
    ColorPreset("dark", "Dark", 0xFF1A1A1F, 0xFFE6E6E6, isDark = true),
    ColorPreset("forest", "Forest", 0xFF1F2E1F, 0xFFD1EBCC, isDark = true),
    ColorPreset("ocean", "Ocean", 0xFF121C38, 0xFFC7EBFF, isDark = true),
    ColorPreset("rose", "Rose", 0xFFFCF2F5, 0xFF592633, isDark = false),
)

/**
 * Android ships font *families* rather than the named faces iOS has; these
 * are the system families present on every device. Korean text falls back to
 * Noto Serif CJK under [SERIF] and Noto Sans CJK under the sans families.
 */
enum class ReaderFont(val label: String, private val family: String, private val style: Int = Typeface.NORMAL) {
    SERIF("Serif · 명조", "serif"),
    SANS_SERIF("Sans Serif · 고딕", "sans-serif"),
    SANS_LIGHT("Sans Serif Light", "sans-serif-light"),
    SANS_MEDIUM("Sans Serif Medium", "sans-serif-medium"),
    CONDENSED("Condensed", "sans-serif-condensed"),
    SERIF_MONO("Serif Monospace", "serif-monospace"),
    MONOSPACE("Monospace", "monospace"),
    CASUAL("Casual", "casual"),
    CURSIVE("Cursive", "cursive");

    val typeface: Typeface by lazy { Typeface.create(family, style) }
}

enum class PageEffect(val label: String) {
    VERTICAL_SLIDE("Vertical Slide"),
    PAPER("Paper Book"),
}

data class ReadingSettings(
    val font: ReaderFont = ReaderFont.SERIF,
    val fontSize: Float = 18f,
    val lineSpacing: Float = 8f,
    val presetId: String = "default",
    val pageEffect: PageEffect = PageEffect.VERTICAL_SLIDE,
    /** Render EPUBs in their embedded font (required for font-obfuscated books). */
    val useEmbeddedFont: Boolean = true,
    /** dp per second. */
    val autoScrollSpeed: Float = 40f,
) {
    val preset: ColorPreset
        get() = COLOR_PRESETS.firstOrNull { it.id == presetId } ?: COLOR_PRESETS[0]

    companion object {
        val FONT_SIZE_RANGE = 12f..32f
        val LINE_SPACING_RANGE = 0f..24f
        val AUTO_SCROLL_RANGE = 10f..120f
    }
}
