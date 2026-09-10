package com.astramesh.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.common.AstraLog
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Peer
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.ui.components.PulsingStatusDot
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraOutline
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraSurfaceVariant
import com.astramesh.ui.theme.AstraTextPrimary
import com.astramesh.ui.theme.AstraTextSecondary
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    peerRepository: PeerRepository,
    onOpenConversation: (chatId: String, recipientId: Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val peers by peerRepository.observeNearbyPeers().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var peerToEdit by remember { mutableStateOf<Peer?>(null) }
    var editNameInput by remember { mutableStateOf("") }

    val filteredPeers = peers.filter {
        val name = it.displayName ?: "Node-${it.nodeId.toHex()}"
        name.contains(searchQuery, ignoreCase = true) || it.nodeId.toHex().contains(searchQuery, ignoreCase = true)
    }

    if (peerToEdit != null) {
        AlertDialog(
            onDismissRequest = { peerToEdit = null },
            title = { Text("Set Contact Name", color = AstraTextPrimary) },
            text = {
                Column {
                    Text(
                        "Assign a friendly name for device ${peerToEdit?.nodeId?.toHex()?.take(8)}:",
                        color = AstraTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        placeholder = { Text("e.g. Rahul, Priya, Team Leader") },
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
                                AstraLog.d("ContactsScreen", "Updated contact name for ${peer.nodeId} to '${editNameInput.trim()}'")
                            }
                        }
                        peerToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCyan)
                ) {
                    Text("Save Contact", color = Color.Black, fontWeight = FontWeight.Bold)
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
            .padding(16.dp)
    ) {
        Text(
            text = "Contacts & Nearby Nodes",
            color = AstraTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Offline decentralized directory • Saved locally",
            color = AstraTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search contacts or node IDs...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AstraTextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AstraCyan,
                unfocusedBorderColor = AstraOutline,
                focusedTextColor = AstraTextPrimary,
                unfocusedTextColor = AstraTextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = AstraTextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No peers or contacts found", color = AstraTextSecondary, fontSize = 14.sp)
                    Text("Nearby devices discovered on BLE mesh will appear here.", color = AstraTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPeers) { peer ->
                    ContactCard(
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
fun ContactCard(
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isKnownName) AstraCyan.copy(alpha = 0.2f) else AstraSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = if (isKnownName) AstraCyan else AstraTextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            color = AstraTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PulsingStatusDot(isActive = true)
                    }
                    Text(
                        text = "ID: ${peer.nodeId.toHex().take(8)} • ${peer.rssi} dBm",
                        color = AstraTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditName) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        tint = AstraTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onOpenChat,
                    colors = ButtonDefaults.buttonColors(containerColor = AstraCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Talk", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
