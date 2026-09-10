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
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
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
    var selectedPeer by remember { mutableStateOf<Peer?>(null) }

    // Selected language for speech input
    var selectedLanguage by remember { mutableStateOf(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    // PTT Recording State
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var liveRms by remember { mutableStateOf(0) }
    var liveTranscript by remember { mutableStateOf("") }
    var currentVoiceMode by remember { mutableStateOf(VoiceMode.PUSH_TO_TALK) }

    // Coroutine Job for timer
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val recordedAudioBuffer = remember { ByteArrayOutputStream() }

    // Active recipient
    val activeRecipientId = selectedPeer?.nodeId ?: nearbyPeers.firstOrNull()?.nodeId ?: NodeId.BROADCAST
    val activeRecipientName = selectedPeer?.displayName ?: nearbyPeers.firstOrNull()?.displayName ?: "All Nearby Mesh"

    // Default chat ID
    val activeChatId = if (activeRecipientId.isBroadcast) {
        com.astramesh.core.ChatId("chat_broadcast")
    } else {
        com.astramesh.core.ChatId("direct_${activeRecipientId.value}")
    }
    val messages by messageRepository.observeMessages(activeChatId).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstraBackground)
            .padding(16.dp)
    ) {
        // Top Bar / Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "iTantra Transceiver",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraTextPrimary
                )
                Text(
                    text = "ID: ${localNodeId.toHex().take(8)} • ${nearbyPeers.size} Peers Active",
                    fontSize = 12.sp,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Target Contact / Peer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AstraSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AstraCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = AstraCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = activeRecipientName,
                            color = AstraTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeRecipientId.isBroadcast) "Broadcast Mode • 0 hops" else "P2P Connected • Node ${activeRecipientId.toHex().take(8)}",
                            color = AstraEmerald,
                            fontSize = 11.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToContacts,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCyan)
                ) {
                    Text("Contacts", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Tactile Push-To-Talk Walkie-Talkie Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isRecording) 1.15f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
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
                        text = "Recording: 00:%02d".format(recordingDurationSec),
                        color = AstraCrimson,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (liveTranscript.isNotBlank()) {
                        Text(
                            text = "\"$liveTranscript\"",
                            color = AstraCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Push To Talk Circular Touch Target
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isRecording) {
                                    listOf(AstraCrimson, AstraCrimson.copy(alpha = 0.6f))
                                } else {
                                    listOf(AstraCyan, AstraSurfaceVariant)
                                }
                            )
                        )
                        .pointerInput(Unit) {
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

                                    AstraLog.d("HomeScreen", "PTT_START initiated lang=${selectedLanguage.name}")
                                    voiceEngineManager?.startRecording(scope) { chunk, rms ->
                                        if (liveRms == 0) liveRms = rms
                                        synchronized(recordedAudioBuffer) {
                                            recordedAudioBuffer.write(chunk)
                                        }
                                    }

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
                                        voiceEngineManager?.stopRecording()
                                        val recognizedText = voiceEngineManager?.stopSttAndAwaitResult(timeoutMs = 700L) ?: ""

                                        val audioBytes = synchronized(recordedAudioBuffer) {
                                            recordedAudioBuffer.toByteArray()
                                        }

                                        val transcriptToSend = if (recognizedText.isNotBlank()) {
                                            recognizedText
                                        } else if (liveTranscript.isNotBlank()) {
                                            liveTranscript
                                        } else if (duration > 0 || audioBytes.isNotEmpty()) {
                                            selectedLanguage.getDefaultVoiceNoteText()
                                        } else {
                                            ""
                                        }

                                        if (transcriptToSend.isNotBlank()) {
                                            AstraLog.d("HomeScreen", "PTT_SEND dispatching transcript='$transcriptToSend' lang=${selectedLanguage.name}")
                                            sendMessageUseCase.sendIthantraVoiceMessage(
                                                chatId = activeChatId,
                                                recipientId = activeRecipientId,
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
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "PTT",
                            tint = if (isRecording) Color.White else AstraTextPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRecording) "RELEASE TO SEND" else "HOLD TO TALK",
                            color = if (isRecording) Color.White else AstraTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = selectedLanguage.nativeName,
                            color = if (isRecording) Color.White.copy(alpha = 0.8f) else AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Reconstructed into speech at receiver",
                    color = AstraTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Recent Transmissions Feed with Instant Replay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
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
                        text = "Recent Transmissions",
                        color = AstraCyan,
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
                            text = "No transmissions yet. Hold button above to speak.",
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

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Emergency Bar
        Button(
            onClick = onNavigateToEmergency,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AstraCrimson),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("EMERGENCY SOS BROADCAST", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    maxLines = 1
                )
                Text(
                    text = if (isFromMe) "Sent • Reconstructed locally" else "Received • Speech decoded",
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
