package com.astralconnector.proxy

import com.astralconnector.model.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.InflaterInputStream

class ClientSession(
    val clientAddress: InetSocketAddress,
    private val serverHost: String,
    private val serverPort: Int,
    private val proxySocket: DatagramSocket,
    private val xboxAccount: XboxAccount? = null,
    private val onChat: (ChatMessage) -> Unit,
    private val onLog: (String) -> Unit,
    private val onPlayerConnected: (ConnectedPlayer) -> Unit,
    private val onPlayerDisconnected: (String) -> Unit,
) {
    val id: String = UUID.randomUUID().toString()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private val toServer = ConcurrentLinkedQueue<ByteArray>()
    private val toClient = ConcurrentLinkedQueue<ByteArray>()

    var player: ConnectedPlayer? = null
        private set

    private var pingMs = 0L
    private var lastPingTime = 0L
    private val _bytesIn = AtomicLong(0)
    private val _bytesOut = AtomicLong(0)
    private var lastActivity = System.currentTimeMillis()
    private var loginProcessed = false
    private val splitPackets = mutableMapOf<Int, MutableMap<Int, ByteArray>>()

    fun start() {
        scope.launch {
            try {
                serverAddress = InetAddress.getByName(serverHost)
                serverSocket = DatagramSocket().apply { soTimeout = 2000 }
                startServerReceive()
                startForwardLoop()
                startTimeoutWatch()
            } catch (e: Exception) {
                onLog("Session error [${clientAddress.address.hostAddress}]: ${e.message}")
                stop()
            }
        }
    }

    private fun startTimeoutWatch() = scope.launch {
        while (isActive) {
            delay(15000)
            if (System.currentTimeMillis() - lastActivity > 90000) {
                onLog("Session timeout: ${clientAddress.address.hostAddress}")
                stop(); break
            }
        }
    }

    private fun startServerReceive() = scope.launch {
        val buf = ByteArray(65535)
        while (isActive) {
            try {
                val pkt = DatagramPacket(buf, buf.size)
                serverSocket?.receive(pkt)
                val data = pkt.data.copyOf(pkt.length)
                _bytesIn.addAndGet(data.size.toLong())
                lastActivity = System.currentTimeMillis()
                interceptServerPacket(data)
                toClient.add(data)
            } catch (_: java.net.SocketTimeoutException) {
            } catch (_: Exception) { if (!isActive) break }
        }
    }

    private fun startForwardLoop() = scope.launch {
        while (isActive) {
            toClient.poll()?.let {
                try {
                    proxySocket.send(DatagramPacket(it, it.size, clientAddress.address, clientAddress.port))
                    _bytesOut.addAndGet(it.size.toLong())
                } catch (_: Exception) {}
            }
            toServer.poll()?.let {
                try {
                    serverSocket?.send(DatagramPacket(it, it.size, serverAddress, serverPort))
                    _bytesOut.addAndGet(it.size.toLong())
                } catch (_: Exception) {}
            }
            delay(1)
        }
    }

    fun receiveFromClient(data: ByteArray) {
        lastActivity = System.currentTimeMillis()
        interceptClientPacket(data)
        toServer.add(data)
    }

    private fun interceptClientPacket(data: ByteArray) {
        if (data.isEmpty()) return
        val id = data[0].toInt() and 0xFF
        if (id in 0x80..0x8F) {
            extractFrames(data) { payload -> processBedrockBatch(payload, fromClient = true) }
        }
    }

    private fun interceptServerPacket(data: ByteArray) {
        if (data.isEmpty()) return
        val id = data[0].toInt() and 0xFF
        when (id) {
            0x00 -> lastPingTime = System.currentTimeMillis()
            0x03 -> if (lastPingTime > 0) {
                pingMs = System.currentTimeMillis() - lastPingTime
                player = player?.copy(ping = pingMs)
            }
            0x15 -> { stop(); return }
        }
        if (id in 0x80..0x8F) {
            extractFrames(data) { payload -> processBedrockBatch(payload, fromClient = false) }
        }
    }

    private fun extractFrames(data: ByteArray, handler: (ByteArray) -> Unit) {
        try {
            var pos = 4
            while (pos + 3 <= data.size) {
                val flagsByte = data[pos].toInt() and 0xFF
                val reliability = (flagsByte shr 5) and 0x07
                val hasSplit = (flagsByte and 0x10) != 0
                pos++
                val bitLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                val byteLen = (bitLen + 7) / 8
                pos += 2
                if (reliability in intArrayOf(2, 3, 4, 6, 7)) pos += 3
                if (reliability in intArrayOf(1, 3, 4, 6, 7)) pos += 3

                var splitCount = 0; var splitId = 0; var splitIndex = 0
                if (hasSplit) {
                    if (pos + 10 > data.size) break
                    splitCount = readInt32(data, pos); splitId = readUShort(data, pos + 4)
                    splitIndex = readInt32(data, pos + 6); pos += 10
                }
                if (byteLen <= 0 || pos + byteLen > data.size) break
                val payload = data.copyOfRange(pos, pos + byteLen)
                pos += byteLen

                val frame: ByteArray? = if (hasSplit && splitCount > 1) {
                    val map = splitPackets.getOrPut(splitId) { mutableMapOf() }
                    map[splitIndex] = payload
                    if (map.size == splitCount) {
                        val assembled = (0 until splitCount).flatMap { map[it]!!.toList() }.toByteArray()
                        splitPackets.remove(splitId); assembled
                    } else null
                } else payload

                if (frame != null && frame.isNotEmpty() && frame[0].toInt() and 0xFF == 0xFE) {
                    handler(frame.copyOfRange(1, frame.size))
                }
            }
        } catch (_: Exception) {}
    }

    private fun readInt32(d: ByteArray, o: Int) =
        ((d[o].toInt() and 0xFF) shl 24) or ((d[o+1].toInt() and 0xFF) shl 16) or
        ((d[o+2].toInt() and 0xFF) shl 8) or (d[o+3].toInt() and 0xFF)

    private fun readUShort(d: ByteArray, o: Int) =
        ((d[o].toInt() and 0xFF) shl 8) or (d[o+1].toInt() and 0xFF)

    private fun processBedrockBatch(batch: ByteArray, fromClient: Boolean) {
        val decompressed = tryDecompress(batch) ?: return
        var pos = 0
        while (pos < decompressed.size) {
            val (pktLen, lenSize) = readVarInt(decompressed, pos)
            if (lenSize == 0 || pktLen <= 0) break
            pos += lenSize
            if (pos + pktLen > decompressed.size) break
            val pkt = decompressed.copyOfRange(pos, pos + pktLen)
            pos += pktLen
            if (pkt.isEmpty()) continue
            val (packetId, idSize) = readVarInt(pkt, 0)
            handleBedrockPacket(packetId and 0x3FF, pkt, idSize, fromClient)
        }
    }

    private fun tryDecompress(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return null
        return try {
            InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        } catch (_: Exception) { data }
    }

    private fun handleBedrockPacket(id: Int, data: ByteArray, offset: Int, fromClient: Boolean) {
        when (id) {
            0x01 -> if (fromClient && !loginProcessed) parseLogin(data)
            0x09 -> if (!fromClient) parseText(data, offset)
        }
    }

    private fun parseLogin(data: ByteArray) {
        try {
            if (loginProcessed) return
            val raw = String(data, Charsets.UTF_8)
            val username = Regex("\"displayName\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: return
            val xuid = Regex("\"XUID\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: ""
            val deviceOS = Regex("\"DeviceOS\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val version = Regex("\"GameVersion\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: ""
            loginProcessed = true

            val platform = when (deviceOS) {
                1 -> PlayerPlatform.ANDROID; 2 -> PlayerPlatform.IOS
                7 -> PlayerPlatform.WINDOWS; 12 -> PlayerPlatform.XBOX
                13 -> PlayerPlatform.PS4; 14 -> PlayerPlatform.PS5
                11 -> PlayerPlatform.SWITCH; else -> PlayerPlatform.UNKNOWN
            }

            val p = ConnectedPlayer(
                id = id,
                username = username,
                address = clientAddress.address.hostAddress ?: "unknown",
                port = clientAddress.port,
                xuid = xuid,
                platform = platform,
                clientVersion = version
            )
            player = p
            onLog("Login: $username [${platform.display}] XUID=$xuid v=$version")
            onPlayerConnected(p)
            onChat(ChatMessage("[JOIN]", "$username joined the game", type = ChatType.JOIN))
        } catch (e: Exception) {
            onLog("Login parse error: ${e.message}")
        }
    }

    private fun parseText(data: ByteArray, offset: Int) {
        try {
            var pos = offset
            if (pos >= data.size) return
            val type = data[pos++].toInt() and 0xFF
            if (pos >= data.size) return
            pos++

            val sourceName = if (type in intArrayOf(1, 7, 8)) {
                val (s, n) = readVarString(data, pos); pos += n; s
            } else ""

            if (pos >= data.size) return
            val (msg, _) = readVarString(data, pos)
            val clean = msg.replace(Regex("§[0-9a-fklmnorA-FKLMNOR]"), "").trim()
            if (clean.isBlank() || clean.startsWith("{")) return

            val chatType = when (type) { 1, 8 -> ChatType.CHAT; else -> ChatType.SYSTEM }
            val sender = sourceName.ifEmpty { if (type == 0 || type == 7) "Server" else player?.username ?: "?" }
            onChat(ChatMessage(sender = sender, message = clean, type = chatType))
        } catch (_: Exception) {}
    }

    fun sendChatToClient(message: String) {
        scope.launch {
            try {
                val textPayload = buildTextPacket(message)
                val compressed = zlibCompress(textPayload)
                val wrapped = byteArrayOf(0xFE.toByte()) + compressed
                val frame = buildSimpleFrame(wrapped)
                proxySocket.send(DatagramPacket(frame, frame.size, clientAddress.address, clientAddress.port))
            } catch (_: Exception) {}
        }
    }

    private fun buildTextPacket(message: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(writeVarInt(0x09))
        out.write(0x07)
        out.write(0x00)
        val serverBytes = "Server".toByteArray(Charsets.UTF_8)
        out.write(writeVarInt(serverBytes.size)); out.write(serverBytes)
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        out.write(writeVarInt(msgBytes.size)); out.write(msgBytes)
        out.write(writeVarInt(0))
        out.write(writeVarInt(0))
        val payload = out.toByteArray()
        val wrapper = ByteArrayOutputStream()
        wrapper.write(writeVarInt(payload.size)); wrapper.write(payload)
        return wrapper.toByteArray()
    }

    private fun zlibCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        val def = java.util.zip.DeflaterOutputStream(baos)
        def.write(data); def.finish(); def.close()
        return baos.toByteArray()
    }

    private fun buildSimpleFrame(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x84); out.write(byteArrayOf(0, 0, 0))
        val bitLen = payload.size * 8
        out.write(0x40)
        out.write((bitLen shr 8) and 0xFF); out.write(bitLen and 0xFF)
        out.write(byteArrayOf(0, 0, 0))
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeVarInt(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        do {
            var b = v and 0x7F; v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
        return out.toByteArray()
    }

    private fun readVarInt(data: ByteArray, pos: Int): Pair<Int, Int> {
        var result = 0; var shift = 0; var p = pos
        while (p < data.size) {
            val b = data[p++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7; if (shift >= 35) break
        }
        return Pair(result, p - pos)
    }

    private fun readVarString(data: ByteArray, pos: Int): Pair<String, Int> {
        if (pos >= data.size) return Pair("", 0)
        val (len, lenSize) = readVarInt(data, pos)
        val start = pos + lenSize
        if (len <= 0 || start + len > data.size) return Pair("", lenSize)
        return Pair(String(data, start, len, Charsets.UTF_8), lenSize + len)
    }

    fun sendKick(reason: String) {
        scope.launch {
            try {
                val kickPayload = buildKickPacket(reason)
                val compressed = zlibCompress(kickPayload)
                val wrapped = byteArrayOf(0xFE.toByte()) + compressed
                val frame = buildSimpleFrame(wrapped)
                proxySocket.send(DatagramPacket(frame, frame.size, clientAddress.address, clientAddress.port))
            } catch (_: Exception) {}
            try { proxySocket.send(DatagramPacket(byteArrayOf(0x15), 1, clientAddress.address, clientAddress.port)) } catch (_: Exception) {}
            delay(200); stop()
        }
    }

    private fun buildKickPacket(message: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(writeVarInt(0x02))
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        out.write(writeVarInt(msgBytes.size))
        out.write(msgBytes)
        val payload = out.toByteArray()
        val wrapper = ByteArrayOutputStream()
        wrapper.write(writeVarInt(payload.size)); wrapper.write(payload)
        return wrapper.toByteArray()
    }

    fun getPing() = pingMs
    fun getBytesIn() = _bytesIn.get()
    fun getBytesOut() = _bytesOut.get()

    fun stop() {
        if (!scope.isActive) return
        player?.let { onChat(ChatMessage("[LEAVE]", "${it.username} left the game", type = ChatType.LEAVE)) }
        onPlayerDisconnected(id)
        scope.cancel()
        serverSocket?.close()
        serverSocket = null
    }
}
