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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
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
import com.astramesh.domain.model.Message
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

enum class EmergencyBroadcastStage {
    READY,
    STAGE2_VOICE_RECORDING,
    BROADCAST_COMPLETED
}

@Composable
fun EmergencyScreen(
    localNodeId: NodeId,
    emergencyBroadcastUseCase: EmergencyBroadcastUseCase,
    messageRepository: MessageRepository,
    meshRepository: MeshRepository? = null,
    voiceEngineManager: VoiceEngineManager?,
    onBackClicked: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var currentStage by remember { mutableStateOf(EmergencyBroadcastStage.READY) }
    var isHoldingSos by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }
    var recordingCountdown by remember { mutableIntStateOf(10) }

    var selectedLanguage by remember { mutableStateOf(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var liveTranscript by remember { mutableStateOf("") }
    var liveRms by remember { mutableIntStateOf(0) }

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

    val recordingPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_scale"
    )

    // Helper to complete Stage 2 and broadcast briefing
    fun transmitVoiceBriefing() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        scope.launch {
            statusMessage = "Transcribing & transmitting voice briefing..."
            val finalTranscript = voiceEngineManager?.stopSttAndAwaitResult() ?: liveTranscript
            val textToSend = if (finalTranscript.isNotBlank()) finalTranscript else selectedLanguage.getDefaultEmergencyText()

            AstraLog.d("EmergencyScreen", "Stage 2 Voice Briefing: '$textToSend'")
            emergencyBroadcastUseCase.sendVoiceBriefing(
                transcript = textToSend,
                language = selectedLanguage
            )

            currentStage = EmergencyBroadcastStage.BROADCAST_COMPLETED
            statusMessage = "EMERGENCY BROADCAST ACTIVE: Beacon & Briefing Relayed Across Mesh!"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holdJob?.cancel()
            recordingTimerJob?.cancel()
            voiceEngineManager?.stopStt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .padding(14.dp)
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
                            .background(AstraCrimson)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "iTANTRA SOS BEACON",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = AstraTextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Node ${localNodeId.toHex().take(8)} • MAXIMUM PRIORITY FLOOD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraCrimson
                )
            }

            // Language Selector Chip & Dropdown
            Box {
                androidx.compose.material3.AssistChip(
                    onClick = { languageDropdownExpanded = true },
                    label = { Text(selectedLanguage.nativeName, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
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

        Spacer(modifier = Modifier.height(12.dp))

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
                    text = "Stage 1 immediately floods SOS beacon without delay. Stage 2 automatically records, transcribes, and broadcasts your voice briefing.",
                    color = AstraTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Action Area based on current Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (currentStage) {
                EmergencyBroadcastStage.READY -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ARE YOU IN IMMEDIATE DANGER?",
                            color = AstraTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isHoldingSos) "Keep holding for Stage 1 Instant Beacon..." else "Hold for 2 seconds to activate 2-Stage SOS",
                            color = if (isHoldingSos) AstraCrimson else AstraTextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

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
                                                    AstraLog.d("EmergencyScreen", "Stage 1: Dispatching Immediate SOS Beacon in ${selectedLanguage.englishName}...")
                                                    statusMessage = "STAGE 1: Immediate SOS Beacon Dispatched to Mesh!"
                                                    isHoldingSos = false
                                                    holdProgress = 0f

                                                    // -------------------------------------------------------------
                                                    // STAGE 1: IMMEDIATE DISTRESS PACKET (NO MIC / NO STT WAIT)
                                                    // -------------------------------------------------------------
                                                    emergencyBroadcastUseCase.sendImmediateSosBeacon(
                                                        language = selectedLanguage
                                                    )

                                                    // -------------------------------------------------------------
                                                    // STAGE 2: AUTOMATIC VOICE BRIEFING CAPTURE
                                                    // -------------------------------------------------------------
                                                    currentStage = EmergencyBroadcastStage.STAGE2_VOICE_RECORDING
                                                    liveTranscript = ""
                                                    recordingCountdown = 10

                                                    voiceEngineManager?.startStt(
                                                        language = selectedLanguage,
                                                        onRmsChanged = { rms -> liveRms = rms },
                                                        onResult = { transcript -> liveTranscript = transcript }
                                                    )

                                                    recordingTimerJob?.cancel()
                                                    recordingTimerJob = scope.launch {
                                                        while (isActive && recordingCountdown > 0) {
                                                            delay(1000L)
                                                            recordingCountdown--
                                                        }
                                                        if (isActive && currentStage == EmergencyBroadcastStage.STAGE2_VOICE_RECORDING) {
                                                            transmitVoiceBriefing()
                                                        }
                                                    }
                                                }
                                            }

                                            tryAwaitRelease()

                                            // Released early
                                            isHoldingSos = false
                                            holdProgress = 0f
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
                        }
                    }
                }

                EmergencyBroadcastStage.STAGE2_VOICE_RECORDING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "STAGE 2: RECORDING VOICE BRIEFING",
                            color = AstraCrimson,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Speak your situation, location, and needs.\nAuto-transmitting in ${recordingCountdown}s...",
                            color = AstraTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Pulsing Mic recording indicator
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(recordingPulse)
                                .clip(CircleShape)
                                .background(AstraCrimson)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Recording Briefing",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Live Transcription Display Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            colors = CardDefaults.cardColors(containerColor = AstraSurface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (liveTranscript.isNotBlank()) "\"$liveTranscript\"" else "Listening... (Speak into microphone)",
                                    color = if (liveTranscript.isNotBlank()) AstraCyan else AstraTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { transmitVoiceBriefing() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AstraCrimson),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TRANSMIT NOW", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                EmergencyBroadcastStage.BROADCAST_COMPLETED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = AstraEmerald,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "TWO-STAGE SOS ACTIVE",
                            color = AstraEmerald,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Stage 1 Beacon and Stage 2 Voice Briefing have been broadcasted across the mesh network with maximum TTL.",
                            color = AstraTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentStage = EmergencyBroadcastStage.STAGE2_VOICE_RECORDING
                                    liveTranscript = ""
                                    recordingCountdown = 10
                                    voiceEngineManager?.startStt(
                                        language = selectedLanguage,
                                        onRmsChanged = { rms -> liveRms = rms },
                                        onResult = { transcript -> liveTranscript = transcript }
                                    )
                                    recordingTimerJob?.cancel()
                                    recordingTimerJob = scope.launch {
                                        while (isActive && recordingCountdown > 0) {
                                            delay(1000L)
                                            recordingCountdown--
                                        }
                                        if (isActive && currentStage == EmergencyBroadcastStage.STAGE2_VOICE_RECORDING) {
                                            transmitVoiceBriefing()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Briefing")
                            }

                            Button(
                                onClick = { currentStage = EmergencyBroadcastStage.READY },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AstraSurfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Dismiss", color = AstraTextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Recent Distress Broadcasts Log Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
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
