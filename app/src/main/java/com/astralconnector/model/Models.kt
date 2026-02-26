package com.astralconnector.model

import kotlinx.serialization.Serializable

enum class PlayerPlatform(val display: String) {
    ANDROID("Android"),
    IOS("iOS"),
    WINDOWS("Windows"),
    XBOX("Xbox"),
    PS4("PS4"),
    PS5("PS5"),
    SWITCH("Switch"),
    UNKNOWN("Unknown")
}

enum class ChatType { CHAT, JOIN, LEAVE, SYSTEM }

enum class ProxyState { IDLE, CONNECTING, RUNNING, STOPPING, ERROR }

@Serializable
data class ServerConfig(
    val host: String = "",
    val port: Int = 19132,
    val lanPort: Int = 19132,
    val motd: String = "Astral Connector",
    val name: String = "Astral",
    val maxPlayers: Int = 20,
    val ownerName: String = "",
    val welcomeMessage: String = ""
)

@Serializable
data class SavedServer(
    val name: String,
    val host: String,
    val port: Int
)

data class ConnectedPlayer(
    val id: String,
    val username: String,
    val address: String,
    val port: Int,
    val xuid: String = "",
    val platform: PlayerPlatform = PlayerPlatform.UNKNOWN,
    val connectedAt: Long = System.currentTimeMillis(),
    val ping: Long = 0L,
    val clientVersion: String = "",
    val titleId: String = ""
)

data class ChatMessage(
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatType = ChatType.CHAT
)

data class ServerInfo(
    val motd: String = "",
    val edition: String = "MCPE",
    val protocol: Int = 0,
    val version: String = "",
    val playerCount: Int = 0,
    val maxPlayers: Int = 0,
    val gameMode: String = "Survival",
    val ping: Long = -1L
)

data class NetworkInfo(
    val ipv4: String = "",
    val ipv6: String = "",
    val ssid: String = "",
    val externalIp: String = "",
    val proxyPort: Int = 19132,
    val gatewayIp: String = ""
)

data class PlayerBan(
    val xuid: String,
    val username: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProxyStats(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val packetsIn: Long = 0L,
    val packetsOut: Long = 0L,
    val totalConnections: Int = 0,
    val uptime: Long = 0L,
    val startedAt: Long = 0L
)

@Serializable
data class XboxAccount(
    val gamertag: String = "",
    val xuid: String = "",
    val accessToken: String = "",
    val userHash: String = "",
    val identityToken: String = "",
    val expiresAt: Long = 0L
)

data class PlayerJoinEvent(
    val username: String,
    val platform: PlayerPlatform,
    val timestamp: Long = System.currentTimeMillis()
)
