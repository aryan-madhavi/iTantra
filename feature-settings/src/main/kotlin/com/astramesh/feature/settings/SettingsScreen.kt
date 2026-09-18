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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.astramesh.core.Language
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
    val preloadLang by viewModel.preloadLanguage.collectAsState()
    val meshStatus by viewModel.meshStatus.collectAsState()

    var languageDropdownExpanded by remember { mutableStateOf(false) }

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
                        text = "SETTINGS",
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

            // AI Speech & Neural TTS Configuration Card
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
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = AstraCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Speech & Neural TTS Engine",
                                color = AstraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Single-Model Resident Memory Optimization",
                                color = AstraEmerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Startup Default TTS Preload Model",
                        color = AstraTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Choose which offline MMS-VITS neural voice model is preloaded on startup. The app enforces a single resident model in RAM (releasing old sessions on language switch).",
                        color = AstraTextSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box {
                        OutlinedButton(
                            onClick = { languageDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCyan)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${preloadLang.nativeName} (${preloadLang.englishName}) [${preloadLang.code}]",
                                    color = AstraTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Language", tint = AstraCyan)
                            }
                        }

                        DropdownMenu(
                            expanded = languageDropdownExpanded,
                            onDismissRequest = { languageDropdownExpanded = false }
                        ) {
                            Language.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${lang.nativeName} (${lang.englishName})", fontWeight = if (lang == preloadLang) FontWeight.Bold else FontWeight.Normal)
                                            if (lang == preloadLang) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("[Active]", color = AstraCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.updatePreloadLanguage(lang)
                                        languageDropdownExpanded = false
                                        Toast.makeText(context, "Default TTS model set to ${lang.englishName}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
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

