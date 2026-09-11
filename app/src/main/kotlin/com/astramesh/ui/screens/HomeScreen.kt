package com.astramesh.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.core.ChatId
import com.astramesh.core.CommunicationMode
import com.astramesh.core.Language
import com.astramesh.core.NodeId
import com.astramesh.core.VoiceMode
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.Peer
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.usecase.SendMessageUseCase
import com.astramesh.services.VoiceEngineManager
import com.astramesh.ui.components.PulsingStatusDot
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    localNodeId: NodeId,
    peerRepository: PeerRepository,
    messageRepository: MessageRepository,
    chatRepository: ChatRepository,
    identityRepository: IdentityRepository,
    sendMessageUseCase: SendMessageUseCase,
    meshRepository: MeshRepository? = null,
    voiceEngineManager: VoiceEngineManager?,
    onNavigateToContacts: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onOpenConversation: (chatId: String, recipientId: Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val nearbyPeers by peerRepository.observeNearbyPeers().collectAsState(initial = emptyList())
    val meshStatus by (meshRepository?.meshStatus ?: kotlinx.coroutines.flow.MutableStateFlow(com.astramesh.domain.model.MeshStatus())).collectAsState()

    // Active Communication Mode: Default is BROADCAST
    var communicationMode by remember { mutableStateOf(CommunicationMode.BROADCAST) }
    var selectedDirectPeer by remember { mutableStateOf<Peer?>(null) }

    // Selected language for speech input
    var selectedLanguage by remember { mutableStateOf(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    // PTT Recording State
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var liveRms by remember { mutableStateOf(0) }
    var liveTranscript by remember { mutableStateOf("") }

    // Coroutine Job for timer
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val recordedAudioBuffer = remember { ByteArrayOutputStream() }

    // Active Target & Chat ID according to active mode
    val activeRecipientId: NodeId = when (communicationMode) {
        CommunicationMode.BROADCAST -> NodeId.BROADCAST
        CommunicationMode.DIRECT -> selectedDirectPeer?.nodeId ?: nearbyPeers.firstOrNull()?.nodeId ?: NodeId.BROADCAST
        CommunicationMode.EMERGENCY -> NodeId.BROADCAST
    }

    val activeChatId: ChatId = when (communicationMode) {
        CommunicationMode.BROADCAST -> ChatId("chat_broadcast")
        CommunicationMode.DIRECT -> {
            if (activeRecipientId.isBroadcast) ChatId("chat_broadcast") else ChatId("direct_${activeRecipientId.value}")
        }
        CommunicationMode.EMERGENCY -> ChatId("chat_emergency_broadcast")
    }

    val messages by messageRepository.observeMessages(activeChatId).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .padding(16.dp)
    ) {
        // -------------------------------------------------------------
        // TOP STATUS BAR & LANGUAGE SELECTOR
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AstraEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "iTantra Walkie-Talkie",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = AstraTextPrimary
                    )
                }
                Text(
                    text = "Node ${localNodeId.toHex().take(8)} • ${nearbyPeers.size} Peers in Mesh",
                    fontSize = 11.sp,
                    color = AstraEmerald
                )
            }

            // Language Selector Chip & Dropdown
            Box {
                AssistChip(
                    onClick = { languageDropdownExpanded = true },
                    label = { Text(selectedLanguage.nativeName, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = AstraSurfaceVariant,
                        labelColor = AstraCyan,
                        leadingIconContentColor = AstraCyan
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
                                AstraLog.d("HomeScreen", "Language changed to ${lang.englishName}")
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // COMMUNICATION MODE SELECTOR (BROADCAST / DIRECT / EMERGENCY)
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AstraSurfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CommunicationMode.entries.forEach { mode ->
                val isSelected = communicationMode == mode
                val selectedBg = when (mode) {
                    CommunicationMode.BROADCAST -> AstraCyan
                    CommunicationMode.DIRECT -> AstraCyan
                    CommunicationMode.EMERGENCY -> AstraCrimson
                }
                val selectedTextColor = when (mode) {
                    CommunicationMode.BROADCAST -> AstraBackground
                    CommunicationMode.DIRECT -> AstraBackground
                    CommunicationMode.EMERGENCY -> Color.White
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) selectedBg else Color.Transparent)
                        .clickable {
                            communicationMode = mode
                            if (mode == CommunicationMode.EMERGENCY) {
                                AstraLog.d("HomeScreen", "Mode switched to EMERGENCY")
                            } else {
                                AstraLog.d("HomeScreen", "Mode switched to ${mode.name}")
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (mode) {
                            CommunicationMode.BROADCAST -> Icons.Default.Podcasts
                            CommunicationMode.DIRECT -> Icons.Default.Person
                            CommunicationMode.EMERGENCY -> Icons.Default.Emergency
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected) selectedTextColor else AstraTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = mode.displayName.uppercase(Locale.getDefault()),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) selectedTextColor else AstraTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // ACTIVE MODE & TARGET CARD
        // -------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (communicationMode) {
                    CommunicationMode.EMERGENCY -> AstraCrimson.copy(alpha = 0.15f)
                    CommunicationMode.BROADCAST -> AstraSurface
                    CommunicationMode.DIRECT -> AstraSurface
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when (communicationMode) {
                                    CommunicationMode.EMERGENCY -> AstraCrimson.copy(alpha = 0.3f)
                                    CommunicationMode.BROADCAST -> AstraCyan.copy(alpha = 0.2f)
                                    CommunicationMode.DIRECT -> AstraCyan.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val cardIcon = when (communicationMode) {
                            CommunicationMode.EMERGENCY -> Icons.Default.Emergency
                            CommunicationMode.BROADCAST -> Icons.Default.Podcasts
                            CommunicationMode.DIRECT -> Icons.Default.Person
                        }
                        val tint = when (communicationMode) {
                            CommunicationMode.EMERGENCY -> AstraCrimson
                            CommunicationMode.BROADCAST -> AstraCyan
                            CommunicationMode.DIRECT -> AstraCyan
                        }
                        Icon(cardIcon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val title = when (communicationMode) {
                            CommunicationMode.BROADCAST -> "ALL REACHABLE MESH"
                            CommunicationMode.DIRECT -> selectedDirectPeer?.displayName ?: nearbyPeers.firstOrNull()?.displayName ?: "No Peer Selected"
                            CommunicationMode.EMERGENCY -> "EMERGENCY DISTRESS BEACON"
                        }
                        val subtitle = when (communicationMode) {
                            CommunicationMode.BROADCAST -> "Broadcasting to all nodes in range (0 Hops Flood)"
                            CommunicationMode.DIRECT -> {
                                if (activeRecipientId.isBroadcast) "Select a peer from Contacts for P2P" else "P2P Node ${activeRecipientId.toHex().take(8)}"
                            }
                            CommunicationMode.EMERGENCY -> "High-Priority Mesh Flood (TTL 15) • Overrides DND"
                        }
                        Text(
                            text = title,
                            color = AstraTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            color = when (communicationMode) {
                                CommunicationMode.EMERGENCY -> AstraCrimson
                                else -> AstraEmerald
                            },
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (communicationMode == CommunicationMode.DIRECT) {
                    OutlinedButton(
                        onClick = onNavigateToContacts,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCyan)
                    ) {
                        Text("Switch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (communicationMode == CommunicationMode.EMERGENCY) {
                    OutlinedButton(
                        onClick = onNavigateToEmergency,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCrimson)
                    ) {
                        Text("SOS Screen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -------------------------------------------------------------
        // MAIN PUSH-TO-TALK WALKIE-TALKIE BUTTON
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isRecording) 1.15f else 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (isRecording) 500 else 1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Waveform indicator during speech
                if (isRecording) {
                    HomeWaveform(rms = liveRms)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Transmitting: 00:%02d".format(recordingDurationSec),
                        color = if (communicationMode == CommunicationMode.EMERGENCY) AstraCrimson else AstraCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (liveTranscript.isNotBlank()) {
                        Text(
                            text = "\"$liveTranscript\"",
                            color = AstraTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Push To Talk Circular Touch Target
                val buttonBrush = when {
                    isRecording && communicationMode == CommunicationMode.EMERGENCY -> Brush.radialGradient(listOf(AstraCrimson, AstraCrimson.copy(alpha = 0.6f)))
                    isRecording -> Brush.radialGradient(listOf(AstraCyan, AstraEmerald))
                    communicationMode == CommunicationMode.EMERGENCY -> Brush.radialGradient(listOf(AstraCrimson, AstraSurfaceVariant))
                    communicationMode == CommunicationMode.DIRECT -> Brush.radialGradient(listOf(AstraCyan, AstraSurfaceVariant))
                    else -> Brush.radialGradient(listOf(AstraCyan, AstraSurfaceVariant))
                }

                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(buttonBrush)
                        .pointerInput(communicationMode, selectedDirectPeer, selectedLanguage) {
                            detectTapGestures(
                                onPress = {
                                    isRecording = true
                                    recordingDurationSec = 0
                                    liveRms = 0
                                    liveTranscript = ""
                                    recordedAudioBuffer.reset()

                                    timerJob?.cancel()
                                    timerJob = scope.launch {
                                        while (isActive && isRecording) {
                                            delay(1000)
                                            recordingDurationSec += 1
                                        }
                                    }

                                    AstraLog.d("HomeScreen", "PTT_START initiated mode=${communicationMode.name} lang=${selectedLanguage.name}")
                                    voiceEngineManager?.startStt(
                                        language = selectedLanguage,
                                        onRmsChanged = { rms -> liveRms = rms }
                                    ) { transcript ->
                                        if (transcript.isNotBlank()) {
                                            liveTranscript = transcript
                                        }
                                    }

                                    tryAwaitRelease()

                                    // RELEASE TO SEND
                                    isRecording = false
                                    liveRms = 0
                                    val duration = recordingDurationSec
                                    timerJob?.cancel()
                                    timerJob = null

                                    scope.launch {
                                        val recognizedText = voiceEngineManager?.stopSttAndAwaitResult(timeoutMs = 1200L) ?: ""

                                        val transcriptToSend = if (recognizedText.isNotBlank()) {
                                            recognizedText
                                        } else if (liveTranscript.isNotBlank()) {
                                            liveTranscript
                                        } else if (duration > 0) {
                                            if (communicationMode == CommunicationMode.EMERGENCY) {
                                                selectedLanguage.getDefaultEmergencyText()
                                            } else {
                                                selectedLanguage.getDefaultVoiceNoteText()
                                            }
                                        } else {
                                            ""
                                        }

                                        if (transcriptToSend.isNotBlank()) {
                                            AstraLog.d("HomeScreen", "PTT_SEND mode=${communicationMode.name} text='$transcriptToSend' lang=${selectedLanguage.name}")
                                            sendMessageUseCase.sendVoiceMessageByMode(
                                                mode = communicationMode,
                                                directRecipientId = if (communicationMode == CommunicationMode.DIRECT) activeRecipientId else null,
                                                text = transcriptToSend,
                                                language = selectedLanguage
                                            )
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val icon = when (communicationMode) {
                            CommunicationMode.EMERGENCY -> Icons.Default.Emergency
                            CommunicationMode.BROADCAST -> Icons.Default.Mic
                            CommunicationMode.DIRECT -> Icons.Default.Mic
                        }
                        Icon(
                            icon,
                            contentDescription = "PTT",
                            tint = if (isRecording) Color.White else AstraTextPrimary,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val mainLabel = when {
                            isRecording -> "RELEASE TO SEND"
                            communicationMode == CommunicationMode.BROADCAST -> "HOLD TO BROADCAST"
                            communicationMode == CommunicationMode.DIRECT -> "HOLD TO TALK DIRECT"
                            communicationMode == CommunicationMode.EMERGENCY -> "HOLD FOR SOS"
                            else -> "HOLD TO TALK"
                        }
                        Text(
                            text = mainLabel,
                            color = if (isRecording) Color.White else AstraTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = selectedLanguage.nativeName,
                            color = if (isRecording) Color.White.copy(alpha = 0.85f) else AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                val footerText = when (communicationMode) {
                    CommunicationMode.BROADCAST -> "Auto-relayed & translated on all reachable devices"
                    CommunicationMode.DIRECT -> "Direct end-to-end P2P mesh voice transmission"
                    CommunicationMode.EMERGENCY -> "High-priority emergency broadcast override"
                }
                Text(
                    text = footerText,
                    color = AstraTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // -------------------------------------------------------------
        // MESH & RELAY NETWORK METRICS BAR
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AstraSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TX: ${meshStatus.packetsSent}  |  RX: ${meshStatus.packetsReceived}  |  Relayed: ${meshStatus.packetsRelayed}",
                fontSize = 10.sp,
                color = AstraTextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (meshStatus.isAdvertising) AstraEmerald else AstraCrimson))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (meshStatus.isAdvertising) "BLE MESH ON" else "MESH OFF",
                    fontSize = 10.sp,
                    color = if (meshStatus.isAdvertising) AstraEmerald else AstraCrimson,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // RECENT TRANSMISSIONS FEED WITH INSTANT REPLAY
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            colors = CardDefaults.cardColors(containerColor = AstraSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (communicationMode) {
                            CommunicationMode.BROADCAST -> "Recent Broadcasts"
                            CommunicationMode.DIRECT -> "Direct Transmissions"
                            CommunicationMode.EMERGENCY -> "Emergency Distress Feed"
                        },
                        color = when (communicationMode) {
                            CommunicationMode.EMERGENCY -> AstraCrimson
                            else -> AstraCyan
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap ▶ to replay",
                        color = AstraTextSecondary,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transmissions yet in ${communicationMode.displayName} mode.",
                            color = AstraTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(messages.takeLast(4).reversed()) { msg ->
                            RecentMessageItem(
                                message = msg,
                                localNodeId = localNodeId,
                                onReplay = {
                                    val cleanText = msg.content.replace(Regex("^\\[.*?\\]:?\\s*"), "").trim()
                                    voiceEngineManager?.speakText(
                                        text = cleanText,
                                        language = selectedLanguage,
                                        isEmergency = msg.priority == com.astramesh.domain.model.MessagePriority.EMERGENCY
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

@Composable
fun RecentMessageItem(
    message: Message,
    localNodeId: NodeId,
    onReplay: () -> Unit
) {
    val isFromMe = message.senderId == localNodeId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFromMe) AstraCyan.copy(alpha = 0.1f) else AstraSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(
                onClick = onReplay,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (message.priority == com.astramesh.domain.model.MessagePriority.EMERGENCY) AstraCrimson else AstraCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = message.content,
                    color = AstraTextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isFromMe) "Outgoing • Reconstructed" else "From Node ${message.senderId.toHex().take(8)} • Translated",
                    color = AstraTextSecondary,
                    fontSize = 9.sp
                )
            }
        }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(
            text = timeFormat.format(Date(message.timestamp)),
            color = AstraTextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun HomeWaveform(rms: Int) {
    val normalized = (rms / 300).coerceIn(1, 14)
    Row(
        modifier = Modifier
            .width(180.dp)
            .height(24.dp)
            .background(AstraSurface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 18) {
            val height = ((i * 7 + rms) % normalized + 2).coerceIn(3, 20)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AstraCyan)
            )
        }
    }
}
