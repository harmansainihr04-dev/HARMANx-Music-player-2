package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberElectricPurple
import com.example.ui.theme.CyberNeonCyan
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import com.example.ui.theme.FlacBadgeGreen

@Composable
fun SettingsScreen(
    isGaplessEnabled: Boolean,
    onToggleGapless: () -> Unit,
    onScanStorageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
        Column {
            Text(
                text = "AUDIO & ENGINE SETTINGS",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = CyberNeonCyan,
                    letterSpacing = 2.sp
                )
            )
            Text(
                text = "Gapless Playback & Hardware Configuration",
                style = MaterialTheme.typography.labelSmall.copy(color = CyberTextSecondary)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Gapless Playback Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = CyberElectricPurple,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Gapless Playback",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary
                            )
                        )
                        Text(
                            text = "Eliminates inter-track silence gaps during playback transitions",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CyberTextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Switch(
                    checked = isGaplessEnabled,
                    onCheckedChange = { onToggleGapless() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberElectricPurple,
                        checkedTrackColor = CyberSurfaceVariant
                    ),
                    modifier = Modifier.testTag("gapless_setting_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Engine Technical Specifications Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = CyberNeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUDIO ENGINE SPECIFICATIONS",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyberNeonCyan,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SpecRow("Decoder Engine", "Native Audio / Low Latency PCM")
                SpecRow("Spatial Audio Effects", "360° Surround Sound & Reverb")
                SpecRow("Supported Audio Formats", ".MP3, .FLAC, .WAV, .M4A, .AAC, .OGG")
                SpecRow("Equalizer Engine", "5-Band Hardware / DSP Parametric FX")
                SpecRow("Audio Session Buffer", "Low Latency Dual-Buffer")
                SpecRow("Android Auto Integration", "MediaBrowserService (Active)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Android Auto Support Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CyberNeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ANDROID AUTO & VEHICLE READY",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyberNeonCyan,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Fully enabled for Android Auto car displays, steering wheel controls, and Google Assistant voice commands while driving.",
                    style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSecondary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                SpecRow("Car Dashboard Display", "Enabled ✓")
                SpecRow("Steering Wheel Controls", "Enabled ✓")
                SpecRow("Voice Media Commands", "Enabled ✓")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Storage Scanner Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STORAGE SCANNER",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80),
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scan your internal and external storage to find and import music and audio tracks.",
                    style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onScanStorageClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22C55E).copy(alpha = 0.15f),
                        contentColor = Color(0xFF4ADE80)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_rescan_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF4ADE80))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rescan Device Storage", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = CyberSurfaceDark
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyberTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "HARMANX Music Player v1.0 • Offline High-Res MP3 & Audio Engine",
                    style = MaterialTheme.typography.labelSmall.copy(color = CyberTextMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(color = CyberTextSecondary))
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(color = CyberTextPrimary, fontWeight = FontWeight.Bold))
    }
}
