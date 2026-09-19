package com.astramesh.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.core.NodeId
import com.astramesh.core.OfflineLanguageDetector
import com.astramesh.core.TranslationSettings
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
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

@Composable
fun PulsingStatusDot(
    isActive: Boolean,
    activeColor: Color = AstraEmerald,
    inactiveColor: Color = AstraOutline,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .scale(if (isActive) scale else 1f)
            .clip(CircleShape)
            .background(if (isActive) activeColor else inactiveColor)
    )
}

@Composable
fun SignalStrengthIndicator(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val bars = when {
        rssi >= -65 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        else -> 1
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val barColor = if (i <= bars) AstraCyan else AstraOutline
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraTopBar(
    title: String,
    connectedPeersCount: Int = 0,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = AstraTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                PulsingStatusDot(isActive = connectedPeersCount > 0)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$connectedPeersCount peers",
                    color = AstraCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AstraSurface
        )
    )
}

/**
 * Compact tactical icon toggle for enabling/disabling the NLLB neural translation pipeline.
 * Features a cyberpunk/tactical aesthetic with cyan glow/border when active and muted outline when bypassed.
 */
@Composable
fun TacticalTranslationToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isEnabled) AstraCyan.copy(alpha = 0.15f) else AstraSurfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (isEnabled) AstraCyan.copy(alpha = 0.7f) else AstraOutline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = if (isEnabled) "Translation ON" else "Translation OFF",
                tint = if (isEnabled) AstraCyan else AstraTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isEnabled) "TR:ON" else "TR:OFF",
                color = if (isEnabled) AstraCyan else AstraTextSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

data class ParsedMessageContent(
    val cleanText: String,
    val translationBadge: String? = null,
    val isUntranslated: Boolean = false,
    val isEmergencyAlert: Boolean = false
)

fun parseMessageContent(rawContent: String, localLanguage: Language? = null): ParsedMessageContent {
    val emergency = rawContent.startsWith("[EMERGENCY") || rawContent.startsWith("[SOS")

    // Check for translation pair: e.g. [Voice Note English -> Hindi] or [EMERGENCY ALERT English -> Hindi]
    val translationRegex = Regex("""\[(?:Voice Note|EMERGENCY ALERT)\s+([A-Za-z]+)\s*(?:->|→)\s*([A-Za-z]+)\]:?\s*""", RegexOption.IGNORE_CASE)
    val translationMatch = translationRegex.find(rawContent)
    if (translationMatch != null) {
        val src = translationMatch.groupValues[1].take(2).uppercase(Locale.ROOT)
        val dst = translationMatch.groupValues[2].take(2).uppercase(Locale.ROOT)
        val clean = rawContent.substring(translationMatch.range.last + 1).trim()
        return ParsedMessageContent(
            cleanText = if (clean.isNotBlank()) clean else rawContent,
            translationBadge = "$src → $dst",
            isEmergencyAlert = emergency
        )
    }

    // Check for explicit untranslated tag: e.g. [Untranslated HI] or [Voice Note HI]
    val untranslatedRegex = Regex("""\[(?:Untranslated|Original|Voice Note)\s+([A-Za-z]{2,})\s*(?:\|\s*Untranslated)?\]:?\s*""", RegexOption.IGNORE_CASE)
    val untranslatedMatch = untranslatedRegex.find(rawContent)
    if (untranslatedMatch != null) {
        val lang = untranslatedMatch.groupValues[1].take(2).uppercase(Locale.ROOT)
        val clean = rawContent.substring(untranslatedMatch.range.last + 1).trim()
        return ParsedMessageContent(
            cleanText = if (clean.isNotBlank()) clean else rawContent,
            translationBadge = lang,
            isUntranslated = true,
            isEmergencyAlert = emergency
        )
    }

    // Fallback: strip general bracket header [ ... ]:
    val clean = rawContent.replace(Regex("""^\[.*?\]:?\s*"""), "").trim()
    val textToInspect = if (clean.isNotBlank()) clean else rawContent

    // Detect language if translation was bypassed or if language differs from user's preferred language
    val detection = OfflineLanguageDetector.detect(textToInspect)
    val isDifferentFromLocal = localLanguage != null && detection.language != localLanguage
    val isUntranslated = !TranslationSettings.isTranslationEnabled.value || isDifferentFromLocal

    val untranslatedBadge = if (isUntranslated && detection.confidence >= 0.3f && textToInspect.length >= 3) {
        detection.language.code.uppercase(Locale.ROOT)
    } else null

    return ParsedMessageContent(
        cleanText = textToInspect,
        translationBadge = untranslatedBadge,
        isUntranslated = untranslatedBadge != null,
        isEmergencyAlert = emergency
    )
}

/**
 * Shared tactical message log entry component for Broadcast, Direct, and Emergency views.
 * Features a compact translation chip (e.g. EN -> HI) that does not wrap or push text,
 * tap-to-expand for long messages (2 lines collapsed preview -> full expanded), and replay.
 */
