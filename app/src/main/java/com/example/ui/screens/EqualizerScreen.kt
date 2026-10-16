package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.CustomEqPreset
import com.example.audio.EqPreset
import com.example.audio.SpatialMode
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkRedAccent
import com.example.ui.theme.DarkRedPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EqNeonGreen
import com.example.ui.theme.EqNeonGreenAccent
import com.example.ui.theme.SpatialBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EqualizerScreen(
    eqEnabled: Boolean,
    isExternalAudioConnected: Boolean = true,
    isAuxSimulated: Boolean = false,
    onToggleSimulateAux: () -> Unit = {},
    bassBoostLevel: Float,
    surround360Enabled: Boolean,
    spatialMode: SpatialMode = SpatialMode.HEADPHONES_3D,
    surroundStrength: Float,
    isSlowedReverbEnabled: Boolean,
    playbackSpeed: Float,
    bandGains: FloatArray,
    selectedPreset: EqPreset,
    customPresets: List<CustomEqPreset> = emptyList(),
    selectedCustomPresetId: String? = null,
    onEqEnabledToggle: (Boolean) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onSurround360Toggle: (Boolean) -> Unit,
    onSpatialModeSelect: (SpatialMode) -> Unit = {},
    onSurroundStrengthChange: (Float) -> Unit,
    onSlowedReverbToggle: (Boolean) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onPresetSelect: (EqPreset) -> Unit,
    onCustomPresetSelect: (CustomEqPreset) -> Unit = {},
    onSaveCustomPreset: (String, FloatArray, Float) -> Unit = { _, _, _ -> },
    onDeleteCustomPreset: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    val bandLabels = listOf("60Hz\nBass", "230Hz\nLow", "910Hz\nMid", "3.6kHz\nHigh", "14kHz\nTreble")
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var presetToDelete by remember { mutableStateOf<CustomEqPreset?>(null) }

    val showSpeakerWarningToast = {
        android.widget.Toast.makeText(
            context,
            "Equalizer is disabled on normal phone speakers. Connect Bluetooth or Wired Aux/Headphones to enable.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EQUALIZER",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = DarkRedPrimary,
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = "360 Surround, Reverb & DSP Audio FX",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (!isExternalAudioConnected) Modifier.clickable { showSpeakerWarningToast() } else Modifier
            ) {
                Text(
                    text = if (eqEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (!isExternalAudioConnected) Color(0xFFFFB74D) else if (eqEnabled) EqNeonGreen else TextMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(
                    checked = eqEnabled && isExternalAudioConnected,
                    enabled = isExternalAudioConnected,
                    onCheckedChange = {
                        if (!isExternalAudioConnected) {
                            showSpeakerWarningToast()
                        } else {
                            onEqEnabledToggle(it)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EqNeonGreen,
                        checkedTrackColor = EqNeonGreen.copy(alpha = 0.35f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant,
                        disabledCheckedThumbColor = TextMuted.copy(alpha = 0.4f),
                        disabledCheckedTrackColor = DarkSurfaceVariant.copy(alpha = 0.2f),
                        disabledUncheckedThumbColor = TextMuted.copy(alpha = 0.4f),
                        disabledUncheckedTrackColor = DarkSurfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.testTag("eq_master_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // Slowed + Reverb (Lofi Aesthetic Sound FX) Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (isSlowedReverbEnabled) EqNeonGreen.copy(alpha = 0.6f) else DarkCardBorder,
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSlowedReverbEnabled) DarkSurfaceVariant.copy(alpha = 0.7f) else DarkSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSlowedReverbEnabled) EqNeonGreen else DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Slowed + Reverb",
                                tint = if (isSlowedReverbEnabled) Color.Black else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Slowed + Reverb",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(EqNeonGreen.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LO-FI",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = EqNeonGreenAccent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            Text(
                                text = if (isSlowedReverbEnabled) "0.90x Tempo + Deep Hall Reverb Active" else "Dreamy, slowed tempo & atmospheric acoustics",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSlowedReverbEnabled) EqNeonGreenAccent else TextSecondary
                                )
                            )
                        }
                    }

                    Switch(
                        checked = isSlowedReverbEnabled,
                        onCheckedChange = onSlowedReverbToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EqNeonGreen,
                            checkedTrackColor = EqNeonGreen.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.testTag("slowed_reverb_switch")
                    )
                }

                AnimatedVisibility(visible = isSlowedReverbEnabled || playbackSpeed != 1.0f) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tempo Speed: ${String.format("%.2f", playbackSpeed)}x",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(
                                    0.75f to "0.75x",
                                    0.85f to "0.85x",
                                    0.90f to "0.90x (Default)",
                                    0.95f to "0.95x",
                                    1.00f to "1.00x"
                                ).forEach { (speedOption, label) ->
                                    val isSelected = Math.abs(playbackSpeed - speedOption) < 0.02f
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) EqNeonGreen else DarkSurfaceVariant)
                                            .clickable { onPlaybackSpeedChange(speedOption) }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) Color.Black else TextSecondary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Slider(
                            value = playbackSpeed,
                            onValueChange = onPlaybackSpeedChange,
                            valueRange = 0.70f..1.30f,
                            colors = SliderDefaults.colors(
                                thumbColor = EqNeonGreen,
                                activeTrackColor = EqNeonGreen,
                                inactiveTrackColor = DarkSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playback_speed_slider")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        
        // Bass Boost Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (eqEnabled && isExternalAudioConnected) EqNeonGreen else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Bass Boost",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = if (isExternalAudioConnected) "Low-frequency punch enhancement" else "Requires Bluetooth or Wired Aux connection",
                                style = MaterialTheme.typography.labelSmall.copy(color = if (isExternalAudioConnected) TextSecondary else Color(0xFFFFB74D))
                            )
                        }
                    }

                    Text(
                        text = "${(bassBoostLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = if (eqEnabled && isExternalAudioConnected) EqNeonGreen else TextMuted
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = bassBoostLevel,
                    onValueChange = onBassBoostChange,
                    enabled = eqEnabled && isExternalAudioConnected,
                    colors = SliderDefaults.colors(
                        thumbColor = EqNeonGreen,
                        activeTrackColor = EqNeonGreen,
                        inactiveTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bass_boost_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Equalizer Presets Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRESETS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            )

            TextButton(
                onClick = {
                    if (!isExternalAudioConnected) {
                        showSpeakerWarningToast()
                    } else {
                        presetNameInput = ""
                        showSavePresetDialog = true
                    }
                },
                enabled = isExternalAudioConnected && eqEnabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = EqNeonGreen,
                    disabledContentColor = TextMuted.copy(alpha = 0.4f)
                ),
                modifier = Modifier.testTag("save_current_preset_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Current as Preset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // User-Saved Custom Presets
            items(customPresets, key = { it.id }) { customPreset ->
                val isSelected = selectedCustomPresetId == customPreset.id && eqEnabled && isExternalAudioConnected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isExternalAudioConnected) {
                            showSpeakerWarningToast()
                        } else {
                            onCustomPresetSelect(customPreset)
                        }
                    },
                    enabled = isExternalAudioConnected,
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(customPreset.name, fontWeight = FontWeight.SemiBold)
                            IconButton(
                                onClick = { presetToDelete = customPreset },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete preset",
                                    tint = if (isSelected) Color.Black else TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EqNeonGreen,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurfaceVariant,
                        labelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = isExternalAudioConnected,
                        selected = isSelected,
                        borderColor = if (isSelected) EqNeonGreen else DarkCardBorder,
                        selectedBorderColor = EqNeonGreen
                    ),
                    modifier = Modifier.testTag("custom_preset_${customPreset.id}")
                )
            }

            // Built-in Presets
            items(EqPreset.entries.toTypedArray()) { preset ->
                val isSelected = selectedCustomPresetId == null && selectedPreset == preset && eqEnabled && isExternalAudioConnected
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isExternalAudioConnected) {
                            showSpeakerWarningToast()
                        } else {
                            onPresetSelect(preset)
                        }
                    },
                    enabled = isExternalAudioConnected,
                    label = { Text(preset.displayName, fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EqNeonGreen,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = isExternalAudioConnected,
                        selected = isSelected,
                        borderColor = DarkCardBorder,
                        selectedBorderColor = EqNeonGreen
                    ),
                    modifier = Modifier.testTag("preset_${preset.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5-Band Equalizer Sliders Header & Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FREQUENCY BANDS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            )

            OutlinedButton(
                onClick = { onPresetSelect(EqPreset.FLAT) },
                enabled = eqEnabled && isExternalAudioConnected,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EqNeonGreen,
                    disabledContentColor = TextMuted.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (eqEnabled && isExternalAudioConnected) EqNeonGreen.copy(alpha = 0.5f) else DarkCardBorder
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sliders Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0..4) {
                    val gainDb = bandGains.getOrElse(i) { 0f }
                    val label = bandLabels[i]

                    Column(
                        modifier = Modifier.width(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${if (gainDb > 0) "+" else ""}${gainDb.toInt()}dB",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (gainDb != 0f && eqEnabled && isExternalAudioConnected) EqNeonGreen else TextMuted,
                                fontSize = 11.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        VerticalEqSlider(
                            value = gainDb,
                            onValueChange = { newGain -> onBandGainChange(i, newGain) },
                            valueRange = -12f..12f,
                            enabled = eqEnabled && isExternalAudioConnected,
                            modifier = Modifier.testTag("eq_band_slider_$i")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Save Custom Preset Dialog
        if (showSavePresetDialog) {
            AlertDialog(
                onDismissRequest = { showSavePresetDialog = false },
                title = {
                    Text(
                        text = "Save Custom Preset",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Save current 5-band frequency sliders and bass boost level as a reusable preset:",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            label = { Text("Preset Name", color = TextSecondary) },
                            placeholder = { Text("e.g. Deep Bass / Car Stereo", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EqNeonGreen,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = EqNeonGreen
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_preset_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = DarkSurfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Current Settings Preview:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = EqNeonGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val bandSummary = (0..4).joinToString(" • ") { idx ->
                                    val gain = bandGains.getOrElse(idx) { 0f }.toInt()
                                    val sign = if (gain > 0) "+" else ""
                                    val lbl = when (idx) {
                                        0 -> "60Hz"
                                        1 -> "230Hz"
                                        2 -> "910Hz"
                                        3 -> "3.6k"
                                        else -> "14k"
                                    }
                                    "$lbl: $sign${gain}dB"
                                }
                                Text(
                                    text = bandSummary,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Bass Boost: ${(bassBoostLevel * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = presetNameInput.trim().ifEmpty { "My Preset" }
                            onSaveCustomPreset(name, bandGains, bassBoostLevel)
                            showSavePresetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EqNeonGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.testTag("confirm_save_preset_btn")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSavePresetDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Delete Preset Confirmation Dialog
        presetToDelete?.let { preset ->
            AlertDialog(
                onDismissRequest = { presetToDelete = null },
                title = {
                    Text(
                        text = "Delete Preset",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete '${preset.name}'?",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteCustomPreset(preset.id)
                            presetToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkRedPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { presetToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun VerticalEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val trackHeight = 170.dp
    val handleHeight = 22.dp
    val padding = 11.dp // handleHeight / 2

    Box(
        modifier = modifier
            .width(48.dp)
            .height(trackHeight + handleHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val totalTrackPx = trackHeight.toPx()
                    if (totalTrackPx > 0) {
                        val touchYOnTrack = (change.position.y - padding.toPx()).coerceIn(0f, totalTrackPx)
                        val fraction = (1f - (touchYOnTrack / totalTrackPx)).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val totalTrackPx = trackHeight.toPx()
                    if (totalTrackPx > 0) {
                        val touchYOnTrack = (offset.y - padding.toPx()).coerceIn(0f, totalTrackPx)
                        val fraction = (1f - (touchYOnTrack / totalTrackPx)).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val totalRange = valueRange.endInclusive - valueRange.start
        val fraction = if (totalRange > 0) ((value - valueRange.start) / totalRange).coerceIn(0f, 1f) else 0.5f

        // Track container
        Box(
            modifier = Modifier
                .padding(top = padding)
                .width(10.dp)
                .height(trackHeight)
                .clip(RoundedCornerShape(5.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(5.dp))
        )

        // Center 0dB indicator line
        Box(
            modifier = Modifier
                .offset(y = padding + (trackHeight / 2) - 1.dp)
                .width(22.dp)
                .height(2.dp)
                .background(if (enabled) TextMuted else TextMuted.copy(alpha = 0.3f))
        )

        // Active Gain Fill
        if (fraction >= 0.5f) {
            val fillFraction = fraction - 0.5f
            val fillHeight = trackHeight * fillFraction
            Box(
                modifier = Modifier
                    .offset(y = padding + (trackHeight * 0.5f) - fillHeight)
                    .width(10.dp)
                    .height(fillHeight)
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(if (enabled) EqNeonGreen else TextMuted)
            )
        } else {
            val fillFraction = 0.5f - fraction
            val fillHeight = trackHeight * fillFraction
            Box(
                modifier = Modifier
                    .offset(y = padding + (trackHeight * 0.5f))
                    .width(10.dp)
                    .height(fillHeight)
                    .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                    .background(if (enabled) EqNeonGreen.copy(alpha = 0.6f) else TextMuted.copy(alpha = 0.4f))
            )
        }

        // Fader / Thumb Handle
        val handleTop = trackHeight * (1f - fraction)
        Box(
            modifier = Modifier
                .offset(y = handleTop)
                .width(38.dp)
                .height(handleHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (enabled) EqNeonGreen else DarkSurfaceVariant
                )
                .border(
                    1.dp,
                    if (enabled) Color.White.copy(alpha = 0.5f) else DarkCardBorder,
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .background(if (enabled) Color.White else TextMuted)
            )
        }
    }
}
