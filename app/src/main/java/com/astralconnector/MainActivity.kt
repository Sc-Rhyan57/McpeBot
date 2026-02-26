package com.astralconnector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.astralconnector.auth.MicrosoftLoginActivity
import com.astralconnector.ui.*
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val vm: ProxyViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onXboxLoginResult(result.resultCode, result.data)
    }

    companion object {
        const val PREF_CRASH = "crash_prefs"
        const val KEY_CRASH_TRACE = "crash_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCrashHandler()
        val crashTrace = getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE).getString(KEY_CRASH_TRACE, null)
        if (crashTrace != null) {
            getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE).edit().remove(KEY_CRASH_TRACE).apply()
            setContent { AppTheme { CrashScreen(trace = crashTrace) } }
            return
        }
        setContent {
            AppTheme {
                AstralApp(vm = vm, onLaunchLogin = {
                    loginLauncher.launch(MicrosoftLoginActivity.intent(this))
                })
            }
        }
    }

    private fun setupCrashHandler() {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val log = "Astral Connector - Crash Report\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\n\n$sw"
                getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE).edit().putString(KEY_CRASH_TRACE, log).commit()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (_: Exception) { def?.uncaughtException(t, e) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AC.Primary,
            background = AC.Bg,
            surface = AC.Surface,
        ),
        content = content
    )
}

@Composable
fun AstralApp(vm: ProxyViewModel, onLaunchLogin: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var footerClicks by remember { mutableIntStateOf(0) }
    val isRunning by vm.isRunning.collectAsState()
    val players by vm.players.collectAsState()
    val account by vm.xboxAccount.collectAsState()

    val tabs = listOf(
        NavTab("Home", Icons.Outlined.Home),
        NavTab("Players", Icons.Outlined.People),
        NavTab("Chat", Icons.AutoMirrored.Outlined.Chat),
        NavTab("Stats", Icons.Outlined.BarChart),
        NavTab("Account", Icons.Outlined.AccountCircle),
        NavTab("Logs", Icons.Outlined.Terminal),
    )

    LaunchedEffect(footerClicks) { if (footerClicks >= 5) { selectedTab = 5; footerClicks = 0 } }

    Scaffold(
        containerColor = AC.Bg,
        bottomBar = {
            Column {
                AstralBottomBar(
                    tabs = tabs,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    players = players.size,
                    isRunning = isRunning,
                    hasAccount = account != null
                )
                Footer(clicks = footerClicks, onClick = { footerClicks++ })
                GitHubButton()
                Spacer(Modifier.height(8.dp))
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(vm)
                1 -> PlayersScreen(vm)
                2 -> ChatScreen(vm)
                3 -> StatsScreen(vm)
                4 -> AccountScreen(vm, onLaunchLogin = onLaunchLogin)
                5 -> LogsScreen(vm)
            }
        }
    }
}

@Composable
fun Footer(clicks: Int, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "rgb")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "h")
    val c = Color.hsv(hue, 0.75f, 1f)
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.clickable { onClick() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("By Rhyan57", fontSize = 12.sp, color = c, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.size(13.dp))
        }
        if (clicks in 1..4) Text("${5 - clicks}x more to open logs", fontSize = 10.sp, color = AC.Muted.copy(0.5f))
    }
}

@Composable
fun GitHubButton() {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sc-Rhyan57/McpeBot"))) },
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AC.Muted),
        border = BorderStroke(1.dp, AC.Border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Outlined.Code, null, modifier = Modifier.size(16.dp), tint = AC.Muted)
        Spacer(Modifier.width(8.dp))
        Text("Sc-Rhyan57/McpeBot", fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
fun AstralBottomBar(
    tabs: List<NavTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    players: Int,
    isRunning: Boolean,
    hasAccount: Boolean
) {
    NavigationBar(containerColor = AC.Surface, tonalElevation = 0.dp) {
        tabs.forEachIndexed { idx, tab ->
            NavigationBarItem(
                selected = selected == idx,
                onClick = { onSelect(idx) },
                icon = {
                    BadgedBox(badge = {
                        if (idx == 1 && players > 0) Badge(containerColor = AC.Success) { Text("$players", fontSize = 8.sp) }
                        if (idx == 0 && isRunning) Badge(containerColor = AC.Success)
                        if (idx == 4 && hasAccount) Badge(containerColor = AC.Success)
                    }) {
                        Icon(tab.icon, tab.label, modifier = Modifier.size(22.dp))
                    }
                },
                label = { Text(tab.label, fontSize = 9.sp, fontWeight = if (selected == idx) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AC.Primary, selectedTextColor = AC.Primary,
                    unselectedIconColor = AC.Muted, unselectedTextColor = AC.Muted,
                    indicatorColor = AC.Primary.copy(0.15f),
                ),
            )
        }
    }
}

@Composable
fun CrashScreen(trace: String) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(AC.Bg)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null, tint = AC.Error, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("App Crashed", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = AC.White)
            }
            TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) {
                Text("Close", color = AC.Error, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("An unexpected error occurred.", style = MaterialTheme.typography.bodyMedium, color = AC.Muted, modifier = Modifier.padding(bottom = 12.dp))
            Button(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Crash Log", trace))
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AC.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Log", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Card(modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = AC.Error.copy(0.1f)), shape = RoundedCornerShape(12.dp)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(trace, modifier = Modifier.padding(14.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AC.Error.copy(0.9f), lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

data class NavTab(val label: String, val icon: ImageVector)
