package com.qbitcore.booklip.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.tts.TtsUiState

@Composable
fun TtsSheet(
    state: TtsUiState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onVoiceSelected: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTogglePlayPause, enabled = state.isReady) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop")
            }
        }

        Text("Voice", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        VoicePicker(state, onVoiceSelected)

        Text("Speed: ${"%.1f".format(state.rate)}x", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Slider(value = state.rate, onValueChange = onRateChange, valueRange = 0.5f..2.0f)

        Text("Pitch: ${"%.1f".format(state.pitch)}x", style = MaterialTheme.typography.titleSmall)
        Slider(value = state.pitch, onValueChange = onPitchChange, valueRange = 0.5f..2.0f)

        Text("Sleep timer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        SleepTimerPicker(state.sleepMinutes, onSleepTimerChange)
    }
}

@Composable
private fun VoicePicker(state: TtsUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.availableVoices.firstOrNull { it.name == state.selectedVoiceName }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { expanded = true }) {
            Text(selected?.let { "${it.name}  (${it.locale.toLanguageTag()})" } ?: "Default")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.availableVoices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text("${voice.name}  (${voice.locale.toLanguageTag()})") },
                    onClick = { onSelect(voice.name); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SleepTimerPicker(sleepMinutes: Int?, onChange: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Text(sleepMinutes?.let { "$it min" } ?: "Off")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Off") }, onClick = { onChange(null); expanded = false })
            listOf(5, 15, 30, 45, 60).forEach { minutes ->
                DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = { onChange(minutes); expanded = false })
            }
        }
    }
}
