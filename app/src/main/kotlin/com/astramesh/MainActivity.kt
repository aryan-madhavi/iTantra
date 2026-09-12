package com.astramesh

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.astramesh.core.ChatId
import com.astramesh.core.NodeId
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.DiscoverPeersUseCase
import com.astramesh.domain.usecase.SendMessageUseCase
import com.astramesh.domain.usecase.VerifyPeerTrustUseCase
import com.astramesh.feature.chat.ChatListScreen
import com.astramesh.feature.chat.ChatListViewModel
import com.astramesh.feature.chat.ConversationScreen
import com.astramesh.feature.chat.ConversationViewModel
import com.astramesh.feature.nearby.NearbyPeersScreen
import com.astramesh.feature.nearby.NearbyPeersViewModel
import com.astramesh.feature.settings.SettingsScreen
import com.astramesh.feature.settings.SettingsViewModel
import com.astramesh.services.AstraMeshForegroundService
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraSurfaceVariant
import com.astramesh.ui.theme.AstraTheme
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Radio
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.usecase.EmergencyBroadcastUseCase
import com.astramesh.ui.screens.ContactsScreen
import com.astramesh.ui.screens.EmergencyScreen
import com.astramesh.ui.screens.HomeScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.activity.result.contract.ActivityResultContracts
import com.astramesh.ble.BlePermissionManager
import com.astramesh.common.AstraLog

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var identityRepository: IdentityRepository
    @Inject lateinit var meshRepository: MeshRepository
    @Inject lateinit var peerRepository: PeerRepository
    @Inject lateinit var sendMessageUseCase: SendMessageUseCase
    @Inject lateinit var emergencyBroadcastUseCase: EmergencyBroadcastUseCase
    @Inject lateinit var discoverPeersUseCase: DiscoverPeersUseCase
    @Inject lateinit var verifyPeerTrustUseCase: VerifyPeerTrustUseCase
    @Inject lateinit var voiceEngineManager: com.astramesh.services.VoiceEngineManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        AstraLog.d("MainActivity", "Permissions result: allGranted=$allGranted")
        if (allGranted) {
            startMeshForegroundService()
            lifecycleScope.launchWhenResumed {
                meshRepository.startMesh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BlePermissionManager.hasPermissions(this)) {
            startMeshForegroundService()
        } else {
            AstraLog.d("MainActivity", "Requesting missing BLE permissions...")
            permissionLauncher.launch(BlePermissionManager.getRequiredPermissions())
        }

        setContent {
            AstraTheme {
                MainAppContent(
                    chatRepository = chatRepository,
                    messageRepository = messageRepository,
                    identityRepository = identityRepository,
                    meshRepository = meshRepository,
                    peerRepository = peerRepository,
                    sendMessageUseCase = sendMessageUseCase,
                    emergencyBroadcastUseCase = emergencyBroadcastUseCase,
                    discoverPeersUseCase = discoverPeersUseCase,
                    verifyPeerTrustUseCase = verifyPeerTrustUseCase,
                    voiceEngineManager = voiceEngineManager
                )
            }
        }
    }

    private fun startMeshForegroundService() {
        val intent = Intent(this, AstraMeshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun MainAppContent(
    chatRepository: ChatRepository,
    messageRepository: MessageRepository,
    identityRepository: IdentityRepository,
    meshRepository: MeshRepository,
    peerRepository: PeerRepository,
    sendMessageUseCase: SendMessageUseCase,
    emergencyBroadcastUseCase: EmergencyBroadcastUseCase,
    discoverPeersUseCase: DiscoverPeersUseCase,
    verifyPeerTrustUseCase: VerifyPeerTrustUseCase,
    voiceEngineManager: com.astramesh.services.VoiceEngineManager? = null
) {
    var localNodeId by remember { mutableStateOf(NodeId(0L)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        localNodeId = identityRepository.getRotatingNodeId()
        if (BlePermissionManager.hasPermissions(context)) {
            AstraLog.d("MainActivity", "Permissions granted, starting MeshEngine...")
            meshRepository.startMesh()
        } else {
            AstraLog.d("MainActivity", "Permissions not granted yet, waiting for user grant...")
        }
    }

    val navController = rememberNavController()
    MainAppScaffold(
        navController = navController,
        localNodeId = localNodeId,
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        identityRepository = identityRepository,
        meshRepository = meshRepository,
        peerRepository = peerRepository,
        sendMessageUseCase = sendMessageUseCase,
        emergencyBroadcastUseCase = emergencyBroadcastUseCase,
        discoverPeersUseCase = discoverPeersUseCase,
        verifyPeerTrustUseCase = verifyPeerTrustUseCase,
        voiceEngineManager = voiceEngineManager
    )
}

@Composable
fun MainAppScaffold(
    navController: NavHostController,
    localNodeId: NodeId,
    chatRepository: ChatRepository,
    messageRepository: MessageRepository,
    identityRepository: IdentityRepository,
    meshRepository: MeshRepository,
    peerRepository: PeerRepository,
    sendMessageUseCase: SendMessageUseCase,
    emergencyBroadcastUseCase: EmergencyBroadcastUseCase,
    discoverPeersUseCase: DiscoverPeersUseCase,
    verifyPeerTrustUseCase: VerifyPeerTrustUseCase,
    voiceEngineManager: com.astramesh.services.VoiceEngineManager? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isConversationScreen = currentRoute?.startsWith("conversation") == true

    Scaffold(
        bottomBar = {
            if (!isConversationScreen) {
                NavigationBar(
                    containerColor = AstraSurface
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Radio, contentDescription = "Transceiver") },
                        label = { Text("Transceiver") },
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            if (currentRoute != Screen.Home.route) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AstraCyan,
                            selectedTextColor = AstraCyan,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = AstraSurfaceVariant
                        )
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, contentDescription = "Mesh Nodes") },
                        label = { Text("Mesh Nodes") },
                        selected = currentRoute == Screen.Contacts.route,
                        onClick = {
                            if (currentRoute != Screen.Contacts.route) {
                                navController.navigate(Screen.Contacts.route)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AstraCyan,
                            selectedTextColor = AstraCyan,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = AstraSurfaceVariant
                        )
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Emergency, contentDescription = "SOS Beacon") },
                        label = { Text("SOS Beacon") },
                        selected = currentRoute == Screen.Emergency.route,
                        onClick = {
                            if (currentRoute != Screen.Emergency.route) {
                                navController.navigate(Screen.Emergency.route)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.astramesh.ui.theme.AstraCrimson,
                            selectedTextColor = com.astramesh.ui.theme.AstraCrimson,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = AstraSurfaceVariant
                        )
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            if (currentRoute != Screen.Settings.route) {
                                navController.navigate(Screen.Settings.route)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AstraCyan,
                            selectedTextColor = AstraCyan,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = AstraSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        localNodeId = localNodeId,
                        peerRepository = peerRepository,
                        messageRepository = messageRepository,
                        chatRepository = chatRepository,
                        identityRepository = identityRepository,
                        sendMessageUseCase = sendMessageUseCase,
                        meshRepository = meshRepository,
                        voiceEngineManager = voiceEngineManager,
                        onNavigateToContacts = { navController.navigate(Screen.Contacts.route) },
                        onNavigateToEmergency = { navController.navigate(Screen.Emergency.route) },
                        onOpenConversation = { chatId, recipientId ->
                            navController.navigate(Screen.Conversation.createRoute(chatId, recipientId))
                        }
                    )
                }

                composable(Screen.Contacts.route) {
                    ContactsScreen(
                        localNodeId = localNodeId,
                        peerRepository = peerRepository,
                        onOpenConversation = { chatId, recipientId ->
                            navController.navigate(Screen.Conversation.createRoute(chatId, recipientId))
                        }
                    )
                }

                composable(Screen.Emergency.route) {
                    EmergencyScreen(
                        localNodeId = localNodeId,
                        emergencyBroadcastUseCase = emergencyBroadcastUseCase,
                        messageRepository = messageRepository,
                        meshRepository = meshRepository,
                        voiceEngineManager = voiceEngineManager,
                        onBackClicked = { navController.popBackStack() }
                    )
                }

                composable(Screen.ChatList.route) {
                    val viewModel = remember { ChatListViewModel(chatRepository, peerRepository) }
                    ChatListScreen(
                        viewModel = viewModel,
                        onChatClicked = { chat ->
                            val recipient = chat.participantIds.firstOrNull { it != localNodeId } ?: localNodeId
                            navController.navigate(Screen.Conversation.createRoute(chat.id.value, recipient.value))
                        },
                        onNewChatClicked = {
                            navController.navigate(Screen.NearbyRadar.route)
                        }
                    )
                }

                composable(Screen.Conversation.route) { backStackEntry ->
                    val chatIdStr = backStackEntry.arguments?.getString("chatId") ?: ""
                    val recipientIdStr = backStackEntry.arguments?.getString("recipientId") ?: "0"
                    val recipientNodeId = NodeId(recipientIdStr.toLongOrNull() ?: 0L)
                    val chatId = ChatId(chatIdStr)

                    val viewModel = remember(chatIdStr) {
                        ConversationViewModel(
                            chatId = chatId,
                            recipientId = recipientNodeId,
                            messageRepository = messageRepository,
                            sendMessageUseCase = sendMessageUseCase,
                            peerRepository = peerRepository,
                            meshRepository = meshRepository,
                            voiceEngineManager = voiceEngineManager
                        )
                    }

                    ConversationScreen(
                        viewModel = viewModel,
                        onBackClicked = { navController.popBackStack() },
                        localNodeId = localNodeId
                    )
                }

                composable(Screen.NearbyRadar.route) {
                    val viewModel = remember {
                        NearbyPeersViewModel(discoverPeersUseCase, verifyPeerTrustUseCase)
                    }
                    NearbyPeersScreen(
                        viewModel = viewModel,
                        onConnectClicked = { peer ->
                            navController.navigate(Screen.Conversation.createRoute("direct_${peer.nodeId.value}", peer.nodeId.value))
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    val viewModel = remember {
                        SettingsViewModel(identityRepository, meshRepository)
                    }
                    SettingsScreen(
                        viewModel = viewModel,
                        onShowQrClicked = {}
                    )
                }
            }
        }
    }
}
