package com.astramesh.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.EmergencyBroadcastUseCase
import com.astramesh.services.VoiceEngineManager
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
    onBackClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isHoldingSos by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var selectedLanguage by remember { mutableStateOf(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var lastBroadcastStatus by remember { mutableStateOf<String?>(null) }
    var liveTranscript by remember { mutableStateOf("") }
    var liveRms by remember { mutableStateOf(0) }

    val emergencyMessages by messageRepository.observeMessages(com.astramesh.core.ChatId("chat_emergency_broadcast"))
        .collectAsState(initial = emptyList())

    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AstraTextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EMERGENCY BEACON",
                    color = AstraCrimson,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "HIGH-PRIORITY MESH FLOODING",
                    color = AstraTextSecondary,
                    fontSize = 11.sp
                )
            }

            // Language Selector Chip & Dropdown
            Box {
                FilterChip(
                    selected = true,
                    onClick = { languageDropdownExpanded = true },
                    label = { Text(selectedLanguage.nativeName, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AstraSurfaceVariant,
                        labelColor = AstraCyan
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
                                selectedLanguage = lang
                                languageDropdownExpanded = false
                                voiceEngineManager?.preferredLanguage = lang
                                meshRepository?.setPreferredLanguage(lang)
                                AstraLog.d("EmergencyScreen", "SOS Language changed to ${lang.englishName}")
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                Icon(androidx.compose.material.icons.Icons.Default.Warning, contentDescription = null, tint = AstraCrimson, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Distress alerts override DND, gain exclusive audio focus, and trigger siren + vibration on all listening devices in mesh range.",
                    color = AstraTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center Big Hold-To-Confirm SOS Trigger
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ARE YOU IN IMMEDIATE DANGER?",
                    color = AstraTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHoldingSos) "Keep holding to broadcast..." else "Hold for 2 seconds to broadcast SOS",
                    color = if (isHoldingSos) AstraCrimson else AstraTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(if (isHoldingSos) 1.15f else pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AstraCrimson, AstraCrimson.copy(alpha = 0.5f))
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldingSos = true
                                    holdProgress = 0f
                                    liveTranscript = ""
                                    voiceEngineManager?.startStt(
                                        language = selectedLanguage,
                                        onRmsChanged = { rms -> liveRms = rms }
                                    ) { transcript ->
                                        if (transcript.isNotBlank()) {
                                            liveTranscript = transcript
                                        }
                                    }

                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        val totalMs = 2000L
                                        val stepMs = 50L
                                        var elapsed = 0L
                                        while (isActive && elapsed < totalMs) {
                                            delay(stepMs)
                                            elapsed += stepMs
                                            holdProgress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                                        }

                                        if (isActive && holdProgress >= 1f) {
                                            AstraLog.d("EmergencyScreen", "SOS Triggered! Broadcasting distress signal in ${selectedLanguage.englishName}...")
                                            lastBroadcastStatus = "Broadcasting Emergency SOS..."
                                            val recognized = voiceEngineManager?.stopSttAndAwaitResult(timeoutMs = 800L) ?: ""
                                            val alertText = if (recognized.isNotBlank()) {
                                                recognized
                                            } else if (liveTranscript.isNotBlank()) {
                                                liveTranscript
                                            } else {
                                                selectedLanguage.getDefaultEmergencyText()
                                            }
                                            emergencyBroadcastUseCase(
                                                alertMessage = alertText,
                                                language = selectedLanguage
                                            )
                                            lastBroadcastStatus = "Emergency SOS Broadcast Dispatched: '$alertText'"
                                            isHoldingSos = false
                                            holdProgress = 0f
                                        }
                                    }

                                    tryAwaitRelease()

                                    // Released early
                                    isHoldingSos = false
                                    holdProgress = 0f
                                    voiceEngineManager?.stopStt()
                                    holdJob?.cancel()
                                    holdJob = null
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Emergency,
                            contentDescription = "SOS",
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HOLD SOS",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (isHoldingSos) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier
                            .width(180.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AstraCrimson,
                        trackColor = AstraSurfaceVariant
                    )
                    if (liveTranscript.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"$liveTranscript\"",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )
                    }
                }

                lastBroadcastStatus?.let { status ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = status,
                        color = AstraEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Recent Distress Broadcasts
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(containerColor = AstraSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Distress Broadcasts Log",
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AstraCrimson.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Emergency, contentDescription = null, tint = AstraCrimson, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = msg.content,
                                        color = AstraTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                Text(
                                    text = timeFormat.format(Date(msg.timestamp)),
                                    color = AstraTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
