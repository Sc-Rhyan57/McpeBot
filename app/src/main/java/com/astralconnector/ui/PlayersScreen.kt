package com.astralconnector.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.astralconnector.ProxyViewModel
import com.astralconnector.model.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PlayersScreen(vm: ProxyViewModel) {
    val players by vm.players.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val bans = remember(isRunning, players) { vm.getBans() }
    var showBans by remember { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<ConnectedPlayer?>(null) }
    var banTarget by remember { mutableStateOf<ConnectedPlayer?>(null) }

    kickTarget?.let { p ->
        ReasonDialog("Kick ${p.username}", { vm.kickPlayer(p.id, it); kickTarget = null }) { kickTarget = null }
    }
    banTarget?.let { p ->
        ReasonDialog("Ban ${p.username}", { vm.banPlayer(p.id, it); banTarget = null }) { banTarget = null }
    }

    Column(Modifier.fillMaxSize().background(AC.Bg)) {
        Row(
            Modifier.fillMaxWidth().background(AC.Surface).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Players", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AC.White)
                Text("${players.size} conectado(s)", fontSize = 12.sp, color = AC.Muted)
            }
            OutlinedButton(
                onClick = { showBans = !showBans },
                border = BorderStroke(1.dp, if (showBans) AC.Error else AC.Border),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Outlined.Block, null, tint = if (showBans) AC.Error else AC.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Banidos (${bans.size})", color = if (showBans) AC.Error else AC.Muted, fontSize = 12.sp)
            }
        }

        if (showBans) {
            BanListSection(bans, onUnban = { vm.unbanPlayer(it) })
        } else when {
            !isRunning -> EmptyStateView(Icons.Outlined.WifiOff, "Proxy parado", "Inicie o proxy na aba Home")
            players.isEmpty() -> EmptyStateView(Icons.Outlined.People, "Nenhum jogador conectado", "Aguardando conexões...")
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(players, key = { it.id }) { player ->
                    PlayerCard(player, onKick = { kickTarget = player }, onBan = { banTarget = player })
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(icon: ImageVector, title: String, sub: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = AC.Muted, modifier = Modifier.size(48.dp))
            Text(title, color = AC.Muted, fontSize = 14.sp)
            Text(sub, color = AC.Muted.copy(0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlayerCard(player: ConnectedPlayer, onKick: () -> Unit, onBan: () -> Unit) {
    val pingColor = when { player.ping < 80 -> AC.Success; player.ping < 150 -> AC.Warning; else -> AC.Error }
    Card(
        colors = CardDefaults.cardColors(containerColor = AC.Card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, AC.Border, RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).background(AC.Primary.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(player.username.firstOrNull()?.uppercase() ?: "?", color = AC.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    Column {
                        Text(player.username, color = AC.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(player.platform.display, color = AC.Accent, fontSize = 11.sp)
                    }
                }
                Box(
                    Modifier.background(pingColor.copy(0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, pingColor.copy(0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (player.ping > 0) "${player.ping}ms" else "—ms", color = pingColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            HorizontalDivider(color = AC.Border)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(Icons.Outlined.Lan, "${player.address}:${player.port}", AC.Cyan)
                if (player.xuid.isNotEmpty()) InfoChip(Icons.Outlined.Badge, player.xuid.take(10) + "…", AC.Gold)
            }
            if (player.clientVersion.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(Icons.Outlined.PhoneAndroid, "v${player.clientVersion}", AC.Accent)
                }
            }
            val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            InfoChip(Icons.Outlined.AccessTime, "Desde ${sdf.format(Date(player.connectedAt))}", AC.Muted)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onKick, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, AC.Warning), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ExitToApp, null, tint = AC.Warning, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kick", color = AC.Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onBan, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, AC.Error), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Outlined.Block, null, tint = AC.Error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ban", color = AC.Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.background(color.copy(0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
        Text(text, fontSize = 10.sp, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BanListSection(bans: List<PlayerBan>, onUnban: (String) -> Unit) {
    if (bans.isEmpty()) { EmptyStateView(Icons.Outlined.CheckCircle, "Nenhum banido", ""); return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(bans, key = { it.xuid }) { ban ->
            Row(
                Modifier.fillMaxWidth()
                    .background(AC.Card, RoundedCornerShape(12.dp))
                    .border(1.dp, AC.Error.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ban.username, color = AC.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(ban.reason, color = AC.Error, fontSize = 11.sp)
                    if (ban.xuid.isNotEmpty())
                        Text(ban.xuid, color = AC.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = { onUnban(ban.xuid) }) {
                    Icon(Icons.Outlined.LockOpen, null, tint = AC.Success, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ReasonDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AC.Card,
        title = { Text(title, color = AC.White, fontWeight = FontWeight.Bold) },
        text = { AstralTextField("Motivo", reason) { reason = it } },
        confirmButton = {
            Button(onClick = { onConfirm(reason.ifEmpty { "Sem motivo" }) },
                colors = ButtonDefaults.buttonColors(containerColor = AC.Error),
                shape = RoundedCornerShape(10.dp)) { Text("Confirmar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Cancelar", color = AC.Muted) }
        }
    )
}
