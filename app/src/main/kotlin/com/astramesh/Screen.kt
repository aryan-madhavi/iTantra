package com.astramesh

sealed class Screen(val route: String) {
    data object ChatList : Screen("chat_list")
    data object Conversation : Screen("conversation/{chatId}/{recipientId}") {
        fun createRoute(chatId: String, recipientId: Long): String = "conversation/$chatId/$recipientId"
    }
    data object NearbyRadar : Screen("nearby_radar")
    data object Settings : Screen("settings")
}
