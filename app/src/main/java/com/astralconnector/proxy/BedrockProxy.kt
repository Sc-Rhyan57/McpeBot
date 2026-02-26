package com.astralconnector.proxy

import com.astralconnector.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class BedrockProxy(
    private val config: ServerConfig,
    private val xboxAccount: XboxAccount? = null,
    private val onLog: (String) -> Unit,
    private val onPlayerJoin: ((PlayerJoinEvent) -> Unit)? = null,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenSocket: DatagramSocket? = null
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private val bannedXuids = ConcurrentHashMap<String, PlayerBan>()
    private val bannedIps = ConcurrentHashMap<String, PlayerBan>()
    private val serverGuid = System.currentTimeMillis()

    private val _state = MutableStateFlow(ProxyState.IDLE)
    val state: StateFlow<ProxyState> = _state

    private val _players = MutableStateFlow<List<ConnectedPlayer>>(emptyList())
    val players: StateFlow<List<ConnectedPlayer>> = _players

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat

    private val _stats = MutableStateFlow(ProxyStats())
    val stats: StateFlow<ProxyStats> = _stats

    fun start() {
        _state.value = ProxyState.CONNECTING
        scope.launch {
            try {
                listenSocket = DatagramSocket(config.lanPort)
                listenSocket!!.soTimeout = 100
                _state.value = ProxyState.RUNNING
                onLog("Proxy started on port ${config.lanPort}")
                onLog("Forwarding to ${config.host}:${config.port}")
                if (xboxAccount != null) onLog("Xbox: ${xboxAccount.gamertag}")
                startStatsLoop()
                receiveLoop()
            } catch (e: Exception) {
                onLog("Proxy error: ${e.message}")
                _state.value = ProxyState.ERROR
            }
        }
    }

    private fun receiveLoop() {
        scope.launch {
            val buf = ByteArray(65535)
            while (isActive && _state.value == ProxyState.RUNNING) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    listenSocket?.receive(pkt)
                    val data = pkt.data.copyOf(pkt.length)
                    val addr = InetSocketAddress(pkt.address, pkt.port)
                    handlePacket(data, addr)
                } catch (_: java.net.SocketTimeoutException) {
                } catch (e: Exception) {
                    if (isActive) onLog("Receive error: ${e.message}")
                }
            }
        }
    }

    private fun handlePacket(data: ByteArray, addr: InetSocketAddress) {
        if (data.isEmpty()) return
        val id = data[0].toInt() and 0xFF
        val hostAddr: String = addr.address.hostAddress ?: addr.address.toString().trimStart('/')
        val key = "$hostAddr:${addr.port}"

        if (bannedIps.containsKey(hostAddr)) {
            sendBanned(addr); return
        }

        when (id) {
            RakNet.ID_UNCONNECTED_PING -> handlePing(data, addr)
            RakNet.ID_OPEN_CONNECTION_REQ_1,
            RakNet.ID_OPEN_CONNECTION_REQ_2 -> getOrCreate(key, addr).receiveFromClient(data)
            else -> sessions[key]?.receiveFromClient(data)
        }
        updateStats()
    }

    private fun getOrCreate(key: String, addr: InetSocketAddress): ClientSession =
        sessions.getOrPut(key) {
            ClientSession(
                clientAddress = addr,
                serverHost = config.host,
                serverPort = config.port,
                proxySocket = listenSocket!!,
                xboxAccount = xboxAccount,
                onChat = ::addChat,
                onPlayerConnected = ::onPlayerConnected,
                onPlayerDisconnected = { onPlayerDisconnected(it, key) },
                onLog = onLog,
            ).also { it.start() }
        }

    private fun handlePing(data: ByteArray, addr: InetSocketAddress) {
        try {
            if (data.size < 9) return
            val pingId = RakNet.readLong(data, 1)
            val pong = RakNet.buildPong(pingId, serverGuid, buildMotd())
            listenSocket?.send(DatagramPacket(pong, pong.size, addr.address, addr.port))
        } catch (_: Exception) {}
    }

    private fun sendBanned(addr: InetSocketAddress) {
        try {
            val pkt = byteArrayOf(RakNet.ID_CONNECTION_BANNED.toByte()) +
                    RakNet.OFFLINE_MAGIC + RakNet.writeLong(serverGuid)
            listenSocket?.send(DatagramPacket(pkt, pkt.size, addr.address, addr.port))
        } catch (_: Exception) {}
    }

    fun buildMotd(): String {
        val count = _players.value.size
        return "MCPE;${config.motd};686;1.21.0;$count;${config.maxPlayers};$serverGuid;${config.name};Survival;1;${config.lanPort};${config.lanPort};"
    }

    private fun onPlayerConnected(player: ConnectedPlayer) {
        if (bannedXuids.containsKey(player.xuid)) {
            val ban = bannedXuids[player.xuid]!!
            kickPlayer(player.id, "Banned: ${ban.reason}")
            return
        }
        val list = _players.value.toMutableList()
        list.removeIf { it.id == player.id }
        list.add(player)
        _players.value = list
        _stats.value = _stats.value.copy(totalConnections = _stats.value.totalConnections + 1)
        onLog("Player connected: ${player.username} [${player.platform.display}] ${player.address}:${player.port}")
        onPlayerJoin?.invoke(PlayerJoinEvent(player.username, player.platform))
    }

    private fun onPlayerDisconnected(sessionId: String, key: String) {
        val player = _players.value.find { it.id == sessionId }
        _players.value = _players.value.filter { it.id != sessionId }
        sessions.remove(key)
        player?.let { onLog("Player disconnected: ${it.username}") }
    }

    fun kickPlayer(playerId: String, reason: String) {
        val session = sessions.values.find { it.id == playerId } ?: return
        session.sendKick(reason)
        onLog("Kick: ${session.player?.username} - $reason")
    }

    fun banPlayer(playerId: String, reason: String) {
        val session = sessions.values.find { it.id == playerId } ?: return
        val player = session.player ?: return
        val ban = PlayerBan(xuid = player.xuid, username = player.username, reason = reason)
        if (player.xuid.isNotEmpty()) bannedXuids[player.xuid] = ban
        bannedIps[player.address] = ban
        session.sendKick("Banned: $reason")
        onLog("Ban: ${player.username} - $reason")
    }

    fun unbanPlayer(xuid: String) {
        bannedXuids.remove(xuid)
        onLog("Unbanned: $xuid")
    }

    fun getBans(): List<PlayerBan> = bannedXuids.values.toList()

    fun sendChatMessage(message: String) {
        sessions.values.forEach { it.sendChatToClient(message) }
    }

    private fun addChat(msg: ChatMessage) {
        val list = _chat.value.toMutableList()
        list.add(msg)
        if (list.size > 300) list.removeAt(0)
        _chat.value = list
    }

    private fun updateStats() {
        val totalIn = sessions.values.sumOf { it.getBytesIn() }
        val totalOut = sessions.values.sumOf { it.getBytesOut() }
        _stats.value = _stats.value.copy(bytesIn = totalIn, bytesOut = totalOut)
    }

    private fun startStatsLoop() {
        val startedAt = System.currentTimeMillis()
        scope.launch {
            while (isActive) {
                delay(1000)
                val uptime = System.currentTimeMillis() - startedAt
                val totalIn = sessions.values.sumOf { it.getBytesIn() }
                val totalOut = sessions.values.sumOf { it.getBytesOut() }
                _stats.value = _stats.value.copy(
                    uptime = uptime, startedAt = startedAt,
                    bytesIn = totalIn, bytesOut = totalOut
                )
                _players.value = _players.value.map { p ->
                    val s = sessions.values.find { it.id == p.id }
                    p.copy(ping = s?.getPing() ?: p.ping)
                }
            }
        }
    }

    fun stop() {
        _state.value = ProxyState.STOPPING
        sessions.values.forEach { it.stop() }
        sessions.clear()
        scope.cancel()
        listenSocket?.close()
        listenSocket = null
        _state.value = ProxyState.IDLE
        _players.value = emptyList()
        onLog("Proxy stopped")
    }
}
