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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.astramesh.domain.usecase.EmergencyBroadcastUseCase
import com.astramesh.services.VoiceEngineManager
import com.astramesh.ui.i18n.AppStrings
import com.astramesh.ui.i18n.LocalAppStrings
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

enum class EmergencyBroadcastStage {
    READY,
    STAGE2_VOICE_RECORDING,
    BROADCAST_COMPLETED
}

/**
 * Canonical Two-Stage Emergency SOS Action Component.
 * Unified across HomeScreen (Emergency view) and EmergencyScreen.
 *
 * Sequence:
 * 1. Hold for 2 seconds -> Arms & transmits Stage 1 Instant SOS Beacon (Priority: EMERGENCY, TTL: 15)
 * 2. Auto-switches to Stage 2 Voice Recording -> Captures up to 10 seconds of mic audio/STT
 * 3. Auto-transmits Stage 2 Voice Briefing packet across mesh upon timeout or "Transmit Now"
 */
@Composable
fun EmergencySosActionArea(
    emergencyBroadcastUseCase: EmergencyBroadcastUseCase,
    voiceEngineManager: VoiceEngineManager?,
    selectedLanguage: Language,
    modifier: Modifier = Modifier,
    onStatusMessage: ((String) -> Unit)? = null,
    onStageChanged: ((EmergencyBroadcastStage) -> Unit)? = null
) {
    val strings = AppStrings.forLanguage(selectedLanguage.code)
    val scope = rememberCoroutineScope()

    var currentStage by remember { mutableStateOf(EmergencyBroadcastStage.READY) }
    var isHoldingSos by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var recordingTimerJob by remember { mutableStateOf<Job?>(null) }
    var recordingCountdown by remember { mutableIntStateOf(10) }

    var liveTranscript by remember { mutableStateOf("") }
    var liveRms by remember { mutableIntStateOf(0) }

    fun updateStage(stage: EmergencyBroadcastStage) {
        currentStage = stage
        onStageChanged?.invoke(stage)
    }

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
            val status = "Transcribing & transmitting voice briefing..."
            onStatusMessage?.invoke(status)
            val finalTranscript = voiceEngineManager?.stopSttAndAwaitResult() ?: liveTranscript
            val textToSend = if (finalTranscript.isNotBlank()) finalTranscript else selectedLanguage.getDefaultEmergencyText()

            AstraLog.d("EmergencySos", "Stage 2 Voice Briefing: '$textToSend'")
            emergencyBroadcastUseCase.sendVoiceBriefing(
                transcript = textToSend,
                language = selectedLanguage
            )

            updateStage(EmergencyBroadcastStage.BROADCAST_COMPLETED)
            onStatusMessage?.invoke("EMERGENCY BROADCAST ACTIVE: Beacon & Briefing Relayed Across Mesh!")
        }
    }

    fun startStage2VoiceRecording() {
        updateStage(EmergencyBroadcastStage.STAGE2_VOICE_RECORDING)
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

    DisposableEffect(Unit) {
        onDispose {
            holdJob?.cancel()
            recordingTimerJob?.cancel()
            voiceEngineManager?.stopStt()
        }
    }

    Box(
        modifier = modifier,
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
                        text = if (isHoldingSos) strings.holdingForSos else strings.holdForSos,
                        color = if (isHoldingSos) AstraCrimson else AstraTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
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
                            .pointerInput(selectedLanguage) {
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
                                                AstraLog.d("EmergencySos", "Stage 1: Dispatching Immediate SOS Beacon in ${selectedLanguage.englishName}...")
                                                onStatusMessage?.invoke("STAGE 1: Immediate SOS Beacon Dispatched to Mesh!")
                                                isHoldingSos = false
                                                holdProgress = 0f

                                                // STAGE 1: IMMEDIATE DISTRESS PACKET
                                                voiceEngineManager?.playEmergencyBeacon(selectedLanguage)
                                                emergencyBroadcastUseCase.sendImmediateSosBeacon(
                                                    language = selectedLanguage
                                                )

                                                // STAGE 2: AUTOMATIC VOICE BRIEFING CAPTURE
                                                startStage2VoiceRecording()
                                            }
                                        }

                                        tryAwaitRelease()

                                        // Released early before 2s
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
                            Text(
                                text = "2 SECONDS",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
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
                        text = strings.stage2RecordingTitle,
                        color = AstraCrimson,
                        fontSize = 15.sp,
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
                            .height(80.dp),
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
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        text = strings.twoStageSosActive,
                        color = AstraEmerald,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.twoStageSosActiveDesc,
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
                            onClick = { startStage2VoiceRecording() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Briefing")
                        }

                        Button(
                            onClick = { updateStage(EmergencyBroadcastStage.READY) },
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
}
