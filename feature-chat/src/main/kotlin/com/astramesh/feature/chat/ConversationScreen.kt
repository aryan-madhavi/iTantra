package com.astramesh.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.core.Language
import com.astramesh.core.NodeId
import com.astramesh.core.TranslationSettings
import com.astramesh.core.VoiceMode
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.ui.components.TacticalMessageLogItem
import com.astramesh.ui.components.TacticalTranslationToggle
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
    val speechLanguage by viewModel.speechLanguage.collectAsState()
    val isTranslationEnabled by TranslationSettings.isTranslationEnabled.collectAsState()

    var showSosConfirmDialog by remember { mutableStateOf(false) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var showTextInputDrawer by remember { mutableStateOf(false) }

    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AstraCrimson) },
            title = { Text("Broadcast Emergency SOS?", color = AstraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This transmits a maximum-priority emergency beacon to this node and all reachable mesh relays.",
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
                    Text("TRANSMIT SOS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirmDialog = false }) {
                    Text("Cancel", color = AstraTextSecondary)
                }
            },
            containerColor = AstraSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    val customPeerName by viewModel.recipientDisplayName.collectAsState()
    val displayName = when {
        viewModel.recipientId.isBroadcast -> "ALL MESH (BROADCAST)"
        customPeerName != null -> customPeerName!!
        else -> "Node-${viewModel.recipientId.toHex().take(8)}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                color = if (viewModel.recipientId.isBroadcast) AstraAmber else AstraTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (viewModel.recipientId.isBroadcast) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AstraAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("BROADCAST", color = AstraAmber, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Text(
                            text = "Direct Neural Transceiver (${speechLanguage.englishName})",
                            color = AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AstraTextPrimary)
                    }
                },
                actions = {
                    TacticalTranslationToggle(
                        isEnabled = isTranslationEnabled,
                        onToggle = { TranslationSettings.toggleTranslation() }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(onClick = { languageDropdownExpanded = true }) {
                            Icon(Icons.Default.Language, contentDescription = "Speech Language", tint = AstraCyan)
                        }
                        DropdownMenu(
                            expanded = languageDropdownExpanded,
                            onDismissRequest = { languageDropdownExpanded = false },
                            modifier = Modifier.background(AstraSurface)
                        ) {
                            Language.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${lang.englishName} (${lang.nativeName})",
                                            color = if (lang == speechLanguage) AstraCyan else AstraTextPrimary,
                                            fontWeight = if (lang == speechLanguage) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSpeechLanguage(lang)
                                        languageDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AstraSurface)
            )
        },
        containerColor = AstraBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // -------------------------------------------------------------
            // DIRECT VOICE MODE SEGMENTED TOGGLE
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Switch for Walkie/Phone
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPhone = isContinuousModeActive || currentVoiceMode == VoiceMode.CONTINUOUS
                    Text("PTT", color = if (!isPhone) AstraCyan else AstraTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isPhone,
                        onCheckedChange = { checked ->
                            viewModel.setVoiceMode(if (checked) VoiceMode.CONTINUOUS else VoiceMode.PUSH_TO_TALK)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AstraEmerald,
                            checkedTrackColor = AstraEmerald.copy(alpha = 0.3f),
                            uncheckedThumbColor = AstraCyan,
                            uncheckedTrackColor = AstraCyan.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Phone", color = if (isPhone) AstraEmerald else AstraTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // SOS Button
                    val isSos = currentVoiceMode == VoiceMode.EMERGENCY
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSos) AstraCrimson.copy(alpha = 0.25f) else AstraSurfaceVariant.copy(alpha = 0.6f))
                            .clickable { viewModel.setVoiceMode(VoiceMode.EMERGENCY) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (isSos) AstraCrimson else AstraTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SOS", color = if (isSos) AstraCrimson else AstraTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Text Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showTextInputDrawer) AstraSurface else AstraSurfaceVariant.copy(alpha = 0.6f))
                            .clickable { showTextInputDrawer = !showTextInputDrawer }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(13.dp), tint = AstraTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showTextInputDrawer) "Hide" else "Text", color = AstraTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Optional Quick Text Drawer
            AnimatedVisibility(visible = showTextInputDrawer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = viewModel::onInputTextChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Quick tactical text...", color = AstraTextSecondary, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AstraTextPrimary,
                            unfocusedTextColor = AstraTextPrimary,
                            focusedBorderColor = AstraCyan,
                            unfocusedBorderColor = AstraOutline
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendMessage()
                            showTextInputDrawer = false
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AstraCyan)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // -------------------------------------------------------------
            // DIRECT TRANSMISSIONS LOG
            // -------------------------------------------------------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = AstraSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIRECT VOICE LOG",
                            color = AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${messages.size} Transmissions",
                            color = AstraTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isContinuousModeActive) "Continuous mode active — listening for speech..." else "Hold PTT below to initiate direct transmission.",
                                color = AstraTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            reverseLayout = true
                        ) {
                            items(messages.reversed(), key = { it.id.value }) { message ->
                                TacticalMessageLogItem(
                                    message = message,
                                    localNodeId = localNodeId,
                                    localLanguage = speechLanguage,
                                    onReplay = { viewModel.playVoiceMessage(message.content) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // -------------------------------------------------------------
            // DIRECT TRANSCEIVER CONTROLLER (PTT & CONTINUOUS)
            // -------------------------------------------------------------
            DirectPttTransceiverControl(
                voiceMode = currentVoiceMode,
                isRecording = isRecordingPtt,
                isContinuousMode = isContinuousModeActive,
                continuousState = continuousState,
                liveRms = liveRms,
                recordingDurationSec = recordingDurationSec,
                liveTranscript = liveTranscript,
                isReceiverSpeaking = isReceiverSpeaking,
                onPttStart = viewModel::startPtt,
                onPttStop = viewModel::stopPtt,
                onToggleContinuous = viewModel::toggleContinuousMode,
                onSosClicked = { showSosConfirmDialog = true }
            )
        }
    }
}

@Composable
fun DirectPttTransceiverControl(
    voiceMode: VoiceMode,
    isRecording: Boolean,
    isContinuousMode: Boolean,
    continuousState: ContinuousState,
    liveRms: Int,
    recordingDurationSec: Int,
    liveTranscript: String,
    isReceiverSpeaking: Boolean,
    onPttStart: () -> Unit,
    onPttStop: () -> Unit,
    onToggleContinuous: () -> Unit,
    onSosClicked: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "direct_pulse")
    val isActivelyTranscribing = isRecording || (isContinuousMode && liveTranscript.isNotBlank())
    val scale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActivelyTranscribing) 500 else 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btnScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                voiceMode == VoiceMode.EMERGENCY -> AstraCrimson.copy(alpha = 0.15f)
                isContinuousMode -> AstraEmerald.copy(alpha = 0.12f)
                else -> AstraSurfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Waveform & Live Transcript preview
            if (isRecording || isContinuousMode) {
                DirectWaveform(rms = liveRms)
                Spacer(modifier = Modifier.height(4.dp))
                val statusTitle = when {
                    isRecording -> "TRANSMITTING DIRECT: 00:%02d".format(recordingDurationSec)
                    isContinuousMode -> "CONTINUOUS PHONE MODE (ALWAYS LISTENING)"
                    else -> ""
                }
                Text(
                    text = statusTitle,
                    color = if (voiceMode == VoiceMode.EMERGENCY) AstraCrimson else if (isContinuousMode) AstraEmerald else AstraCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                if (liveTranscript.isNotBlank()) {
                    Text(
                        text = "\"$liveTranscript\"",
                        color = AstraTextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else if (isReceiverSpeaking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = AstraAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PEER IS TRANSMITTING (BUSY)...", color = AstraAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Transceiver Action Button
            val buttonColor = when {
                isRecording && voiceMode == VoiceMode.EMERGENCY -> AstraCrimson
                isRecording -> AstraEmerald
                voiceMode == VoiceMode.EMERGENCY -> AstraCrimson
                isContinuousMode -> AstraEmerald
                else -> AstraCyan
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .scale(if (isActivelyTranscribing) scale else 1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(buttonColor)
                    .pointerInput(voiceMode, isContinuousMode) {
                        if (isContinuousMode) {
                            detectTapGestures(
                                onTap = { onToggleContinuous() }
                            )
                        } else {
                            detectTapGestures(
                                onPress = {
                                    onPttStart()
                                    tryAwaitRelease()
                                    onPttStop()
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
                        when {
                            voiceMode == VoiceMode.EMERGENCY -> Icons.Default.Emergency
                            isContinuousMode -> Icons.Default.Phone
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = if (isRecording || isContinuousMode || voiceMode == VoiceMode.EMERGENCY) Color.White else Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val label = when {
                        isContinuousMode -> "PHONE MODE ACTIVE (TAP TO HANG UP)"
                        isRecording -> "RELEASE TO TRANSMIT DIRECT (${recordingDurationSec}s)"
                        voiceMode == VoiceMode.EMERGENCY -> "HOLD FOR DIRECT SOS VOICE"
                        else -> "HOLD TO TALK (VOICE PTT)"
                    }
                    Text(
                        text = label,
                        color = if (isRecording || isContinuousMode || voiceMode == VoiceMode.EMERGENCY) Color.White else Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DirectVoiceLogItem(
    message: Message,
    isFromMe: Boolean,
    recipientName: String,
    onPlayAudio: () -> Unit
) {
    val bubbleColor = if (isFromMe) AstraCyan.copy(alpha = 0.15f) else AstraSurfaceVariant
    val alignment = if (isFromMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPlayAudio,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (message.priority == MessagePriority.EMERGENCY) AstraCrimson else AstraCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isFromMe) "[TX OUT]" else "[RX IN]",
                            color = if (isFromMe) AstraCyan else AstraEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.content,
                            color = AstraTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = AstraTextSecondary,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (isFromMe) {
                        when (message.status) {
                            MessageStatus.QUEUED, MessageStatus.TRANSMITTING -> {
                                Text("...", color = AstraAmber, fontSize = 9.sp)
                            }
                            MessageStatus.SENT -> {
                                Icon(Icons.Default.Check, contentDescription = "Sent", tint = AstraTextSecondary, modifier = Modifier.size(11.dp))
                            }
                            MessageStatus.RELAYED -> {
                                Icon(Icons.Default.Sync, contentDescription = "Relayed", tint = AstraCyan, modifier = Modifier.size(11.dp))
                            }
                            MessageStatus.DELIVERED, MessageStatus.READ -> {
                                Icon(Icons.Default.DoneAll, contentDescription = "Delivered", tint = AstraEmerald, modifier = Modifier.size(12.dp))
                            }
                            MessageStatus.FAILED -> {
                                Text("!", color = AstraCrimson, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DirectWaveform(rms: Int) {
    val normalized = (rms / 300).coerceIn(1, 10)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(AstraSurface.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 20) {
            val heightFactor = ((i * 7 + rms) % normalized + 2).coerceIn(2, 18)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(heightFactor.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AstraCyan)
            )
        }
    }
}
