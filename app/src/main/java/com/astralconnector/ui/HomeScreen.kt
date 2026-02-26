package com.astralconnector.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.astralconnector.ProxyViewModel
import com.astralconnector.model.*

@Composable
fun HomeScreen(vm: ProxyViewModel) {
    val config by vm.config.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val serverInfo by vm.serverInfo.collectAsState()
    val networkInfo by vm.networkInfo.collectAsState()
    val savedServers by vm.savedServers.collectAsState()
    val proxyState by vm.proxyState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showServersDialog by remember { mutableStateOf(false) }

    if (showSaveDialog) SaveServerDialog(
        onSave = { vm.saveServer(it); showSaveDialog = false },
        onDismiss = { showSaveDialog = false }
    )
    if (showServersDialog) SavedServersDialog(
        servers = savedServers,
        onLoad = { vm.loadServer(it); showServersDialog = false },
        onDelete = { vm.deleteServer(it) },
        onDismiss = { showServersDialog = false }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AC.GradTop, AC.GradBot))),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Astral Connector", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = AC.White)
                    Text("Bedrock LAN Proxy", fontSize = 12.sp, color = AC.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showServersDialog = true }, modifier = Modifier.size(38.dp).background(AC.CardAlt, RoundedCornerShape(10.dp))) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = AC.Accent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showSaveDialog = true }, modifier = Modifier.size(38.dp).background(AC.CardAlt, RoundedCornerShape(10.dp))) {
                        Icon(Icons.Outlined.Save, null, tint = AC.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        item { ServerInfoCard(serverInfo, config, isRunning, proxyState, onPing = { vm.pingServer() }) }

        item {
            AstralCard("Target Server", Icons.Outlined.Dns) {
                AstralTextField("Host / IP", config.host, enabled = !isRunning) { vm.updateConfig(config.copy(host = it)) }
                AstralTextField("Port", config.port.toString(), KeyboardType.Number, !isRunning) {
                    vm.updateConfig(config.copy(port = it.toIntOrNull() ?: 19132))
                }
            }
        }

        item {
            AstralCard("LAN Settings", Icons.Outlined.Wifi) {
                AstralTextField("Server Name (LAN)", config.name, enabled = !isRunning) { vm.updateConfig(config.copy(name = it)) }
                AstralTextField("MOTD", config.motd, enabled = !isRunning) { vm.updateConfig(config.copy(motd = it)) }
                AstralTextField("LAN Port", config.lanPort.toString(), KeyboardType.Number, !isRunning) {
                    vm.updateConfig(config.copy(lanPort = it.toIntOrNull() ?: 19133))
                }
                AstralTextField("Max Players", config.maxPlayers.toString(), KeyboardType.Number) {
                    vm.updateConfig(config.copy(maxPlayers = it.toIntOrNull() ?: 20))
                }
                AstralTextField("Welcome Message", config.welcomeMessage) {
                    vm.updateConfig(config.copy(welcomeMessage = it))
                }
            }
        }

        item { NetworkInfoCard(networkInfo, config) }

        item {
            Button(
                onClick = { if (isRunning) vm.stop() else vm.start() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) AC.Error else AC.Primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(if (isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (isRunning) "Stop Proxy" else "Start Proxy", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ServerInfoCard(
    info: ServerInfo?,
    config: ServerConfig,
    isRunning: Boolean,
    proxyState: ProxyState,
    onPing: () -> Unit
) {
    AstralCard("Server Info", Icons.Outlined.Info) {
        if (isRunning) {
            val stateColor = when (proxyState) {
                ProxyState.RUNNING -> AC.Success
                ProxyState.CONNECTING -> AC.Warning
                ProxyState.ERROR -> AC.Error
                else -> AC.Muted
            }
            val stateLabel = when (proxyState) {
                ProxyState.RUNNING -> "RUNNING"
                ProxyState.CONNECTING -> "CONNECTING"
                ProxyState.ERROR -> "ERROR"
                else -> "IDLE"
            }
            Row(
                Modifier.fillMaxWidth().background(stateColor.copy(0.08f), RoundedCornerShape(8.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(8.dp).background(stateColor, CircleShape))
                Text("Proxy $stateLabel", color = stateColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (info != null) {
            Spacer(Modifier.height(4.dp))
            InfoRow("MOTD", info.motd.replace(Regex("§[0-9a-fklmnorA-FKLMNOR]"), "").ifEmpty { "—" })
            InfoRow("Players", "${info.playerCount} / ${info.maxPlayers}")
            InfoRow("Version", info.version.ifEmpty { "—" })
            InfoRow("Protocol", "${info.protocol}")
            InfoRow("Game Mode", info.gameMode.ifEmpty { "—" })
            InfoRow("Edition", info.edition.ifEmpty { "—" })
            InfoRow("Ping", if (info.ping >= 0) "${info.ping}ms" else "—")
        } else {
            Text(
                if (config.host.isBlank()) "Configure the server host above" else "Tap Ping to check the server",
                color = AC.Muted, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onPing,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, AC.Border),
            shape = RoundedCornerShape(10.dp),
            enabled = config.host.isNotBlank()
        ) {
            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp), tint = AC.Accent)
            Spacer(Modifier.width(6.dp))
            Text("Ping Server", color = AC.Accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NetworkInfoCard(info: NetworkInfo, config: ServerConfig) {
    AstralCard("Network Info", Icons.Outlined.NetworkWifi) {
        InfoRow("IPv4", info.ipv4.ifEmpty { "Loading..." })
        InfoRow("IPv6", info.ipv6.ifEmpty { "Loading..." })
        InfoRow("Wi-Fi SSID", info.ssid.ifEmpty { "N/A" })
        InfoRow("External IP", info.externalIp.ifEmpty { "Loading..." })
        InfoRow("LAN Port", "${config.lanPort}")
        Spacer(Modifier.height(4.dp))
        Text(
            "Players on the same Wi-Fi can join via Friends tab",
            fontSize = 11.sp, color = AC.Success,
            modifier = Modifier.background(AC.Success.copy(0.08f), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()
        )
    }
}

@Composable
fun AstralCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AC.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, AC.Border, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp).background(AC.Primary.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = AC.Primary, modifier = Modifier.size(16.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AC.White)
            }
            HorizontalDivider(color = AC.Border)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = AC.Muted)
        Text(value, fontSize = 12.sp, color = AC.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AstralTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    onCommit: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = AC.Muted, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onCommit(text) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AC.Primary, unfocusedBorderColor = AC.Border,
                focusedTextColor = AC.White, unfocusedTextColor = AC.White,
                disabledBorderColor = AC.Border.copy(0.5f), disabledTextColor = AC.Muted,
                cursorColor = AC.Primary, focusedContainerColor = AC.CardAlt,
                unfocusedContainerColor = AC.CardAlt, disabledContainerColor = AC.CardAlt.copy(0.5f),
            ),
            shape = RoundedCornerShape(10.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

@Composable
private fun SaveServerDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = AC.Card,
        title = { Text("Save Server", color = AC.White, fontWeight = FontWeight.Bold) },
        text = { AstralTextField("Name", name) { name = it } },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = AC.Primary),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("Cancel", color = AC.Muted) } }
    )
}

@Composable
private fun SavedServersDialog(servers: List<SavedServer>, onLoad: (SavedServer) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = AC.Card,
        title = { Text("Saved Servers", color = AC.White, fontWeight = FontWeight.Bold) },
        text = {
            if (servers.isEmpty()) Text("No saved servers.", color = AC.Muted, fontSize = 13.sp)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers) { s ->
                    Row(
                        Modifier.fillMaxWidth().background(AC.CardAlt, RoundedCornerShape(8.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(s.name, color = AC.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${s.host}:${s.port}", color = AC.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row {
                            IconButton(onClick = { onLoad(s) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Download, null, tint = AC.Primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDelete(s.name) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Delete, null, tint = AC.Error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = AC.Muted) } }
    )
}
