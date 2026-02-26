package com.astralconnector.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralconnector.ProxyViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(vm: ProxyViewModel) {
    val logs by vm.logs.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AC.GradTop, AC.GradBot)))) {
        Row(
            Modifier.fillMaxWidth().background(AC.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Terminal, null, tint = AC.Primary, modifier = Modifier.size(20.dp))
                Text("Logs", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AC.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${logs.size}", fontSize = 11.sp, color = AC.Muted)
                if (logs.isNotEmpty()) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = AC.Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Terminal, null, tint = AC.Muted, modifier = Modifier.size(48.dp))
                    Text("Nenhum log", color = AC.Muted, fontSize = 14.sp)
                    Text("Inicie o proxy para ver os logs", color = AC.Muted.copy(0.6f), fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(logs, key = { i, _ -> i }) { _, log ->
                    val color: Color = when {
                        log.contains("error", true) || log.contains("falhou", true) || log.contains("failed", true) -> AC.Error
                        log.contains("warn", true) -> AC.Warning
                        log.contains("ok", true) || log.contains("sucesso", true) || log.contains("conectado", true) || log.contains("successful", true) -> AC.Success
                        log.contains("desconectado", true) || log.contains("parado", true) || log.contains("stopped", true) -> AC.Error.copy(0.75f)
                        log.contains("ping", true) || log.contains("servidor", true) -> AC.Cyan
                        log.contains("xbox", true) || log.contains("login", true) -> AC.Gold
                        else -> AC.Muted
                    }
                    Text(
                        log,
                        color = color,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { clipboard.setText(AnnotatedString(log)) })
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
