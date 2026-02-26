package com.astralconnector.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.astralconnector.ProxyViewModel
import com.astralconnector.model.ProxyState
import com.astralconnector.proxy.NetworkUtils

@Composable
fun StatsScreen(vm: ProxyViewModel) {
    val stats by vm.proxyStats.collectAsState()
    val state by vm.proxyState.collectAsState()
    val players by vm.players.collectAsState()
    val config by vm.config.collectAsState()
    val networkInfo by vm.networkInfo.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val serverInfo by vm.serverInfo.collectAsState()

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(AC.GradTop, AC.GradBot)))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Statistics", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = AC.White)

        Card(
            colors = CardDefaults.cardColors(containerColor = AC.Card),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, AC.Border, RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(12.dp).background(stateColor(state, isRunning), CircleShape))
                        Column {
                            Text(
                                if (config.host.isNotBlank()) "${config.host}:${config.port}" else "No server configured",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AC.White
                            )
                            Text("→ LAN :${config.lanPort}", fontSize = 11.sp, color = AC.Muted, fontFamily = FontFamily.Monospace)
                        }
                    }
                    StateChip(state, isRunning)
                }
                HorizontalDivider(color = AC.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat("Players", "${players.size}", AC.Primary)
                    MiniStat("Uptime", NetworkUtils.formatUptime(stats.uptime), AC.Cyan)
                    MiniStat("Sessions", "${stats.totalConnections}", AC.Gold)
                }
            }
        }

        if (serverInfo != null) {
            AstralCard("Remote Server Info", Icons.Outlined.Dns) {
                val info = serverInfo!!
                InfoRow("MOTD", info.motd.replace(Regex("§[0-9a-fklmnorA-FKLMNOR]"), "").ifEmpty { "—" })
                InfoRow("Players", "${info.playerCount} / ${info.maxPlayers}")
                InfoRow("Version", info.version.ifEmpty { "—" })
                InfoRow("Protocol", "${info.protocol}")
                InfoRow("Game Mode", info.gameMode.ifEmpty { "—" })
                InfoRow("Ping", if (info.ping >= 0) "${info.ping}ms" else "—")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "↓ Bytes In", NetworkUtils.formatBytes(stats.bytesIn), AC.Success)
            StatCard(Modifier.weight(1f), "↑ Bytes Out", NetworkUtils.formatBytes(stats.bytesOut), AC.Cyan)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "Pkts In", "${stats.packetsIn}", AC.Primary)
            StatCard(Modifier.weight(1f), "Pkts Out", "${stats.packetsOut}", AC.Purple)
        }

        AstralCard("Network", Icons.Outlined.Wifi) {
            InfoRow("IPv4", networkInfo.ipv4.ifEmpty { "N/A" })
            InfoRow("IPv6", networkInfo.ipv6.ifEmpty { "N/A" })
            InfoRow("External IP", networkInfo.externalIp.ifEmpty { "N/A" })
            InfoRow("Wi-Fi SSID", networkInfo.ssid.ifEmpty { "N/A" })
            InfoRow("LAN Port", "${config.lanPort}")
        }

        AstralCard("Connected Players", Icons.Outlined.People) {
            if (players.isEmpty()) {
                Text("No players connected", color = AC.Muted, fontSize = 12.sp)
            } else {
                players.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(p.username, color = AC.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(p.platform.display, color = AC.Accent, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val pingColor = when { p.ping < 80 -> AC.Success; p.ping < 150 -> AC.Warning; else -> AC.Error }
                            Text(if (p.ping > 0) "${p.ping}ms" else "—ms", color = pingColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(p.address, color = AC.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (players.last() != p) HorizontalDivider(color = AC.Border.copy(0.5f))
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StateChip(state: ProxyState, isRunning: Boolean) {
    val (color, label) = when {
        isRunning && state == ProxyState.RUNNING -> AC.Success to "RUNNING"
        state == ProxyState.CONNECTING -> AC.Warning to "CONNECTING"
        state == ProxyState.ERROR -> AC.Error to "ERROR"
        state == ProxyState.STOPPING -> AC.Warning to "STOPPING"
        else -> AC.Muted to "IDLE"
    }
    Box(
        Modifier.background(color.copy(0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = AC.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AC.Card),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, color.copy(0.2f), RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 11.sp, color = AC.Muted)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun stateColor(state: ProxyState, isRunning: Boolean) = when {
    isRunning && state == ProxyState.RUNNING -> AC.Success
    state == ProxyState.CONNECTING -> AC.Warning
    state == ProxyState.ERROR -> AC.Error
    else -> AC.Muted
}
