package com.astralconnector.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import com.astralconnector.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getNetworkInfo(context: Context): NetworkInfo {
        val ipv4 = getIPv4()
        val ipv6 = getIPv6()
        val ssid = getSSID(context)
        return NetworkInfo(
            ipv4 = ipv4,
            ipv6 = ipv6,
            ssid = ssid,
        )
    }

    private fun getIPv4(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress ?: "N/A"
        } catch (_: Exception) { "N/A" }
    }

    private fun getIPv6(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet6Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { it.hostAddress?.substringBefore("%") ?: "" }
                .firstOrNull { it.isNotEmpty() }
                ?: run {
                    NetworkInterface.getNetworkInterfaces().toList()
                        .flatMap { it.inetAddresses.toList() }
                        .filterIsInstance<Inet6Address>()
                        .filter { !it.isLoopbackAddress }
                        .map { it.hostAddress?.substringBefore("%") ?: "" }
                        .firstOrNull { it.isNotEmpty() } ?: "N/A"
                }
        } catch (_: Exception) { "N/A" }
    }

    private fun getSSID(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val network = cm.activeNetwork ?: return "N/A"
                val caps = cm.getNetworkCapabilities(network) ?: return "N/A"
                val wifiInfo = caps.transportInfo as? WifiInfo ?: return "N/A"
                wifiInfo.ssid?.removeSurrounding("\"") ?: "N/A"
            } else {
                @Suppress("DEPRECATION")
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                wm.connectionInfo?.ssid?.removeSurrounding("\"") ?: "N/A"
            }
        } catch (_: Exception) { "N/A" }
    }

    suspend fun getExternalIP(): String = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api4.ipify.org")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.inputStream.bufferedReader().readLine() ?: "N/A"
        } catch (_: Exception) { "N/A" }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun formatUptime(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "${h}h ${m}m ${sec}s" else if (m > 0) "${m}m ${sec}s" else "${sec}s"
    }
}
