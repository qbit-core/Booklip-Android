package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.settings.COLOR_PRESETS
import com.qbitcore.booklip.settings.ReaderFont
import com.qbitcore.booklip.settings.ReadingSettings

@Composable
fun AppearanceSheet(settings: ReadingSettings, onChange: ((ReadingSettings) -> ReadingSettings) -> Unit) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Font", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderFont.entries.forEach { font ->
                FilterChip(
                    selected = settings.fontFamily == font,
                    onClick = { onChange { it.copy(fontFamily = font) } },
                    label = { Text(font.label) },
                )
            }
        }

        Text("Font size: ${settings.fontSize.toInt()}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.fontSize,
            onValueChange = { value -> onChange { it.copy(fontSize = value) } },
            valueRange = 12f..32f,
        )

        Text("Line spacing: ${settings.lineSpacing.toInt()}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.lineSpacing,
            onValueChange = { value -> onChange { it.copy(lineSpacing = value) } },
            valueRange = 0f..24f,
        )

        Text(
            "Theme",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            COLOR_PRESETS.forEach { preset ->
                val isSelected = settings.presetId == preset.id
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(preset.background))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(preset.text).copy(alpha = 0.3f),
                            shape = CircleShape,
                        )
                        .clickable { onChange { it.copy(presetId = preset.id) } },
                )
            }
        }
    }
}
