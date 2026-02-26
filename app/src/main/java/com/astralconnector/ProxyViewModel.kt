package com.astralconnector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astralconnector.lan.LanBroadcaster
import com.astralconnector.model.*
import com.astralconnector.proxy.BedrockProxy
import com.astralconnector.proxy.NetworkUtils
import com.astralconnector.proxy.ServerPinger
import com.astralconnector.service.ProxyService
import com.astralconnector.ui.AuthState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "astral_prefs")

class ProxyViewModel(app: Application) : AndroidViewModel(app) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val CONFIG_KEY = stringPreferencesKey("server_config")
    private val SERVERS_KEY = stringPreferencesKey("saved_servers")
    private val ACCOUNT_KEY = stringPreferencesKey("xbox_account")

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config

    private val _savedServers = MutableStateFlow<List<SavedServer>>(emptyList())
    val savedServers: StateFlow<List<SavedServer>> = _savedServers

    private val _xboxAccount = MutableStateFlow<XboxAccount?>(null)
    val xboxAccount: StateFlow<XboxAccount?> = _xboxAccount

    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState

    private var proxy: BedrockProxy? = null
    private var broadcaster: LanBroadcaster? = null

    private val _proxyState = MutableStateFlow(ProxyState.IDLE)
    val proxyState: StateFlow<ProxyState> = _proxyState

    private val _players = MutableStateFlow<List<ConnectedPlayer>>(emptyList())
    val players: StateFlow<List<ConnectedPlayer>> = _players

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat

    private val _proxyStats = MutableStateFlow(ProxyStats())
    val proxyStats: StateFlow<ProxyStats> = _proxyStats

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    companion object {
        const val NOTIF_CHANNEL_JOIN = "player_join_channel"
    }

    init {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
                .let { prefs ->
                    prefs[CONFIG_KEY]?.let { runCatching { _config.value = json.decodeFromString(it) } }
                    prefs[SERVERS_KEY]?.let { runCatching { _savedServers.value = json.decodeFromString(it) } }
                    prefs[ACCOUNT_KEY]?.let { runCatching { _xboxAccount.value = json.decodeFromString(it) } }
                }
        }
        loadNetworkInfo()
        createNotifChannel()
    }

    private fun createNotifChannel() {
        val nm = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(NOTIF_CHANNEL_JOIN, "Player Connections", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(ch)
    }

    private fun notifyPlayerJoin(event: PlayerJoinEvent) {
        try {
            val ctx = getApplication<Application>()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
            val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notif = NotificationCompat.Builder(ctx, NOTIF_CHANNEL_JOIN)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Player Connected")
                .setContentText("${event.username} [${event.platform.display}] joined")
                .setAutoCancel(true).setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH).build()
            nm.notify(event.username.hashCode(), notif)
        } catch (_: Exception) {}
    }

    fun loadNetworkInfo() {
        viewModelScope.launch {
            val info = NetworkUtils.getNetworkInfo(getApplication())
            val extIp = NetworkUtils.getExternalIP()
            _networkInfo.value = info.copy(externalIp = extIp)
        }
    }

    fun updateConfig(config: ServerConfig) {
        _config.value = config
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[CONFIG_KEY] = json.encodeToString(config)
            }
        }
    }

    fun onXboxLoginResult(resultCode: Int, data: Intent?) {
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val gamertag = data.getStringExtra("gamertag") ?: ""
            val accessToken = data.getStringExtra("accessToken") ?: ""
            val identityToken = data.getStringExtra("identityToken") ?: ""
            val userHash = data.getStringExtra("userHash") ?: ""
            val xuid = data.getStringExtra("xuid") ?: ""
            val expiresAt = data.getLongExtra("expiresAt", System.currentTimeMillis() + 86400_000L)

            if (accessToken.isEmpty() || identityToken.isEmpty()) {
                _authState.value = AuthState.ERROR
                log("Xbox login failed: empty token received")
                return
            }

            val account = XboxAccount(
                gamertag = gamertag,
                xuid = xuid,
                accessToken = accessToken,
                userHash = userHash,
                identityToken = identityToken,
                expiresAt = expiresAt
            )
            _xboxAccount.value = account
            _authState.value = AuthState.IDLE
            saveAccount(account)
            log("Xbox login OK: ${account.gamertag}")
        } else {
            _authState.value = AuthState.ERROR
            log("Xbox login canceled or failed")
        }
    }

    fun logoutXbox() {
        _xboxAccount.value = null
        _authState.value = AuthState.IDLE
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it.remove(ACCOUNT_KEY) }
        }
        log("Xbox disconnected")
    }

    private fun saveAccount(account: XboxAccount) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[ACCOUNT_KEY] = json.encodeToString(account)
            }
        }
    }

    fun start() {
        if (_isRunning.value) return
        val cfg = _config.value
        _proxyState.value = ProxyState.CONNECTING
        proxy = BedrockProxy(
            config = cfg,
            xboxAccount = _xboxAccount.value,
            onLog = ::log,
            onPlayerJoin = { event -> viewModelScope.launch { notifyPlayerJoin(event) } }
        )
        viewModelScope.launch { proxy!!.players.collect { _players.value = it } }
        viewModelScope.launch { proxy!!.chat.collect { _chat.value = it } }
        viewModelScope.launch { proxy!!.stats.collect { _proxyStats.value = it } }
        viewModelScope.launch { proxy!!.state.collect { _proxyState.value = it } }
        proxy!!.start()
        broadcaster = LanBroadcaster(cfg)
        broadcaster!!.start()
        _isRunning.value = true
        try {
            getApplication<Application>().startForegroundService(Intent(getApplication(), ProxyService::class.java))
        } catch (_: Exception) {}
        pingServer()
        loadNetworkInfo()
    }

    fun stop() {
        proxy?.stop()
        proxy = null
        broadcaster?.stop()
        broadcaster = null
        _isRunning.value = false
        _proxyState.value = ProxyState.IDLE
        _players.value = emptyList()
        try { getApplication<Application>().stopService(Intent(getApplication(), ProxyService::class.java)) } catch (_: Exception) {}
    }

    fun kickPlayer(id: String, reason: String) = proxy?.kickPlayer(id, reason)
    fun banPlayer(id: String, reason: String) = proxy?.banPlayer(id, reason)
    fun unbanPlayer(xuid: String) = proxy?.unbanPlayer(xuid)
    fun getBans() = proxy?.getBans() ?: emptyList()
    fun sendChatMessage(message: String) = proxy?.sendChatMessage(message)

    fun pingServer() {
        val cfg = _config.value
        if (cfg.host.isBlank()) return
        viewModelScope.launch {
            log("Pinging ${cfg.host}:${cfg.port}...")
            val info = ServerPinger.ping(cfg.host, cfg.port)
            _serverInfo.value = info
            if (info != null) {
                log("Server: ${info.motd} | ${info.playerCount}/${info.maxPlayers} | ${info.ping}ms | v${info.version}")
            } else {
                log("Ping failed - server offline or unreachable")
            }
        }
    }

    fun log(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val current = _logs.value.toMutableList()
        current.add("[$ts] $msg")
        if (current.size > 500) current.removeAt(0)
        _logs.value = current
    }

    fun saveServer(name: String) {
        val cfg = _config.value
        val current = _savedServers.value.toMutableList()
        current.removeIf { it.host == cfg.host && it.port == cfg.port }
        current.add(0, SavedServer(name = name, host = cfg.host, port = cfg.port))
        _savedServers.value = current
        persistServers(current)
    }

    fun loadServer(server: SavedServer) {
        updateConfig(_config.value.copy(host = server.host, port = server.port, name = server.name))
    }

    fun deleteServer(name: String) {
        val current = _savedServers.value.toMutableList()
        current.removeIf { it.name == name }
        _savedServers.value = current
        persistServers(current)
    }

    private fun persistServers(servers: List<SavedServer>) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[SERVERS_KEY] = json.encodeToString(servers)
            }
        }
    }

    override fun onCleared() { stop(); super.onCleared() }
}