@Composable
fun TacticalMessageLogItem(
    message: Message,
    localNodeId: NodeId,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
    localLanguage: Language? = null,
    untranslatedLangCode: String? = null
) {
    val isFromMe = message.senderId == localNodeId
    val isEmergency = message.priority == MessagePriority.EMERGENCY
    var showDetailsDialog by remember { mutableStateOf(false) }

    val parsed = remember(message.content, localLanguage) { parseMessageContent(message.content, localLanguage) }
    val displayTranslationBadge = parsed.translationBadge ?: untranslatedLangCode
    val isUntranslatedBadge = parsed.isUntranslated || (untranslatedLangCode != null && parsed.translationBadge == null)

    Card(
        onClick = {
            showDetailsDialog = true
        },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isEmergency -> AstraCrimson.copy(alpha = 0.12f)
                isFromMe -> AstraCyan.copy(alpha = 0.08f)
                else -> AstraSurfaceVariant.copy(alpha = 0.7f)
            }
        ),
        border = if (isEmergency) BorderStroke(1.dp, AstraCrimson.copy(alpha = 0.4f)) else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Top Row: [TX/RX], Translation Chip, Sender info, Time, Status, Replay
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val directionTag = if (isFromMe) "TX" else "RX"
                    val tagColor = when {
                        isEmergency -> AstraCrimson
                        isFromMe -> AstraCyan
                        else -> AstraEmerald
                    }
                    Text(
                        text = "[$directionTag]",
                        color = tagColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    // Compact Translation or Untranslated chip
                    if (displayTranslationBadge != null) {
                        if (isUntranslatedBadge) {
                            // Themed outlined untranslated badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, AstraTextSecondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .background(Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$displayTranslationBadge • ORIGINAL",
                                    color = AstraTextSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            // Solid compact translated chip: EN → HI
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isEmergency) AstraCrimson.copy(alpha = 0.25f) else AstraCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = displayTranslationBadge,
                                    color = if (isEmergency) AstraCrimson else AstraCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Sender info
                    val hopText = if (message.hopCount == 0) "Direct" else "${message.hopCount}H"
                    val senderTag = if (isFromMe) "Self • $hopText" else "Node ${message.senderId.toHex().take(6)} • $hopText"
                    Text(
                        text = senderTag,
                        color = AstraTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right controls: Time, Status, Replay icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = AstraTextSecondary,
                        fontSize = 10.sp
                    )

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

                    // Replay button
                    IconButton(
                        onClick = onReplay,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Replay Audio",
                            tint = if (isEmergency) AstraCrimson else AstraCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Message Body text: 2 lines collapsed preview in list
            Text(
                text = parsed.cleanText,
                color = AstraTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Popup Modal Dialog for Detailed Inspection
    if (showDetailsDialog) {
        Dialog(
            onDismissRequest = { showDetailsDialog = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AstraSurface)
                    .border(
                        1.dp,
                        if (isEmergency) AstraCrimson.copy(alpha = 0.8f) else AstraCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header: Title & Direction Tag & Close Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val directionTag = if (isFromMe) "TRANSMISSION (TX)" else "RECEPTION (RX)"
                            val tagColor = when {
                                isEmergency -> AstraCrimson
                                isFromMe -> AstraCyan
                                else -> AstraEmerald
                            }
                            Text(
                                text = directionTag,
                                color = tagColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isEmergency) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AstraCrimson.copy(alpha = 0.2f))
                                        .border(1.dp, AstraCrimson, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "EMERGENCY",
                                        color = AstraCrimson,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showDetailsDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = AstraTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = AstraOutline.copy(alpha = 0.3f), thickness = 0.5.dp)

                    // Full Message Content Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AstraBackground.copy(alpha = 0.8f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "MESSAGE CONTENT",
                            color = AstraTextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = parsed.cleanText,
                            color = AstraTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 18.sp
                        )
                    }

                    // Metadata Details Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AstraSurfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val fullDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                        val fullDateStr = fullDateFormat.format(Date(message.timestamp))
                        val senderStr = if (isFromMe) "Self (${localNodeId.toHex()})" else "Node ${message.senderId.toHex()}"
                        val hopStr = if (message.hopCount == 0) "Direct (0 Hops)" else "${message.hopCount} Hops (TTL: ${message.ttl})"
                        val translationStr = displayTranslationBadge ?: (localLanguage?.englishName ?: "Standard")
                        val statusStr = "${message.status.name} • $hopStr"

                        TacticalDetailRow(label = "SENDER:", value = senderStr, valueColor = if (isFromMe) AstraCyan else AstraTextPrimary)
                        TacticalDetailRow(label = "TIMESTAMP:", value = fullDateStr)
                        TacticalDetailRow(label = "LANGUAGE / PAIR:", value = translationStr, valueColor = if (isEmergency) AstraCrimson else AstraCyan)
                        TacticalDetailRow(label = "STATUS & ROUTE:", value = statusStr, valueColor = AstraEmerald)
                        TacticalDetailRow(label = "PRIORITY:", value = if (isEmergency) "EMERGENCY DISTRESS" else message.priority.name, valueColor = if (isEmergency) AstraCrimson else AstraTextSecondary)
                        TacticalDetailRow(label = "MESSAGE ID:", value = message.id.value)
                    }

                    // Actions: Replay Audio & Dismiss
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onReplay()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isEmergency) AstraCrimson else AstraCyan
                            ),
                            border = BorderStroke(1.dp, if (isEmergency) AstraCrimson.copy(alpha = 0.6f) else AstraCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REPLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { showDetailsDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEmergency) AstraCrimson else AstraCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("DISMISS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TacticalDetailRow(
    label: String,
    value: String,
    valueColor: Color = AstraTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = AstraTextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
