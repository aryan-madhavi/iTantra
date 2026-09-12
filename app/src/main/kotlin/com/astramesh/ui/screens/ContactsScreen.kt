package com.astramesh.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Peer
import com.astramesh.domain.repository.PeerRepository
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
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ContactsScreen(
    localNodeId: NodeId = NodeId(0L),
    peerRepository: PeerRepository,
    onOpenConversation: (chatId: String, recipientId: Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val peers by peerRepository.observeNearbyPeers().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var isRadarVisible by remember { mutableStateOf(true) }
    var peerToEdit by remember { mutableStateOf<Peer?>(null) }
    var editNameInput by remember { mutableStateOf("") }

    val filteredPeers = peers.filter {
        val name = it.displayName ?: "Node-${it.nodeId.toHex()}"
        name.contains(searchQuery, ignoreCase = true) || it.nodeId.toHex().contains(searchQuery, ignoreCase = true)
    }

    if (peerToEdit != null) {
        AlertDialog(
            onDismissRequest = { peerToEdit = null },
            title = { Text("Set Tactical Callsign", color = AstraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Assign a tactical callsign for node ${peerToEdit?.nodeId?.toHex()?.take(8)}:",
                        color = AstraTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        placeholder = { Text("e.g. ALPHA-1, SCOUT, COMMAND") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AstraCyan,
                            unfocusedBorderColor = AstraOutline,
                            focusedTextColor = AstraTextPrimary,
                            unfocusedTextColor = AstraTextPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val peer = peerToEdit
                        if (peer != null && editNameInput.isNotBlank()) {
                            scope.launch {
                                peerRepository.updatePeer(peer.copy(displayName = editNameInput.trim()))
                                AstraLog.d("ContactsScreen", "Updated callsign for ${peer.nodeId} to '${editNameInput.trim()}'")
                            }
                        }
                        peerToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCyan)
                ) {
                    Text("Save Callsign", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { peerToEdit = null }) {
                    Text("Cancel", color = AstraTextSecondary)
                }
            },
            containerColor = AstraSurface
        )
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
                            .background(if (peers.isNotEmpty()) AstraEmerald else AstraAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "iTANTRA MESH NODES",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = AstraTextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = if (localNodeId.value != 0L) {
                        "Node ${localNodeId.toHex().take(8)} • ${peers.size} PEERS ACTIVE"
                    } else {
                        "${peers.size} DISCOVERED NODES IN RANGE"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (peers.isNotEmpty()) AstraEmerald else AstraAmber
                )
            }

            // Radar toggle chip
            AssistChip(
                onClick = { isRadarVisible = !isRadarVisible },
                label = { Text(if (isRadarVisible) "RADAR ON" else "RADAR OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isRadarVisible) AstraCyan.copy(alpha = 0.2f) else AstraSurfaceVariant,
                    labelColor = if (isRadarVisible) AstraCyan else AstraTextSecondary,
                    leadingIconContentColor = if (isRadarVisible) AstraCyan else AstraTextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // ANIMATED TACTICAL RADAR VISUALIZER (SONAR SWEEP)
        // -------------------------------------------------------------
        AnimatedVisibility(visible = isRadarVisible) {
            Column {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    colors = CardDefaults.cardColors(containerColor = AstraSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        RadarSweepVisualizer(peers = peers)
                        // Overlay top-left & bottom-right telemetry tags
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "BLE RADAR SWEEP",
                                color = AstraCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            Text(
                                text = "${peers.size} TARGETS IN RANGE",
                                color = AstraEmerald,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter callsign or Node ID...", color = AstraTextSecondary, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AstraTextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AstraCyan,
                unfocusedBorderColor = AstraOutline,
                focusedTextColor = AstraTextPrimary,
                unfocusedTextColor = AstraTextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CellTower, contentDescription = null, tint = AstraTextSecondary, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Mesh Nodes Discovered Yet", color = AstraTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Nearby devices running iTantra BLE mesh will appear on radar.", color = AstraTextSecondary, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPeers) { peer ->
                    TacticalPeerCard(
                        peer = peer,
                        onOpenChat = {
                            onOpenConversation("direct_${peer.nodeId.value}", peer.nodeId.value)
                        },
                        onEditName = {
                            peerToEdit = peer
                            editNameInput = peer.displayName ?: ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RadarSweepVisualizer(peers: List<Peer>) {
    val transition = rememberInfiniteTransition(label = "radarSweep")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = Modifier.size(170.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2

        // Range Rings
        drawCircle(color = AstraOutline, radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = AstraOutline, radius = radius * 0.66f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = AstraOutline, radius = radius * 0.33f, center = center, style = Stroke(width = 1.dp.toPx()))

        // Crosshairs
        drawLine(color = AstraOutline.copy(alpha = 0.5f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
        drawLine(color = AstraOutline.copy(alpha = 0.5f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 1.dp.toPx())

        // Sonar Sweep Line
        val rad = Math.toRadians(angle.toDouble())
        val endX = center.x + (radius * cos(rad)).toFloat()
        val endY = center.y + (radius * sin(rad)).toFloat()
        drawLine(color = AstraCyan, start = center, end = Offset(endX, endY), strokeWidth = 2.dp.toPx())

        // Center Local Node dot
        drawCircle(color = AstraCyan, radius = 3.dp.toPx(), center = center)

        // Draw Peer Blips
        for ((index, peer) in peers.withIndex()) {
            val peerAngle = (index * 68 + 25) % 360
            val peerRad = Math.toRadians(peerAngle.toDouble())
            // Distance proportional to RSSI (-40 dBm is close to center, -100 dBm is near edge)
            val distanceRatio = ((100 + peer.rssi.coerceIn(-100, -40)) / 60f).let { 1f - it }.coerceIn(0.18f, 0.88f)
            val bx = center.x + (radius * distanceRatio * cos(peerRad)).toFloat()
            val by = center.y + (radius * distanceRatio * sin(peerRad)).toFloat()

            // Outer blip glow + inner solid blip
            drawCircle(color = AstraEmerald.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(bx, by))
            drawCircle(color = AstraEmerald, radius = 3.5.dp.toPx(), center = Offset(bx, by))
        }
    }
}

@Composable
fun TacticalPeerCard(
    peer: Peer,
    onOpenChat: () -> Unit,
    onEditName: () -> Unit
) {
    val name = peer.displayName
    val isKnownName = name != null && !name.startsWith("Node-")
    val displayName = name ?: "Node-${peer.nodeId.toHex().take(8)}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AstraSurface),
        shape = RoundedCornerShape(10.dp)
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
                        .background(if (isKnownName) AstraCyan.copy(alpha = 0.2f) else AstraSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(2).uppercase(),
                        color = if (isKnownName) AstraCyan else AstraTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            color = AstraTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (peer.rssi > -75) AstraEmerald else AstraAmber)
                        )
                    }
                    Text(
                        text = "ID: ${peer.nodeId.toHex().take(8)} • ${peer.rssi} dBm • Direct Hop",
                        color = AstraTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEditName,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Callsign",
                        tint = AstraTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onOpenChat,
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Talk Direct", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
