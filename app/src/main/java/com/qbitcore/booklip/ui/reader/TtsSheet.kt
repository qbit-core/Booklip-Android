package com.qbitcore.booklip.ui.reader

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qbitcore.booklip.tts.TtsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSheet(
    state: TtsState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onVoiceSelected: (String?) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onTogglePlayPause, enabled = !state.unavailable, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onStop, enabled = state.isActive, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = "Stop", modifier = Modifier.size(44.dp))
                }
            }
            if (state.unavailable) {
                Text(
                    "No text-to-speech engine is available on this device. Install or enable one in Settings › Accessibility › Text-to-speech output.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            PanelSection("Voice") {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(state.voices.firstOrNull { it.name == state.selectedVoiceName }?.label ?: "Automatic")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Automatic (by language)") },
                            trailingIcon = { if (state.selectedVoiceName == null) Icon(Icons.Filled.Check, contentDescription = null) },
                            onClick = {
                                onVoiceSelected(null)
                                expanded = false
                            },
                        )
                        state.voices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voice.label) },
                                trailingIcon = { if (voice.name == state.selectedVoiceName) Icon(Icons.Filled.Check, contentDescription = null) },
                                onClick = {
                                    onVoiceSelected(voice.name)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
            PanelSection("Speed") { CommitSlider(state.rate, 0.5f..2.5f, onRateChange) }
            PanelSection("Pitch") { CommitSlider(state.pitch, 0.5f..2f, onPitchChange) }
            PanelSection("Sleep Timer") {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                        Text(state.sleepMinutes?.let { "$it min" } ?: "Off")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Off") }, onClick = {
                            onSleepTimerChange(null)
                            expanded = false
                        })
                        listOf(5, 15, 30, 45, 60).forEach { minutes ->
                            DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = {
                                onSleepTimerChange(minutes)
                                expanded = false
                            })
                        }
                    }
                }
            }
        }
    }
}

/** Commits when the drag ends: a change while speaking restarts the current sentence. */
@Composable
private fun CommitSlider(value: Float, range: ClosedFloatingPointRange<Float>, onCommit: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = shown,
            onValueChange = { dragging = (it * 10).toInt() / 10f },
            onValueChangeFinished = {
                dragging?.let(onCommit)
                dragging = null
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(
            String.format("%.1fx", shown),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp).padding(start = 8.dp),
        )
    }
}
