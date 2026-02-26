package com.astralconnector.proxy

object RakNet {
    const val ID_UNCONNECTED_PING = 0x01
    const val ID_UNCONNECTED_PONG = 0x1C
    const val ID_OPEN_CONNECTION_REQ_1 = 0x05
    const val ID_OPEN_CONNECTION_REQ_2 = 0x07
    const val ID_CONNECTION_BANNED = 0x17

    val OFFLINE_MAGIC = byteArrayOf(
        0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00,
        0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
        0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(),
        0x12, 0x34, 0x56, 0x78
    )

    fun readLong(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return result
    }

    fun writeLong(value: Long): ByteArray {
        val buf = ByteArray(8)
        for (i in 7 downTo 0) {
            buf[i] = (value and 0xFF).toByte()
        }
        return buf
    }

    fun buildPong(pingId: Long, serverGuid: Long, motd: String): ByteArray {
        val motdBytes = motd.toByteArray(Charsets.UTF_8)
        val buf = mutableListOf<Byte>()
        buf.add(ID_UNCONNECTED_PONG.toByte())
        buf.addAll(writeLong(pingId).toList())
        buf.addAll(writeLong(serverGuid).toList())
        buf.addAll(OFFLINE_MAGIC.toList())
        buf.add(((motdBytes.size shr 8) and 0xFF).toByte())
        buf.add((motdBytes.size and 0xFF).toByte())
        buf.addAll(motdBytes.toList())
        return buf.toByteArray()
    }
}
