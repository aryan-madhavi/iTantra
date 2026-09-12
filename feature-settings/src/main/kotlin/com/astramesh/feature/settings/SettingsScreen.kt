package com.astramesh.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.ui.components.AstraTopBar
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCrimson
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraOutline
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraSurfaceVariant
import com.astramesh.ui.theme.AstraTextPrimary
import com.astramesh.ui.theme.AstraTextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onShowQrClicked: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val displayName by viewModel.displayName.collectAsState()
    val permanentId by viewModel.permanentNodeId.collectAsState()
    val fingerprint by viewModel.publicKeyFingerprint.collectAsState()
    val trustStatus by viewModel.trustStatus.collectAsState()
    val meshStatus by viewModel.meshStatus.collectAsState()
    val vadConfig by viewModel.sttVadConfig.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // -------------------------------------------------------------
        // CONSISTENT TOP TACTICAL TELEMETRY HEADER
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(AstraCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "iTANTRA SETTINGS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = AstraTextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Node ${permanentId?.toHex()?.take(8) ?: "OFFLINE"} • CONFIG & IDENTITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
            // Cryptographic Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AstraCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = AstraCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Cryptographic Identity",
                                color = AstraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AstraEmerald,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = trustStatus,
                                    color = AstraEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Permanent Device ID
                    Text(
                        text = "Permanent Device ID",
                        color = AstraTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = permanentId?.toHex() ?: "Initializing...",
                        color = AstraCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Public Key Fingerprint
                    if (fingerprint.isNotEmpty()) {
                        Text(
                            text = "Public Key Fingerprint",
                            color = AstraTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = fingerprint,
                            color = AstraTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Display Name
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = viewModel::updateDisplayName,
                        label = { Text("Display Name", color = AstraTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AstraTextPrimary,
                            unfocusedTextColor = AstraTextPrimary,
                            focusedBorderColor = AstraCyan,
                            unfocusedBorderColor = AstraOutline
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Show Pairing QR Code
                    Button(
                        onClick = {
                            AstraLog.d("SettingsScreen", "UI Show pairing QR clicked")
                            onShowQrClicked()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show Pairing QR Code", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Export / Import Identity Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val exported = viewModel.exportIdentity()
                                clipboardManager.setText(AnnotatedString(exported))
                                Toast.makeText(context, "Permanent Identity copied to clipboard", Toast.LENGTH_SHORT).show()
                                AstraLog.d("SettingsScreen", "UI Identity exported to clipboard")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = AstraCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", color = AstraCyan, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Identity import: Read pairing QR or file", Toast.LENGTH_SHORT).show()
                                AstraLog.d("SettingsScreen", "UI Import identity requested")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = AstraEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", color = AstraEmerald, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.resetIdentity {
                                    Toast.makeText(context, "Cryptographic Identity Reset", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AstraCrimson, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", color = AstraCrimson, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Network Diagnostics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LIVE MESH METRICS",
                        color = AstraCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    MetricRow("Packets Sent", "${meshStatus.packetsSent}")
                    MetricRow("Packets Relayed", "${meshStatus.packetsRelayed}")
                    MetricRow("Packets Received", "${meshStatus.packetsReceived}")
                    MetricRow("Active BLE Links", "${meshStatus.activeConnectionsCount}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // -----------------------------------------------------------
            // VOICE KEYWORD / HANDS-FREE SETTINGS CARD
            // -----------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AstraCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = AstraCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "VOICE KEYWORD / HANDS-FREE",
                                color = AstraCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Wake-word detection and capture window",
                                color = AstraTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = vadConfig.enableKeywordDetection,
                            onCheckedChange = { viewModel.setEnableKeywordDetection(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AstraCyan,
                                checkedTrackColor = AstraCyan.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Wake-word sensitivity (0.01..0.50)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wake-Word Sensitivity", color = AstraTextSecondary, fontSize = 12.sp)
                        Text(
                            "%.2f".format(vadConfig.wakeWordSensitivity),
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vadConfig.wakeWordSensitivity,
                        onValueChange = { viewModel.setWakeWordSensitivity(it) },
                        valueRange = 0.01f..0.50f,
                        colors = SliderDefaults.colors(
                            thumbColor = AstraCyan,
                            activeTrackColor = AstraCyan,
                            inactiveTrackColor = AstraOutline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Command capture timeout (1000..10000 ms)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Command Capture Timeout", color = AstraTextSecondary, fontSize = 12.sp)
                        Text(
                            "${vadConfig.commandCaptureTimeoutMs} ms",
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vadConfig.commandCaptureTimeoutMs.toFloat(),
                        onValueChange = { viewModel.setCommandCaptureTimeoutMs(it.toLong()) },
                        valueRange = 1000f..10000f,
                        steps = 17, // 500ms increments
                        colors = SliderDefaults.colors(
                            thumbColor = AstraCyan,
                            activeTrackColor = AstraCyan,
                            inactiveTrackColor = AstraOutline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // -----------------------------------------------------------
            // ADVANCED VAD & STT TUNING CARD
            // -----------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AstraSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AstraCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = AstraCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "ADVANCED: VAD & STT TUNING",
                                color = AstraCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Voice activity detection parameters",
                                color = AstraTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // VAD Threshold (0.10..0.90)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VAD Threshold", color = AstraTextSecondary, fontSize = 12.sp)
                        Text(
                            "%.2f  (RMS ≈ ${vadConfig.effectiveRmsThreshold})".format(vadConfig.vadThreshold),
                            color = AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vadConfig.vadThreshold,
                        onValueChange = { viewModel.setVadThreshold(it) },
                        valueRange = 0.10f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = AstraCyan,
                            activeTrackColor = AstraCyan,
                            inactiveTrackColor = AstraOutline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // VAD Minimum Silence (100..500 ms)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VAD Min Silence", color = AstraTextSecondary, fontSize = 12.sp)
                        Text(
                            "${vadConfig.vadMinSilenceMs} ms",
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vadConfig.vadMinSilenceMs.toFloat(),
                        onValueChange = { viewModel.setVadMinSilenceMs(it.toLong()) },
                        valueRange = 100f..500f,
                        steps = 7, // 50ms increments
                        colors = SliderDefaults.colors(
                            thumbColor = AstraCyan,
                            activeTrackColor = AstraCyan,
                            inactiveTrackColor = AstraOutline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // VAD Minimum Speech (100..500 ms)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VAD Min Speech", color = AstraTextSecondary, fontSize = 12.sp)
                        Text(
                            "${vadConfig.vadMinSpeechMs} ms",
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vadConfig.vadMinSpeechMs.toFloat(),
                        onValueChange = { viewModel.setVadMinSpeechMs(it.toLong()) },
                        valueRange = 100f..500f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = AstraCyan,
                            activeTrackColor = AstraCyan,
                            inactiveTrackColor = AstraOutline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // VAD Speech Padding (0..100 ms)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VAD Speech Padding", color = AstraTextSecondary, fontSize = 12.sp)
                        Text(
                            "${vadConfig.vadSpeechPadMs} ms",
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vadConfig.vadSpeechPadMs.toFloat(),
                        onValueChange = { viewModel.setVadSpeechPadMs(it.toLong()) },
                        valueRange = 0f..100f,
                        steps = 9, // 10ms increments
                        colors = SliderDefaults.colors(
                            thumbColor = AstraCyan,
                            activeTrackColor = AstraCyan,
                            inactiveTrackColor = AstraOutline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Panic Wipe Button
            Button(
                onClick = {
                    viewModel.emergencyWipeAllData {
                        Toast.makeText(context, "All keys wiped", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AstraCrimson),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("PANIC: Wipe All Keys & Messages", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }


@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AstraTextSecondary, fontSize = 14.sp)
        Text(value, color = AstraTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

