package com.astramesh.feature.chat

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessageStatus
import com.astramesh.ui.theme.AstraBackground
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.recipientId.toHex(),
                            color = AstraTextPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Direct P2P Mesh Channel",
                            color = AstraEmerald,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AstraTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AstraSurface
                )
            )
        },
        containerColor = AstraBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                items(messages.reversed(), key = { it.id.value }) { message ->
                    val isFromMe = message.senderId == localNodeId
                    MessageBubble(message = message, isFromMe = isFromMe)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AstraSurface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write encrypted message...", color = AstraTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AstraTextPrimary,
                        unfocusedTextColor = AstraTextPrimary,
                        focusedBorderColor = AstraCyan,
                        unfocusedBorderColor = AstraOutline,
                        cursorColor = AstraCyan
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = viewModel::sendMessage,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AstraCyan)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean
) {
    val bubbleColor = if (isFromMe) AstraCyan.copy(alpha = 0.2f) else AstraSurfaceVariant
    val alignment = if (isFromMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isFromMe) 16.dp else 2.dp,
                        bottomEnd = if (isFromMe) 2.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    color = AstraTextPrimary,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = AstraTextSecondary,
                        fontSize = 11.sp
                    )
                    if (isFromMe) {
                        when (message.status) {
                            MessageStatus.QUEUED, MessageStatus.TRANSMITTING -> {
                                Text("...", color = AstraTextSecondary, fontSize = 11.sp)
                            }
                            MessageStatus.SENT, MessageStatus.RELAYED -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = AstraTextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.DELIVERED, MessageStatus.READ -> {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = AstraCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Text("!", color = Color.Red, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
