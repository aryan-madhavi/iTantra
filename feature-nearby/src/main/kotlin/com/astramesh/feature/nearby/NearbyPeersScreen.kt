package com.astramesh.feature.nearby

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.domain.model.Peer
import com.astramesh.domain.model.PeerTrustLevel
import com.astramesh.ui.components.AstraTopBar
import com.astramesh.ui.components.SignalStrengthIndicator
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraOutline
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraSurfaceVariant
import com.astramesh.ui.theme.AstraTextPrimary
import com.astramesh.ui.theme.AstraTextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NearbyPeersScreen(
    viewModel: NearbyPeersViewModel,
    onConnectClicked: (Peer) -> Unit
) {
    val peers by viewModel.peers.collectAsState()

    Scaffold(
        topBar = {
            AstraTopBar(
                title = "BLE Radar Discovery",
                connectedPeersCount = peers.size
            )
        },
        containerColor = AstraBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Animated Radar Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(AstraSurface),
                contentAlignment = Alignment.Center
            ) {
                RadarSweepCanvas(peers = peers)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DISCOVERED PEERS (${peers.size})",
                color = AstraTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(peers, key = { it.nodeId.value }) { peer ->
                    PeerCard(
                        peer = peer,
                        onConnect = { onConnectClicked(peer) },
                        onVerify = { viewModel.updateTrust(peer.nodeId, PeerTrustLevel.VERIFIED_IN_PERSON) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun RadarSweepCanvas(peers: List<Peer>) {
    val transition = rememberInfiniteTransition(label = "radarSweep")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = Modifier.size(240.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2

        // Radar Rings
        drawCircle(color = AstraOutline, radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = AstraOutline, radius = radius * 0.66f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = AstraOutline, radius = radius * 0.33f, center = center, style = Stroke(width = 1.dp.toPx()))

        // Crosshairs
        drawLine(color = AstraOutline, start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
        drawLine(color = AstraOutline, start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 1.dp.toPx())

        // Sonar Sweep Line
        val rad = Math.toRadians(angle.toDouble())
        val endX = center.x + (radius * cos(rad)).toFloat()
        val endY = center.y + (radius * sin(rad)).toFloat()
        drawLine(color = AstraCyan, start = center, end = Offset(endX, endY), strokeWidth = 2.dp.toPx())

        // Draw Peer Blips
        for ((index, peer) in peers.withIndex()) {
            val peerAngle = (index * 72) % 360
            val peerRad = Math.toRadians(peerAngle.toDouble())
            // Distance proportional to RSSI (-40 is close, -100 is far)
            val distanceRatio = ((peer.rssi.coerceIn(-100, -40) + 100) / 60f).coerceIn(0.15f, 0.9f)
            val bx = center.x + (radius * distanceRatio * cos(peerRad)).toFloat()
            val by = center.y + (radius * distanceRatio * sin(peerRad)).toFloat()

            drawCircle(color = AstraEmerald, radius = 6.dp.toPx(), center = Offset(bx, by))
        }
    }
}

@Composable
fun PeerCard(
    peer: Peer,
    onConnect: () -> Unit,
    onVerify: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AstraSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AstraCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AstraCyan)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName ?: peer.nodeId.toHex(),
                    color = AstraTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalStrengthIndicator(rssi = peer.rssi)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${peer.rssi} dBm",
                        color = AstraTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
