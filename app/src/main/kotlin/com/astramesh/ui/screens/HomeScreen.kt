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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
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
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
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

enum class PttState {
    IDLE,
    RECORDING,
    TRANSMITTING,
    RELAYING,
    RECEIVING
}

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

    // Selected language for speech input / output
    var selectedLanguage by remember { mutableStateOf(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    // PTT Transceiver State
    var pttState by remember { mutableStateOf(PttState.IDLE) }
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
            .padding(14.dp)
    ) {
        // -------------------------------------------------------------
        // 1. TOP TACTICAL TELEMETRY HEADER & LANGUAGE SELECTOR
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusDotColor = when {
                        !meshStatus.isAdvertising && !meshStatus.isScanning -> AstraCrimson
                        nearbyPeers.isEmpty() -> AstraAmber
                        else -> AstraEmerald
                    }
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "iTANTRA TRANSCEIVER",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = AstraTextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                val networkStatusText = when {
                    !meshStatus.isAdvertising && !meshStatus.isScanning -> "OFFLINE • BLE MESH OFF"
                    nearbyPeers.isEmpty() -> "Node ${localNodeId.toHex().take(8)} • SCANNING (0 PEERS)"
                    else -> "Node ${localNodeId.toHex().take(8)} • ${nearbyPeers.size} PEERS ACTIVE"
                }
                val networkStatusColor = when {
                    !meshStatus.isAdvertising && !meshStatus.isScanning -> AstraCrimson
                    nearbyPeers.isEmpty() -> AstraAmber
                    else -> AstraEmerald
                }
                Text(
                    text = networkStatusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = networkStatusColor
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
        // 2. TACTICAL COMMUNICATION MODE SELECTOR (BROADCAST / DIRECT / EMERGENCY)
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

        Spacer(modifier = Modifier.height(8.dp))

        // -------------------------------------------------------------
        // 3. TARGET CHANNEL & PEER SELECTOR BAR
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
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
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
                            Icon(cardIcon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val title = when (communicationMode) {
                                CommunicationMode.BROADCAST -> "ALL REACHABLE MESH (FLOOD)"
                                CommunicationMode.DIRECT -> selectedDirectPeer?.displayName ?: nearbyPeers.firstOrNull()?.displayName ?: "No Peer Selected"
                                CommunicationMode.EMERGENCY -> "EMERGENCY DISTRESS BEACON"
                            }
                            val subtitle = when (communicationMode) {
                                CommunicationMode.BROADCAST -> "0 Hops Broadcast • Automatic Multi-Hop Relay"
                                CommunicationMode.DIRECT -> {
                                    if (activeRecipientId.isBroadcast) "Select a peer node below for P2P link" else "P2P Node ${activeRecipientId.toHex().take(8)} • Direct Route"
                                }
                                CommunicationMode.EMERGENCY -> "High-Priority Mesh Flood (TTL 15) • Overrides DND"
                            }
                            Text(
                                text = title,
                                color = AstraTextPrimary,
                                fontSize = 13.sp,
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
                                fontSize = 10.sp,
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
                            Text("Directory", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (communicationMode == CommunicationMode.EMERGENCY) {
                        OutlinedButton(
                            onClick = onNavigateToEmergency,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCrimson)
                        ) {
                            Text("SOS Panel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // If in DIRECT mode and nearby peers exist, show a quick peer selector strip
                if (communicationMode == CommunicationMode.DIRECT && nearbyPeers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Active Mesh Peers in Range:",
                        fontSize = 10.sp,
                        color = AstraTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(nearbyPeers) { peer ->
                            val isPeerSelected = selectedDirectPeer?.nodeId == peer.nodeId ||
                                    (selectedDirectPeer == null && nearbyPeers.firstOrNull()?.nodeId == peer.nodeId)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPeerSelected) AstraCyan.copy(alpha = 0.3f) else AstraSurfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isPeerSelected) AstraCyan else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedDirectPeer = peer }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (peer.rssi > -75) AstraEmerald else AstraAmber)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = peer.displayName ?: "Node-${peer.nodeId.toHex().take(6)}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isPeerSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPeerSelected) AstraCyan else AstraTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${peer.rssi}dBm",
                                        fontSize = 9.sp,
                                        color = AstraTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // 4. MAIN PUSH-TO-TALK TACTICAL TRANSCEIVER
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
                targetValue = if (pttState == PttState.RECORDING) 1.15f else 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (pttState == PttState.RECORDING) 500 else 1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Waveform & Realtime Audio VU-Meter during speech
                if (pttState == PttState.RECORDING) {
                    TacticalWaveform(rms = liveRms)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "TRANSMITTING: 00:%02d".format(recordingDurationSec),
                        color = if (communicationMode == CommunicationMode.EMERGENCY) AstraCrimson else AstraCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    if (liveTranscript.isNotBlank()) {
                        Text(
                            text = "\"$liveTranscript\"",
                            color = AstraTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (pttState == PttState.TRANSMITTING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = AstraCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ENCODING & DISPATCHING TO MESH...",
                            color = AstraCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Push To Talk Circular Touch Target
                val buttonBrush = when {
                    pttState == PttState.RECORDING && communicationMode == CommunicationMode.EMERGENCY -> Brush.radialGradient(listOf(AstraCrimson, AstraCrimson.copy(alpha = 0.6f)))
                    pttState == PttState.RECORDING -> Brush.radialGradient(listOf(AstraCyan, AstraEmerald))
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
                                    pttState = PttState.RECORDING
                                    recordingDurationSec = 0
                                    liveRms = 0
                                    liveTranscript = ""
                                    recordedAudioBuffer.reset()

                                    timerJob?.cancel()
                                    timerJob = scope.launch {
                                        while (isActive && pttState == PttState.RECORDING) {
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
                                    pttState = PttState.TRANSMITTING
                                    liveRms = 0
                                    val duration = recordingDurationSec
                                    timerJob?.cancel()
                                    timerJob = null

                                    scope.launch {
                                        val recognizedText = voiceEngineManager?.stopSttAndAwaitResult(timeoutMs = 1200L) ?: ""

                                        var rawTranscript = if (recognizedText.isNotBlank()) {
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

                                        if (rawTranscript.isNotBlank() && selectedLanguage == com.astramesh.core.Language.ENGLISH && rawTranscript.any { it in 'ऀ'..'ॿ' }) {
                                            val translatedEng = voiceEngineManager?.translateText(rawTranscript, com.astramesh.core.Language.HINDI, com.astramesh.core.Language.ENGLISH) ?: ""
                                            if (translatedEng.isNotBlank()) {
                                                rawTranscript = translatedEng
                                            }
                                        }
                                        val transcriptToSend = rawTranscript

                                        if (transcriptToSend.isNotBlank()) {
                                            AstraLog.d("HomeScreen", "PTT_SEND mode=${communicationMode.name} text='$transcriptToSend' lang=${selectedLanguage.name}")
                                            sendMessageUseCase.sendVoiceMessageByMode(
                                                mode = communicationMode,
                                                directRecipientId = if (communicationMode == CommunicationMode.DIRECT) activeRecipientId else null,
                                                text = transcriptToSend,
                                                language = selectedLanguage
                                            )
                                        }
                                        delay(300)
                                        pttState = PttState.IDLE
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
                            tint = if (pttState == PttState.RECORDING) Color.White else AstraTextPrimary,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val mainLabel = when {
                            pttState == PttState.RECORDING -> "RELEASE TO TRANSMIT"
                            communicationMode == CommunicationMode.BROADCAST -> "HOLD TO BROADCAST"
                            communicationMode == CommunicationMode.DIRECT -> "HOLD TO TALK DIRECT"
                            communicationMode == CommunicationMode.EMERGENCY -> "HOLD FOR SOS"
                            else -> "HOLD TO TALK"
                        }
                        Text(
                            text = mainLabel,
                            color = if (pttState == PttState.RECORDING) Color.White else AstraTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = selectedLanguage.nativeName,
                            color = if (pttState == PttState.RECORDING) Color.White.copy(alpha = 0.85f) else AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
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
        // 5. MESH & RELAY NETWORK METRICS BAR
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
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (meshStatus.isAdvertising) AstraEmerald else AstraCrimson)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (meshStatus.isAdvertising) "BLE MESH ON" else "MESH OFF",
                    fontSize = 10.sp,
                    color = if (meshStatus.isAdvertising) AstraEmerald else AstraCrimson,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // -------------------------------------------------------------
        // 6. SECTOR TRANSMISSIONS & RELAY FEED WITH INSTANT REPLAY
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp),
            colors = CardDefaults.cardColors(containerColor = AstraSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (communicationMode) {
                                CommunicationMode.BROADCAST -> "SECTOR BROADCAST LOG"
                                CommunicationMode.DIRECT -> "DIRECT P2P LOG"
                                CommunicationMode.EMERGENCY -> "EMERGENCY DISTRESS FEED"
                            },
                            color = when (communicationMode) {
                                CommunicationMode.EMERGENCY -> AstraCrimson
                                else -> AstraCyan
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "Tap ▶ to replay",
                        color = AstraTextSecondary,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transmissions logged on ${communicationMode.displayName} channel.",
                            color = AstraTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(messages.takeLast(6).reversed()) { msg ->
                            TacticalMessageLogItem(
                                message = msg,
                                localNodeId = localNodeId,
                                onReplay = {
                                    val cleanText = msg.content.replace(Regex("^\\[.*?\\]:?\\s*"), "").trim()
                                    voiceEngineManager?.speakText(
                                        text = cleanText,
                                        language = selectedLanguage,
                                        isEmergency = msg.priority == MessagePriority.EMERGENCY
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
fun TacticalMessageLogItem(
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
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(
                onClick = onReplay,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (message.priority == MessagePriority.EMERGENCY) AstraCrimson else AstraCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val directionTag = if (isFromMe) "TX" else "RX"
                    val tagColor = if (isFromMe) AstraCyan else AstraEmerald
                    Text(
                        text = "[$directionTag]",
                        color = tagColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = message.content,
                        color = AstraTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val hopText = if (message.hopCount == 0) "Direct Link" else "${message.hopCount} Hops Relayed"
                val subtitle = if (isFromMe) "Outgoing • $hopText" else "Node ${message.senderId.toHex().take(8)} • $hopText"
                Text(
                    text = subtitle,
                    color = AstraTextSecondary,
                    fontSize = 9.sp
                )
            }
        }

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

@Composable
fun TacticalWaveform(rms: Int) {
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
