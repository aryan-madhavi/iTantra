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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.astramesh.ui.i18n.AppLanguageState
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
import android.util.Log
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
    onOpenConversation: (chatId: String, recipientId: Long) -> Unit,
    onApplicationLanguageSelected: (Language) -> Unit
) {
    val scope = rememberCoroutineScope()
    val strings = appStrings()
    val selectedUiLanguage = Language.fromCode(strings.languageCode)
    val nearbyPeers by peerRepository.observeNearbyPeers().collectAsState(initial = emptyList())
    var selectedPeer by remember { mutableStateOf<Peer?>(null) }
    var uiLanguageMenuExpanded by remember { mutableStateOf(false) }

    // Selected language for speech input — independent of Application UI language
    var selectedSpeechLanguage by remember { mutableStateOf(voiceEngineManager?.preferredLanguage ?: Language.HINDI) }

    // PTT Recording State
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var liveRms by remember { mutableStateOf(0) }
    var liveTranscript by remember { mutableStateOf("") }
    var currentVoiceMode by remember { mutableStateOf(VoiceMode.PUSH_TO_TALK) }

    // Coroutine Job for timer
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val recordedAudioBuffer = remember { ByteArrayOutputStream() }

    // Active recipient: default to true Mesh Broadcast (no 1:1 lock)
    val activeRecipientId = selectedPeer?.nodeId ?: NodeId.BROADCAST
    val activeRecipientName = selectedPeer?.displayName ?: strings.availableNodes
    val selectedUiLanguageLabel = selectedUiLanguage.nativeName

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
                    text = strings.appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AstraTextPrimary
                )
                Text(
                    text = "ID: ${localNodeId.toHex().take(8)} • ${nearbyPeers.size} ${strings.peersActive}",
                    fontSize = 12.sp,
                    color = AstraEmerald
                )
            }

            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                AssistChip(
                    onClick = { uiLanguageMenuExpanded = true },
                    label = { Text(selectedUiLanguageLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = AstraSurfaceVariant,
                        labelColor = AstraCyan,
                        leadingIconContentColor = AstraCyan
                    ),
                    modifier = Modifier.testTag("home_language_selector")
                )

                if (uiLanguageMenuExpanded) {
                    AlertDialog(
                        onDismissRequest = { uiLanguageMenuExpanded = false },
                        title = {
                            Text(strings.appInterfaceLanguage)
                        },
                        text = {
                            Column {
                                AppLanguageState.supportedLanguages.forEach { lang ->
                                    TextButton(
                                        onClick = {
                                            Log.d(
                                                "APP_LANGUAGE_DEBUG",
                                                "HOME CLICK language=${lang.code}, native=${lang.nativeName}"
                                            )
                                            onApplicationLanguageSelected(lang)
                                            uiLanguageMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${lang.nativeName} (${lang.englishName})",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                            text = if (activeRecipientId.isBroadcast) strings.meshBroadcast else "${strings.directPeer} ${activeRecipientId.toHex().take(8)}",
                            color = if (activeRecipientId.isBroadcast) AstraCyan else AstraEmerald,
                            fontSize = 11.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedPeer != null) {
                        TextButton(
                            onClick = { selectedPeer = null },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.allMesh, fontSize = 11.sp, color = AstraCyan)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToContacts,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AstraCyan)
                    ) {
                        Text(strings.contacts, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                if (isRecording) {
                    HomeWaveform(rms = liveRms)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${strings.recordingAudio}: 00:%02d".format(recordingDurationSec),
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

                                    AstraLog.d("HomeScreen", "PTT_START initiated lang=${selectedSpeechLanguage.name}")
                                    voiceEngineManager?.startStt(
                                        language = selectedSpeechLanguage,
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
                                            selectedSpeechLanguage.getDefaultVoiceNoteText()
                                        } else {
                                            ""
                                        }

                                        if (transcriptToSend.isNotBlank()) {
                                            AstraLog.d("HomeScreen", "PTT_SEND dispatching transcript='$transcriptToSend' lang=${selectedSpeechLanguage.name}")
                                            sendMessageUseCase.sendIthantraVoiceMessage(
                                                chatId = activeChatId,
                                                recipientId = activeRecipientId,
                                                text = transcriptToSend,
                                                language = selectedSpeechLanguage
                                            )
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = strings.pushToTalk,
                            tint = if (isRecording) Color.White else AstraTextPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRecording) strings.transmitting else strings.holdToBroadcast,
                            color = if (isRecording) Color.White else AstraTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = selectedUiLanguage.nativeName,
                            color = if (isRecording) Color.White.copy(alpha = 0.8f) else AstraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                        text = strings.receiverOutputLanguage,
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
                        text = strings.transcriptsTitle,
                        color = AstraCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.replay,
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
                            text = strings.noTranscripts,
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
                                        language = selectedSpeechLanguage,
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
            Text(strings.emergencyBroadcast, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun RecentMessageItem(
    message: Message,
    localNodeId: NodeId,
    onReplay: () -> Unit
) {
    val strings = appStrings()
    val isFromMe = message.senderId == localNodeId
    val voiceNotePrefix = "[Voice Note]: "
    val displayContent = if (
        message.contentType == com.astramesh.domain.model.MessageContentType.AUDIO_NOTE &&
        message.content.startsWith(voiceNotePrefix)
    ) {
        val transcript = message.content.removePrefix(voiceNotePrefix)
        "${strings.voiceNoteLabel}: $transcript"
    } else {
        message.content
    }
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
                    contentDescription = strings.replay,
                    tint = if (message.priority == com.astramesh.domain.model.MessagePriority.EMERGENCY) AstraCrimson else AstraCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = displayContent,
                    color = AstraTextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Text(
                    text = if (isFromMe) "${strings.sent} • ${strings.savedToSystem}" else "${strings.received} • ${strings.voiceNoteLabel}",
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


