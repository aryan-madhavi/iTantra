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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CallEnd
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.MutableStateFlow
import com.astramesh.core.ChatId
import com.astramesh.core.CommunicationMode
import com.astramesh.core.Language
import com.astramesh.core.NodeId
import com.astramesh.core.TranslationSettings
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.domain.model.Peer
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.usecase.EmergencyBroadcastUseCase
import com.astramesh.domain.usecase.SendMessageUseCase
import com.astramesh.services.VoiceEngineManager
import com.astramesh.ui.components.PulsingStatusDot
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

enum class DirectCommsMode {
    WALKIE_TALKIE,
    PHONE
}

@Composable
fun HomeScreen(
    localNodeId: NodeId,
    peerRepository: PeerRepository,
    messageRepository: MessageRepository,
    chatRepository: ChatRepository,
    identityRepository: IdentityRepository,
    sendMessageUseCase: SendMessageUseCase,
    emergencyBroadcastUseCase: EmergencyBroadcastUseCase,
    meshRepository: MeshRepository? = null,
    voiceEngineManager: VoiceEngineManager?,
    discoverPeersUseCase: com.astramesh.domain.usecase.DiscoverPeersUseCase = remember(peerRepository) { com.astramesh.domain.usecase.DiscoverPeersUseCase(peerRepository) },
    onNavigateToContacts: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onOpenConversation: (chatId: String, recipientId: Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val nearbyPeers by discoverPeersUseCase().collectAsState(initial = emptyList())
    val meshStatus by (meshRepository?.meshStatus ?: kotlinx.coroutines.flow.MutableStateFlow(com.astramesh.domain.model.MeshStatus())).collectAsState()
    val isTranslationEnabled by TranslationSettings.isTranslationEnabled.collectAsState()

    // Active Communication Mode: Default is BROADCAST
    var communicationMode by remember { mutableStateOf(CommunicationMode.BROADCAST) }
    var selectedDirectPeer by remember { mutableStateOf<Peer?>(null) }
    var directCommsMode by remember { mutableStateOf(DirectCommsMode.WALKIE_TALKIE) }
    var isDirectPhoneCallActive by remember { mutableStateOf(false) }

    // Selected language for speech input / output (observed from unified meshRepository source of truth)
    val selectedLanguage by (meshRepository?.preferredLanguage ?: remember { MutableStateFlow(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }).collectAsState(initial = voiceEngineManager?.preferredLanguage ?: Language.HINDI)
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

    LaunchedEffect(activeChatId, isDirectPhoneCallActive) {
        var lastMsgTimestamp = System.currentTimeMillis()
        if (isDirectPhoneCallActive) {
            messageRepository.observeMessages(activeChatId).collect { msgList ->
                if (msgList.isEmpty()) return@collect
                val lastMsg = msgList.last()
                if (lastMsg.senderId != localNodeId && lastMsg.timestamp > lastMsgTimestamp) {
                    lastMsgTimestamp = lastMsg.timestamp
                    if (lastMsg.content.contains("[CALL_ENDED]") || lastMsg.content.contains("[CALL_DECLINED]")) {
                        isDirectPhoneCallActive = false
                        voiceEngineManager?.stopStt()
                        com.astramesh.domain.model.CallSessionManager.endCall()
                    } else if (lastMsg.content.contains("[Voice Note") || !lastMsg.content.startsWith("[")) {
                        val cleanText = lastMsg.content.replace(Regex("^\\[.*?\\]:?\\s*"), "").trim()
                        voiceEngineManager?.speakText(cleanText, selectedLanguage)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .statusBarsPadding()
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
                        text = "TRANSCEIVER",
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
                                    languageDropdownExpanded = false
                                    meshRepository?.setPreferredLanguage(lang)
                                    AstraLog.d("HomeScreen", "Language changed to ${lang.englishName}")
                                }
                            )
                        }
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
                                CommunicationMode.EMERGENCY -> "Hold for 2s to broadcast SOS and record a 10s voice brief"
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

                // If in DIRECT mode, show the Walkie-Talkie vs Phone Mode toggle
                if (communicationMode == CommunicationMode.DIRECT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AstraSurfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (directCommsMode == DirectCommsMode.WALKIE_TALKIE) AstraCyan.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (directCommsMode == DirectCommsMode.WALKIE_TALKIE) AstraCyan else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable {
                                    if (isDirectPhoneCallActive) {
                                        isDirectPhoneCallActive = false
                                        voiceEngineManager?.stopStt()
                                        com.astramesh.domain.model.CallSessionManager.endCall()
                                    }
                                    directCommsMode = DirectCommsMode.WALKIE_TALKIE
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (directCommsMode == DirectCommsMode.WALKIE_TALKIE) AstraCyan else AstraTextSecondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Walkie-Talkie (PTT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (directCommsMode == DirectCommsMode.WALKIE_TALKIE) AstraCyan else AstraTextSecondary)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (directCommsMode == DirectCommsMode.PHONE) AstraEmerald.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (directCommsMode == DirectCommsMode.PHONE) AstraEmerald else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { directCommsMode = DirectCommsMode.PHONE }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (directCommsMode == DirectCommsMode.PHONE) AstraEmerald else AstraTextSecondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isDirectPhoneCallActive) "Phone (Active)" else "Phone (Continuous)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (directCommsMode == DirectCommsMode.PHONE) AstraEmerald else AstraTextSecondary)
                            }
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
                                        color = if (isPeerSelected) AstraCyan else AstraTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

            if (communicationMode == CommunicationMode.EMERGENCY) {
                EmergencySosActionArea(
                    emergencyBroadcastUseCase = emergencyBroadcastUseCase,
                    voiceEngineManager = voiceEngineManager,
                    selectedLanguage = selectedLanguage,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
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
                            color = AstraCyan,
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

                    if (communicationMode == CommunicationMode.DIRECT && directCommsMode == DirectCommsMode.PHONE) {
                        val phoneBrush = if (isDirectPhoneCallActive) {
                            Brush.radialGradient(listOf(AstraCrimson, AstraSurfaceVariant))
                        } else {
                            Brush.radialGradient(listOf(AstraEmerald, AstraSurfaceVariant))
                        }

                        Box(
                            modifier = Modifier
                                .size(175.dp)
                                .scale(if (isDirectPhoneCallActive) pulseScale else 1.0f)
                                .clip(CircleShape)
                                .background(phoneBrush)
                                .clickable {
                                    if (!isDirectPhoneCallActive) {
                                        isDirectPhoneCallActive = true
                                        liveTranscript = ""
                                        com.astramesh.domain.model.CallSessionManager.acceptCall(activeRecipientId)

                                        scope.launch {
                                            sendMessageUseCase.sendVoiceMessageByMode(
                                                mode = CommunicationMode.DIRECT,
                                                directRecipientId = activeRecipientId,
                                                text = "[CALL_INVITE]: Phone call initiated",
                                                language = selectedLanguage
                                            )
                                        }

                                        AstraLog.d("HomeScreen", "[UI_EVENT] MIC_PRESS mode=PHONE_CALL")
                                        voiceEngineManager?.startStt(
                                            language = selectedLanguage,
                                            onRmsChanged = { rms -> liveRms = rms },
                                            onPartialResult = { partial ->
                                                if (partial.isNotBlank()) liveTranscript = partial
                                            },
                                            onFinalResult = { finalSentence ->
                                                if (finalSentence.isNotBlank() && isDirectPhoneCallActive) {
                                                    liveTranscript = finalSentence
                                                    scope.launch {
                                                        AstraLog.d("HomeScreen", "[UI_EVENT] MSG_SEND mode=PHONE_CALL type=VOICE")
                                                        sendMessageUseCase.sendVoiceMessageByMode(
                                                            mode = CommunicationMode.DIRECT,
                                                            directRecipientId = activeRecipientId,
                                                            text = finalSentence,
                                                            language = selectedLanguage
                                                        )
                                                        delay(1200)
                                                        if (isDirectPhoneCallActive) {
                                                            liveTranscript = ""
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        isDirectPhoneCallActive = false
                                        liveRms = 0
                                        liveTranscript = ""
                                        com.astramesh.domain.model.CallSessionManager.endCall()
                                        scope.launch {
                                            sendMessageUseCase.sendVoiceMessageByMode(
                                                mode = CommunicationMode.DIRECT,
                                                directRecipientId = activeRecipientId,
                                                text = "[CALL_ENDED]: Phone call ended",
                                                language = selectedLanguage
                                            )
                                        }
                                        voiceEngineManager?.stopStt()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (isDirectPhoneCallActive) Icons.Default.CallEnd else Icons.Default.Phone,
                                    contentDescription = "Phone Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isDirectPhoneCallActive) "TAP TO HANG UP" else "START PHONE CALL",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = if (isDirectPhoneCallActive) "CALL ACTIVE (LIVE)" else selectedLanguage.nativeName,
                                    color = if (isDirectPhoneCallActive) Color.White.copy(alpha = 0.85f) else AstraEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Push To Talk Circular Touch Target
                        val buttonBrush = when {
                            pttState == PttState.RECORDING -> Brush.radialGradient(listOf(AstraCyan, AstraEmerald))
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

                                            AstraLog.d("HomeScreen", "[UI_EVENT] MIC_PRESS mode=${communicationMode.name} lang=${selectedLanguage.name}")
                                            voiceEngineManager?.startStt(
                                                language = selectedLanguage,
                                                onRmsChanged = { rms -> liveRms = rms }
                                            ) { transcript ->
                                                if (transcript.isNotBlank()) {
                                                    liveTranscript = transcript
                                                }
                                            }

                                            tryAwaitRelease()

                                            AstraLog.d("HomeScreen", "[UI_EVENT] MIC_RELEASE mode=${communicationMode.name}")
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
                                                    selectedLanguage.getDefaultVoiceNoteText()
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
                                                    AstraLog.d("HomeScreen", "[UI_EVENT] MSG_SEND mode=${communicationMode.name} type=VOICE text='$transcriptToSend' lang=${selectedLanguage.name}")
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
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "PTT",
                                    tint = if (pttState == PttState.RECORDING) Color.White else AstraTextPrimary,
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val mainLabel = when {
                                    pttState == PttState.RECORDING -> "RELEASE TO TRANSMIT"
                                    communicationMode == CommunicationMode.BROADCAST -> "HOLD TO BROADCAST"
                                    communicationMode == CommunicationMode.DIRECT -> "HOLD TO TALK DIRECT"
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
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val footerText = when (communicationMode) {
                        CommunicationMode.BROADCAST -> "Auto-relayed & translated on all reachable devices"
                        CommunicationMode.DIRECT -> "Direct end-to-end P2P mesh voice transmission"
                        else -> ""
                    }
                    Text(
                        text = footerText,
                        color = AstraTextSecondary,
                        fontSize = 11.sp
                    )
                }
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
                                localLanguage = selectedLanguage,
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
