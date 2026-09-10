package com.astramesh

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Contacts : Screen("contacts")
    data object ChatList : Screen("chat_list")
    data object Conversation : Screen("conversation/{chatId}/{recipientId}") {
        fun createRoute(chatId: String, recipientId: Long): String = "conversation/$chatId/$recipientId"
    }
    data object NearbyRadar : Screen("nearby_radar")
    data object Emergency : Screen("emergency")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
}
