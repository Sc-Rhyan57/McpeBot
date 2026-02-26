package com.astralconnector.lan

import com.astralconnector.model.ServerConfig
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class LanBroadcaster(private val config: ServerConfig) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var socket: DatagramSocket? = null

    fun start() {
        job = scope.launch {
            try {
                socket = DatagramSocket()
                socket!!.broadcast = true
                while (isActive) {
                    broadcast()
                    delay(1500)
                }
            } catch (_: Exception) {}
        }
    }

    private fun broadcast() {
        try {
            val motd = buildMotd()
            val data = motd.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                data, data.size,
                InetAddress.getByName("255.255.255.255"),
                19132
            )
            socket?.send(packet)
        } catch (_: Exception) {}
    }

    private fun buildMotd(): String {
        return "MCPE;${config.motd};${PROTOCOL};${VERSION};0;${config.maxPlayers};${System.currentTimeMillis()};${config.name};Survival;1;${config.lanPort};${config.lanPort};"
    }

    fun stop() {
        job?.cancel()
        socket?.close()
        socket = null
    }

    companion object {
        const val PROTOCOL = 686
        const val VERSION = "1.21.0"
        const val BROADCAST_PORT = 19132
    }
}
