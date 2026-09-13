package com.qbitcore.booklip.settings

data class ColorPreset(
    val id: String,
    val label: String,
    val background: Long,
    val text: Long,
    val isDark: Boolean,
)

val COLOR_PRESETS = listOf(
    ColorPreset("default", "Default", 0xFFFFFFFF, 0xFF000000, isDark = false),
    ColorPreset("sepia", "Sepia", 0xFFF7EFDB, 0xFF4D331A, isDark = false),
    ColorPreset("dark", "Dark", 0xFF1A1A1F, 0xFFE6E6E6, isDark = true),
    ColorPreset("forest", "Forest", 0xFF1F2E1F, 0xFFD1EBCC, isDark = true),
    ColorPreset("ocean", "Ocean", 0xFF121C38, 0xFFC7EBFF, isDark = true),
    ColorPreset("rose", "Rose", 0xFFFCF2F4, 0xFF592634, isDark = false),
)

enum class ReaderFont(val label: String, val fontFamily: String) {
    SERIF("Serif", "serif"),
    SANS_SERIF("Sans Serif", "sans-serif"),
    MONOSPACE("Monospace", "monospace"),
}

data class ReadingSettings(
    val fontFamily: ReaderFont = ReaderFont.SERIF,
    val fontSize: Float = 18f,
    val lineSpacing: Float = 8f,
    val presetId: String = "default",
) {
    val preset: ColorPreset
        get() = COLOR_PRESETS.firstOrNull { it.id == presetId } ?: COLOR_PRESETS[0]
}
