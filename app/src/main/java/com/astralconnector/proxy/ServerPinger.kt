package com.astralconnector.proxy

import com.astralconnector.model.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ServerPinger {

    private val OFFLINE_MAGIC = byteArrayOf(
        0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00,
        0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
        0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(),
        0x12, 0x34, 0x56, 0x78
    )

    suspend fun ping(host: String, port: Int, timeout: Int = 3000): ServerInfo? =
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = timeout
                val addr = InetAddress.getByName(host)

                val pingId = System.currentTimeMillis()
                val buf = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN)
                buf.put(0x01)
                buf.putLong(pingId)
                buf.put(OFFLINE_MAGIC)
                buf.putLong(0L)
                val pingBytes = buf.array()

                val start = System.currentTimeMillis()
                socket.send(DatagramPacket(pingBytes, pingBytes.size, addr, port))

                val recvBuf = ByteArray(2048)
                val recvPkt = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(recvPkt)
                val pingMs = System.currentTimeMillis() - start

                val data = recvPkt.data.copyOf(recvPkt.length)
                if (data.isEmpty() || data[0] != 0x1C.toByte()) return@withContext null

                // Skip: 1 (id) + 8 (pingId) + 8 (serverGuid) + 16 (magic) + 2 (strLen) = 35
                val motdOffset = 35
                if (data.size <= motdOffset + 2) return@withContext null

                val strLen = ((data[motdOffset].toInt() and 0xFF) shl 8) or
                        (data[motdOffset + 1].toInt() and 0xFF)
                if (data.size < motdOffset + 2 + strLen) return@withContext null

                val motdStr = String(data, motdOffset + 2, strLen, Charsets.UTF_8)
                parsePong(motdStr, pingMs)
            } catch (_: Exception) {
                null
            } finally {
                socket?.close()
            }
        }

    private fun parsePong(pong: String, pingMs: Long): ServerInfo {
        val p = pong.split(";")
        return ServerInfo(
            edition = p.getOrElse(0) { "MCPE" },
            motd = p.getOrElse(1) { pong }.replace(Regex("§[0-9a-fklmnorA-FKLMNOR]"), "").trim(),
            protocol = p.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
            version = p.getOrElse(3) { "" },
            playerCount = p.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
            maxPlayers = p.getOrElse(5) { "0" }.toIntOrNull() ?: 0,
            gameMode = p.getOrElse(8) { "Survival" },
            ping = pingMs
        )
    }
}
