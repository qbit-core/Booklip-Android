package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbitcore.booklip.settings.COLOR_PRESETS
import com.qbitcore.booklip.settings.PageEffect
import com.qbitcore.booklip.settings.ReaderFont
import com.qbitcore.booklip.settings.ReadingSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    settings: ReadingSettings,
    /** PDFs only use the theme and the page-turn mode. */
    textOptions: Boolean,
    /** The book ships its own font, so "use the book's font" does something. */
    hasEmbeddedFont: Boolean,
    onChange: ((ReadingSettings) -> ReadingSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            PanelSection("Theme") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    COLOR_PRESETS.forEach { preset ->
                        val selected = preset.id == settings.presetId
                        Column(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange { it.copy(presetId = preset.id) } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(width = 56.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(preset.background))
                                    .border(
                                        if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Aa", color = Color(preset.text), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(preset.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (textOptions) {
                PanelSection("Font") {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(settings.font.label, fontFamily = FontFamily(settings.font.typeface))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ReaderFont.entries.forEach { font ->
                                DropdownMenuItem(
                                    text = { Text(font.label, fontFamily = FontFamily(font.typeface)) },
                                    trailingIcon = { if (font == settings.font) Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = {
                                        onChange { it.copy(font = font) }
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                PanelSection("Size") {
                    LabeledSlider(settings.fontSize, ReadingSettings.FONT_SIZE_RANGE, "pt") { v -> onChange { it.copy(fontSize = v) } }
                }
                PanelSection("Line Spacing") {
                    LabeledSlider(settings.lineSpacing, ReadingSettings.LINE_SPACING_RANGE, "pt") { v -> onChange { it.copy(lineSpacing = v) } }
                }
            }

            PanelSection("Page Turn") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PageEffect.entries.forEachIndexed { index, effect ->
                        SegmentedButton(
                            selected = settings.pageEffect == effect,
                            onClick = { onChange { it.copy(pageEffect = effect) } },
                            shape = SegmentedButtonDefaults.itemShape(index, PageEffect.entries.size),
                            icon = {},
                        ) { Text(effect.label) }
                    }
                }
            }

            if (textOptions) {
                PanelSection("Auto-Scroll Speed") {
                    LabeledSlider(settings.autoScrollSpeed, ReadingSettings.AUTO_SCROLL_RANGE, "", step = 5f) { v ->
                        onChange { it.copy(autoScrollSpeed = v) }
                    }
                }
                PanelSection("EPUB") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use book's original font")
                            if (!hasEmbeddedFont) {
                                Text(
                                    "This book has no embedded font.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(checked = settings.useEmbeddedFont, onCheckedChange = { on -> onChange { it.copy(useEmbeddedFont = on) } })
                    }
                }
                PanelSection("Preview") {
                    Text(
                        "The quick brown fox jumps over the lazy dog.\n다람쥐 헌 쳇바퀴에 타고파.",
                        color = Color(settings.preset.text),
                        fontFamily = FontFamily(settings.font.typeface),
                        fontSize = settings.fontSize.sp,
                        lineHeight = (settings.fontSize * 1.2f + settings.lineSpacing).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(settings.preset.background))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PanelSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * The value follows the finger locally and is committed when the drag ends:
 * every committed change re-lays-out (and re-paginates) the book.
 */
@Composable
private fun LabeledSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    step: Float = 1f,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableFloatStateOf(value) }
    val shown = dragging ?: value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = shown,
            onValueChange = {
                val snapped = ((it / step).roundToInt() * step).coerceIn(range)
                dragging = snapped
                pending = snapped
            },
            onValueChangeFinished = {
                onCommit(pending)
                dragging = null
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${shown.roundToInt()}$unit",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
    }
}
