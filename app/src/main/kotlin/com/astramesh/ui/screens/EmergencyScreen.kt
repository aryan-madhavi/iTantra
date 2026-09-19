package com.astramesh.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.core.NodeId
import com.astramesh.core.TranslationSettings
import com.astramesh.domain.model.Message
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.EmergencyBroadcastUseCase
import com.astramesh.services.VoiceEngineManager
import com.astramesh.ui.components.TacticalTranslationToggle
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCrimson
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraSurfaceVariant
import com.astramesh.ui.theme.AstraTextPrimary
import com.astramesh.ui.theme.AstraTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyScreen(
    localNodeId: NodeId,
    emergencyBroadcastUseCase: EmergencyBroadcastUseCase,
    messageRepository: MessageRepository,
    meshRepository: MeshRepository? = null,
    voiceEngineManager: VoiceEngineManager?,
    onBackClicked: (() -> Unit)? = null
) {
    val isTranslationEnabled by TranslationSettings.isTranslationEnabled.collectAsState()
    val selectedLanguage by (meshRepository?.preferredLanguage ?: remember { MutableStateFlow(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }).collectAsState(initial = voiceEngineManager?.preferredLanguage ?: Language.HINDI)
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val emergencyMessages by messageRepository.observeMessages(com.astramesh.core.ChatId("chat_emergency_broadcast"))
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .statusBarsPadding()
            .padding(14.dp)
    ) {
        // -------------------------------------------------------------
        // 1. TOP TACTICAL TELEMETRY HEADER & CONTROLS (Row 1)
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(AstraCrimson)
                    )
                    Text(
                        text = "SOS BEACON",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = AstraTextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Node ${localNodeId.toHex().take(8)} • MAXIMUM PRIORITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraCrimson
                )
            }

            // Language Selector Chip & Dropdown and Global Translation Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TacticalTranslationToggle(
                    isEnabled = isTranslationEnabled,
                    onToggle = { TranslationSettings.toggleTranslation() }
                )

                Box {
                    AssistChip(
                        onClick = { languageDropdownExpanded = true },
                        label = { Text(selectedLanguage.nativeName, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = AstraSurfaceVariant,
                            labelColor = AstraCrimson,
                            leadingIconContentColor = AstraCrimson
                        )
                    )

                    DropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        Language.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.nativeName} (${lang.englishName})") },
                                onClick = {
                                    languageDropdownExpanded = false
                                    meshRepository?.setPreferredLanguage(lang)
                                    AstraLog.d("EmergencyScreen", "SOS Language changed to ${lang.englishName}")
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))
        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // 2. DISTINCT NODE TELEMETRY & FLOOD STATUS (Row 2)
        // -------------------------------------------------------------

        // Warning Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AstraCrimson.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AstraCrimson, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Hold for 2s to broadcast an immediate SOS beacon and auto-record a 10s voice briefing relayed across the mesh.",
                    color = AstraTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Action Area (Shared Canonical Two-Stage SOS)
        EmergencySosActionArea(
            emergencyBroadcastUseCase = emergencyBroadcastUseCase,
            voiceEngineManager = voiceEngineManager,
            selectedLanguage = selectedLanguage,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onStatusMessage = { statusMessage = it }
        )

        // Recent Distress Broadcasts Log Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = AstraSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Sector Distress Broadcasts Log",
                    color = AstraCrimson,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (emergencyMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No active distress alerts in sector.", color = AstraTextSecondary, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(emergencyMessages.takeLast(5).reversed()) { msg ->
                            com.astramesh.ui.components.TacticalMessageLogItem(
                                message = msg,
                                localNodeId = localNodeId,
                                localLanguage = selectedLanguage,
                                onReplay = {
                                    val cleanText = msg.content.replace(Regex("^\\[.*?\\]:?\\s*"), "").trim()
                                    voiceEngineManager?.speakText(
                                        text = cleanText,
                                        language = selectedLanguage,
                                        isEmergency = true
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
