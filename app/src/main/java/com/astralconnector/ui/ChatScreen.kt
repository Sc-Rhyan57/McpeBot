package com.astralconnector.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralconnector.ProxyViewModel
import com.astralconnector.model.ChatMessage
import com.astralconnector.model.ChatType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: ProxyViewModel) {
    val chat by vm.chat.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }

    // Filtra APENAS mensagens de chat/join/leave — logs ficam na tela de Logs
    val visible = remember(chat) {
        chat.filter { it.type == ChatType.CHAT || it.type == ChatType.JOIN || it.type == ChatType.LEAVE }
    }

    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AC.GradTop, AC.GradBot)))) {
        Row(
            Modifier.fillMaxWidth().background(AC.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = AC.Primary, modifier = Modifier.size(20.dp))
                Text("Chat", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AC.White)
            }
            Text("${visible.count { it.type == ChatType.CHAT }} mensagens", fontSize = 11.sp, color = AC.Muted)
        }

        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = AC.Muted, modifier = Modifier.size(48.dp))
                    Text("Nenhuma mensagem", color = AC.Muted, fontSize = 14.sp)
                    Text(if (isRunning) "Aguardando jogadores..." else "Inicie o proxy para ver o chat", color = AC.Muted.copy(0.6f), fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(visible, key = { i, m -> "${m.timestamp}_$i" }) { _, msg ->
                    ChatLine(msg) { clipboard.setText(AnnotatedString("${msg.sender}: ${msg.message}")) }
                }
            }
        }

        // Input de chat — só aparece quando o proxy está rodando
        if (isRunning) {
            HorizontalDivider(color = AC.Border)
            Row(
                Modifier.fillMaxWidth().background(AC.Surface).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enviar mensagem...", fontSize = 13.sp, color = AC.Muted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AC.Primary,
                        unfocusedBorderColor = AC.Border,
                        focusedTextColor = AC.White,
                        unfocusedTextColor = AC.White,
                        cursorColor = AC.Primary,
                        focusedContainerColor = AC.CardAlt,
                        unfocusedContainerColor = AC.CardAlt,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) { vm.sendChatMessage(inputText.trim()); inputText = "" }
                    })
                )
                IconButton(
                    onClick = { if (inputText.isNotBlank()) { vm.sendChatMessage(inputText.trim()); inputText = "" } },
                    modifier = Modifier.size(48.dp).background(AC.Primary, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = AC.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatLine(msg: ChatMessage, onCopy: () -> Unit) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val (bgColor, textColor, senderColor) = when (msg.type) {
        ChatType.CHAT -> Triple(androidx.compose.ui.graphics.Color.Transparent, AC.White, AC.Accent)
        ChatType.JOIN -> Triple(AC.Success.copy(0.06f), AC.Success, AC.Success)
        ChatType.LEAVE -> Triple(AC.Error.copy(0.06f), AC.Error.copy(0.85f), AC.Error)
        else -> Triple(AC.Primary.copy(0.04f), AC.Muted, AC.Muted)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .combinedClickable(onClick = {}, onLongClick = onCopy)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            sdf.format(Date(msg.timestamp)),
            fontSize = 9.sp, color = AC.Muted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            buildAnnotatedString {
                if (msg.sender.isNotBlank() && msg.type == ChatType.CHAT) {
                    withStyle(SpanStyle(color = senderColor, fontWeight = FontWeight.Bold)) {
                        append("<${msg.sender}> ")
                    }
                }
                withStyle(SpanStyle(color = textColor)) { append(msg.message) }
            },
            fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp
        )
    }
}
