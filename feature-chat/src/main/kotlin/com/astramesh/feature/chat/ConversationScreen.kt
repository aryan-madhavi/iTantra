package com.astramesh.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.core.NodeId
import com.astramesh.core.VoiceMode
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.ui.components.PulsingStatusDot
import com.astramesh.ui.i18n.appStrings
import com.astramesh.ui.theme.AstraAmber
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCrimson
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraOutline
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraSurfaceVariant
import com.astramesh.ui.theme.AstraTextPrimary
import com.astramesh.ui.theme.AstraTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBackClicked: () -> Unit,
    localNodeId: NodeId
) {
    val strings = appStrings()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isRecordingPtt by viewModel.isRecordingPtt.collectAsState()
    val currentVoiceMode by viewModel.currentVoiceMode.collectAsState()
    val isContinuousModeActive by viewModel.isContinuousModeActive.collectAsState()
    val continuousState by viewModel.continuousState.collectAsState()
    val liveRms by viewModel.liveRms.collectAsState()
    val recordingDurationSec by viewModel.recordingDurationSec.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val isReceiverSpeaking by viewModel.isReceiverSpeaking.collectAsState()

    var showSosConfirmDialog by remember { mutableStateOf(false) }

    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AstraCrimson) },
            title = { Text(strings.emergencyAlertTitle, color = AstraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    strings.emergencyAlertDesc,
                    color = AstraTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosConfirmDialog = false
                        viewModel.sendEmergencySos("EMERGENCY SOS: Immediate assistance required")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCrimson)
                ) {
                    Text(strings.broadcastAlertNow, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirmDialog = false }) {
                    Text(strings.cancel, color = AstraTextSecondary)
                }
            },
            containerColor = AstraSurfaceVariant
        )
    }

    val recipientDisplayName by viewModel.recipientDisplayName.collectAsState()
    val displayName = recipientDisplayName ?: "${strings.deviceLabel} ${viewModel.recipientId.toHex().take(8)}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                color = AstraTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            PulsingStatusDot(isActive = true)
                        }
                        Text(
                            text = "${strings.p2pMeshLink} • ${when (currentVoiceMode) {
                                VoiceMode.PUSH_TO_TALK -> strings.pushToTalkMode
                                VoiceMode.WALKIE_TALKIE -> strings.walkieTalkieHalfDuplex
                                VoiceMode.EMERGENCY -> strings.emergencyBroadcast
                                VoiceMode.CONTINUOUS -> strings.handsFreeContinuousSpeech
                            }}",
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.cancel,
                            tint = AstraTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AstraSurface
                )
            )
        },
        containerColor = AstraBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Voice Mode Selector Ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AstraSurfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { viewModel.setVoiceMode(VoiceMode.PUSH_TO_TALK) },
                    label = { Text(strings.pushToTalk, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (currentVoiceMode == VoiceMode.PUSH_TO_TALK) AstraCyan.copy(alpha = 0.25f) else Color.Transparent,
                        labelColor = if (currentVoiceMode == VoiceMode.PUSH_TO_TALK) AstraCyan else AstraTextSecondary,
                        leadingIconContentColor = if (currentVoiceMode == VoiceMode.PUSH_TO_TALK) AstraCyan else AstraTextSecondary
                    )
                )
                AssistChip(
                    onClick = { viewModel.setVoiceMode(VoiceMode.WALKIE_TALKIE) },
                    label = { Text(strings.walkieTalkieHalfDuplex, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (currentVoiceMode == VoiceMode.WALKIE_TALKIE) AstraEmerald.copy(alpha = 0.25f) else Color.Transparent,
                        labelColor = if (currentVoiceMode == VoiceMode.WALKIE_TALKIE) AstraEmerald else AstraTextSecondary,
                        leadingIconContentColor = if (currentVoiceMode == VoiceMode.WALKIE_TALKIE) AstraEmerald else AstraTextSecondary
                    )
                )
                AssistChip(
                    onClick = { viewModel.setVoiceMode(VoiceMode.EMERGENCY) },
                    label = { Text(strings.emergencyBroadcast, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (currentVoiceMode == VoiceMode.EMERGENCY) AstraCrimson.copy(alpha = 0.25f) else Color.Transparent,
                        labelColor = if (currentVoiceMode == VoiceMode.EMERGENCY) AstraCrimson else AstraTextSecondary,
                        leadingIconContentColor = if (currentVoiceMode == VoiceMode.EMERGENCY) AstraCrimson else AstraTextSecondary
                    )
                )
                AssistChip(
                    onClick = { viewModel.toggleContinuousMode() },
                    label = { Text(if (isContinuousModeActive) strings.sttOn else strings.sttOff, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isContinuousModeActive) AstraCyan.copy(alpha = 0.35f) else Color.Transparent,
                        labelColor = if (isContinuousModeActive) AstraCyan else AstraTextSecondary,
                        leadingIconContentColor = if (isContinuousModeActive) AstraCyan else AstraTextSecondary
                    )
                )
            }

            // Voice Control Panel (PTT / Walkie / SOS / Continuous)
            VoiceControlPanel(
                voiceMode = currentVoiceMode,
                isRecording = isRecordingPtt,
                isContinuous = isContinuousModeActive,
                continuousState = continuousState,
                liveRms = liveRms,
                recordingDurationSec = recordingDurationSec,
                liveTranscript = liveTranscript,
                isReceiverSpeaking = isReceiverSpeaking,
                onPttStart = viewModel::startPtt,
                onPttStop = viewModel::stopPtt,
                onSosClicked = { showSosConfirmDialog = true }
            )

            // Chat Message Stream
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                items(messages.reversed(), key = { it.id.value }) { message ->
                    val isFromMe = message.senderId == localNodeId
                    val senderName = if (isFromMe) strings.youLabel else (recipientDisplayName ?: "Node-${message.senderId.toHex().take(6)}")
                    MessageBubble(
                        message = message,
                        isFromMe = isFromMe,
                        senderName = senderName,
                        onPlayAudio = { viewModel.playVoiceMessage(message.content) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AstraSurface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(strings.typeMessage, color = AstraTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AstraTextPrimary,
                        unfocusedTextColor = AstraTextPrimary,
                        focusedBorderColor = AstraCyan,
                        unfocusedBorderColor = AstraOutline,
                        cursorColor = AstraCyan
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = viewModel::sendMessage,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AstraCyan)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = strings.transmitting,
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceControlPanel(
    voiceMode: VoiceMode,
    isRecording: Boolean,
    isContinuous: Boolean,
    continuousState: ContinuousState,
    liveRms: Int,
    recordingDurationSec: Int,
    liveTranscript: String,
    isReceiverSpeaking: Boolean,
    onPttStart: () -> Unit,
    onPttStop: () -> Unit,
    onSosClicked: () -> Unit
) {
    val strings = appStrings()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = AstraSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (voiceMode) {
                VoiceMode.PUSH_TO_TALK -> {
                    Text(
                        text = if (isRecording) strings.recordingHoldToTalk else strings.pushToTalkMode,
                        color = if (isRecording) AstraCrimson else AstraCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    WaveformVisualizer(rms = liveRms, isRecording = isRecording)
                    Spacer(modifier = Modifier.height(10.dp))

                    HoldToTalkButton(
                        isRecording = isRecording,
                        label = if (isRecording) strings.releaseToSend(recordingDurationSec) else strings.holdToTalk,
                        buttonColor = if (isRecording) AstraCrimson else AstraCyan,
                        iconTint = if (isRecording) Color.White else Color.Black,
                        onPressStart = onPttStart,
                        onPressStop = onPttStop
                    )
                }
                VoiceMode.WALKIE_TALKIE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isReceiverSpeaking) strings.peerTransmittingBusy else if (isRecording) strings.transmitting else strings.walkieTalkieHalfDuplex,
                            color = if (isReceiverSpeaking) AstraAmber else if (isRecording) AstraEmerald else AstraTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isReceiverSpeaking) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = AstraAmber, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    WaveformVisualizer(rms = liveRms, isRecording = isRecording || isReceiverSpeaking)
                    Spacer(modifier = Modifier.height(10.dp))

                    HoldToTalkButton(
                        isRecording = isRecording,
                        label = if (isRecording) "${strings.transmitting} (${recordingDurationSec}s)..." else strings.holdToTransmit,
                        buttonColor = if (isRecording) AstraEmerald else AstraSurface,
                        iconTint = if (isRecording) Color.Black else AstraEmerald,
                        enabled = !isReceiverSpeaking,
                        onPressStart = onPttStart,
                        onPressStop = onPttStop
                    )
                }
                VoiceMode.EMERGENCY -> {
                    Text(
                        text = strings.emergencyVoiceBroadcast,
                        color = AstraCrimson,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.emergencyPriorityDescription,
                        color = AstraTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSosClicked,
                            colors = ButtonDefaults.buttonColors(containerColor = AstraCrimson),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.broadcastAlertNow, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRecording) AstraCrimson else AstraSurface)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            onPttStart()
                                            tryAwaitRelease()
                                            onPttStop()
                                        }
                                    )
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = if (isRecording) Color.White else AstraCrimson, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRecording) "${strings.sending} (${recordingDurationSec}s)..." else strings.holdSosVoice,
                                    color = if (isRecording) Color.White else AstraCrimson,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                VoiceMode.CONTINUOUS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.handsFreeContinuousSpeech,
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (continuousState) {
                                ContinuousState.IDLE -> strings.idleStatus
                                ContinuousState.LISTENING -> strings.listeningStatus
                                ContinuousState.SPEECH_DETECTED -> strings.speechDetectedStatus
                                ContinuousState.TRANSCRIBING -> strings.transcribingStatus
                                ContinuousState.THINKING -> strings.thinkingStatus
                                ContinuousState.SPEAKING -> strings.speakingStatus
                                ContinuousState.CONNECTED -> strings.connectedStatus
                                ContinuousState.DISCONNECTED -> strings.disconnectedStatus
                            },
                            color = when (continuousState) {
                                ContinuousState.LISTENING -> AstraEmerald
                                ContinuousState.SPEECH_DETECTED, ContinuousState.TRANSCRIBING -> AstraCyan
                                ContinuousState.SPEAKING -> AstraAmber
                                else -> AstraTextSecondary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    WaveformVisualizer(rms = liveRms, isRecording = isContinuous)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (liveTranscript.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(AstraSurface)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${strings.transcriptLabel}: \"$liveTranscript\"",
                                color = AstraTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HoldToTalkButton(
    isRecording: Boolean,
    label: String,
    buttonColor: Color,
    iconTint: Color,
    enabled: Boolean = true,
    onPressStart: () -> Unit,
    onPressStop: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(if (isRecording) scale else 1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) buttonColor else AstraOutline)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            tryAwaitRelease()
                            onPressStop()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = iconTint,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun WaveformVisualizer(rms: Int, isRecording: Boolean) {
    val normalized = (rms / 300).coerceIn(1, 12)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(AstraSurface.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 24
        for (i in 0 until barCount) {
            val heightFactor = if (isRecording) {
                ((i * 7 + rms) % normalized + 2).coerceIn(2, 22)
            } else {
                3
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(heightFactor.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isRecording) AstraCyan else AstraOutline)
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    senderName: String = "",
    onPlayAudio: (() -> Unit)? = null
) {
    val strings = appStrings()
    val bubbleColor = if (isFromMe) AstraCyan.copy(alpha = 0.2f) else AstraSurfaceVariant
    val alignment = if (isFromMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isFromMe && senderName.isNotBlank()) {
            Text(
                text = senderName,
                color = AstraCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isFromMe) 16.dp else 2.dp,
                        bottomEnd = if (isFromMe) 2.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (message.priority == MessagePriority.EMERGENCY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = AstraCrimson, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.emergencyBroadcast, color = AstraCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Voice Playback Waveform Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    IconButton(
                        onClick = { onPlayAudio?.invoke() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isFromMe) AstraCyan else AstraEmerald)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = strings.replay,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bars = listOf(8, 14, 18, 12, 22, 16, 10, 20, 14, 8, 16, 12)
                            bars.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(h.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (isFromMe) AstraCyan else AstraEmerald)
                                )
                            }
                        }
                        Text(
                            text = strings.recordingAudio,
                            color = AstraTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = "\"${message.content}\"",
                    color = AstraTextPrimary,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = AstraTextSecondary,
                        fontSize = 11.sp
                    )
                    if (isFromMe) {
                        when (message.status) {
                            MessageStatus.QUEUED, MessageStatus.TRANSMITTING -> {
                                Text("...", color = AstraTextSecondary, fontSize = 11.sp)
                            }
                            MessageStatus.SENT, MessageStatus.RELAYED -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = strings.sent,
                                    tint = AstraTextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.DELIVERED, MessageStatus.READ -> {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = strings.received,
                                    tint = AstraCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Text("!", color = Color.Red, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

