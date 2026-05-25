package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.server.PlaybackServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

enum class AppMode {
    SELECT_MODE,
    MOBILE_CONTROLLER,
    TV_RECEIVER
}

sealed interface PlaybackCommand {
    data class Play(val url: String) : PlaybackCommand
    object Pause : PlaybackCommand
    object Resume : PlaybackCommand
    object Stop : PlaybackCommand
    data class SetVolume(val level: Float) : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand
}

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("EchoPlayerPrefs", Context.MODE_PRIVATE)

    // Global Screen Mode
    private val _currentMode = MutableStateFlow(AppMode.SELECT_MODE)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    // ----------------------------------------------------
    // TV / RECEIVER MODE STATE
    // ----------------------------------------------------
    private var playbackServer: PlaybackServer? = null
    
    private val _receiverIp = MutableStateFlow("127.0.0.1")
    val receiverIp: StateFlow<String> = _receiverIp.asStateFlow()

    private val _receiverPort = MutableStateFlow(8080)
    val receiverPort: StateFlow<Int> = _receiverPort.asStateFlow()

    private val _receiverIsRunning = MutableStateFlow(false)
    val receiverIsRunning: StateFlow<Boolean> = _receiverIsRunning.asStateFlow()

    // Stream playback events received by our TV HTTP Server
    private val _playbackCommands = MutableSharedFlow<PlaybackCommand>(extraBufferCapacity = 16)
    val playbackCommands: SharedFlow<PlaybackCommand> = _playbackCommands.asSharedFlow()

    private val _activePlayingUrl = MutableStateFlow<String?>(null)
    val activePlayingUrl: StateFlow<String?> = _activePlayingUrl.asStateFlow()

    // ----------------------------------------------------
    // MOBILE / CONTROLLER MODE STATE
    // ----------------------------------------------------
    private val _targetIp = MutableStateFlow(prefs.getString("target_ip", "") ?: "")
    val targetIp: StateFlow<String> = _targetIp.asStateFlow()

    private val _targetPort = MutableStateFlow(prefs.getInt("target_port", 8080))
    val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

    private val _inputUrl = MutableStateFlow(prefs.getString("input_url", "") ?: "")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _connectionLogs = MutableStateFlow<List<String>>(listOf("Controller ready. Enter TV IP to cast."))
    val connectionLogs: StateFlow<List<String>> = _connectionLogs.asStateFlow()

    private val _historyUrls = MutableStateFlow<List<String>>(
        prefs.getStringSet("history_urls", emptySet())?.toList() ?: emptyList()
    )
    val historyUrls: StateFlow<List<String>> = _historyUrls.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    val demoStreams = listOf(
        Pair("Big Buck Bunny (HLS .m3u8)", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"),
        Pair("Sintel Movie (HLS .m3u8)", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"),
        Pair("BBC Test Card (DASH .mpd)", "https://rdmedia.bbc.co.uk/dash/ondemand/testcard/1/client_manifest-events.mpd"),
        Pair("Tears of Steel (DASH .mpd)", "https://dash.akamaized.net/akamai/test/isptest.mpd"),
        Pair("Big Buck Bunny (MP4 Video)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
        Pair("Sintel (MP4 Video)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
        Pair("Dog Run Activity (TS Stream)", "https://res.cloudinary.com/demo/video/upload/f_auto,q_auto/dog.ts")
    )

    fun setAppMode(mode: AppMode) {
        _currentMode.value = mode
        if (mode == AppMode.TV_RECEIVER) {
            startReceiverServer()
        } else {
            stopReceiverServer()
        }
    }

    // --- TV Receiver Server Logic ---
    private fun startReceiverServer() {
        if (_receiverIsRunning.value) return
        val ip = getLocalIpAddress()
        _receiverIp.value = ip

        playbackServer = PlaybackServer(_receiverPort.value, object : PlaybackServer.ServerCallback {
            override fun onPlay(url: String) {
                viewModelScope.launch {
                    _activePlayingUrl.value = url
                    _playbackCommands.emit(PlaybackCommand.Play(url))
                }
            }

            override fun onPause() {
                viewModelScope.launch {
                    _playbackCommands.emit(PlaybackCommand.Pause)
                }
            }

            override fun onResume() {
                viewModelScope.launch {
                    _playbackCommands.emit(PlaybackCommand.Resume)
                }
            }

            override fun onStop() {
                viewModelScope.launch {
                    _activePlayingUrl.value = null
                    _playbackCommands.emit(PlaybackCommand.Stop)
                }
            }

            override fun onVolume(level: Float) {
                viewModelScope.launch {
                    _playbackCommands.emit(PlaybackCommand.SetVolume(level))
                }
            }

            override fun onSeek(positionMs: Long) {
                viewModelScope.launch {
                    _playbackCommands.emit(PlaybackCommand.SeekTo(positionMs))
                }
            }
        })
        playbackServer?.start()
        _receiverIsRunning.value = true
        Log.d("PlaybackViewModel", "Receiver local host server started on http://$ip:${_receiverPort.value}")
    }

    private fun stopReceiverServer() {
        playbackServer?.stop()
        playbackServer = null
        _receiverIsRunning.value = false
        _activePlayingUrl.value = null
    }

    fun setReceiverActiveUrl(url: String?) {
        _activePlayingUrl.value = url
    }

    // --- Mobile Receiver Remote Cast Commands ---
    fun updateTargetIp(ip: String) {
        _targetIp.value = ip
        prefs.edit().putString("target_ip", ip).apply()
    }

    fun updateTargetPort(port: Int) {
        _targetPort.value = port
        prefs.edit().putInt("target_port", port).apply()
    }

    fun updateInputUrl(url: String) {
        _inputUrl.value = url
        prefs.edit().putString("input_url", url).apply()
    }

    fun addLog(log: String) {
        val currentLogs = _connectionLogs.value.toMutableList()
        currentLogs.add(0, "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $log")
        if (currentLogs.size > 50) currentLogs.removeAt(currentLogs.size - 1)
        _connectionLogs.value = currentLogs
    }

    fun saveUrlToHistory(url: String) {
        if (url.isBlank()) return
        val currentList = _historyUrls.value.toMutableList()
        if (currentList.contains(url)) {
            currentList.remove(url)
        }
        currentList.add(0, url)
        if (currentList.size > 10) currentList.removeAt(currentList.size - 1)
        _historyUrls.value = currentList
        prefs.edit().putStringSet("history_urls", currentList.toSet()).apply()
    }

    fun clearHistory() {
        _historyUrls.value = emptyList()
        prefs.edit().remove("history_urls").apply()
        addLog("Stream history cleared.")
    }

    // HTTP Cast trigger helper
    private fun sendNetworkCommand(commandPath: String, queryParams: Map<String, String> = emptyMap()) {
        val ip = _targetIp.value.trim()
        val port = _targetPort.value
        if (ip.isEmpty()) {
            addLog("Error: Setup TV IP first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val queryBuilder = StringBuilder()
            if (queryParams.isNotEmpty()) {
                queryBuilder.append("?")
                queryParams.forEach { (key, value) ->
                    queryBuilder.append("$key=${java.net.URLEncoder.encode(value, "UTF-8")}&")
                }
                if (queryBuilder.endsWith("&") || queryBuilder.endsWith("?")) {
                    queryBuilder.deleteAt(queryBuilder.length - 1)
                }
            }

            val sanitizedIp = if (!ip.startsWith("http://") && !ip.startsWith("https://")) {
                "http://$ip"
            } else {
                ip
            }

            val url = "$sanitizedIp:$port$commandPath$queryBuilder"
            addLog("Sending command: $commandPath")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        launch(Dispatchers.Main) {
                            addLog("Success: Command '$commandPath' delivered.")
                        }
                    } else {
                        val body = response.body?.string() ?: ""
                        launch(Dispatchers.Main) {
                            addLog("Failed: Server error ${response.code} ($body)")
                        }
                    }
                }
            } catch (e: IOException) {
                launch(Dispatchers.Main) {
                    addLog("Error: Could not connect to TV at $sanitizedIp:$port. Ensure Wifi is active.")
                }
            }
        }
    }

    fun castPlay(url: String) {
        if (url.isBlank()) {
            addLog("Error: Empty URL cannot be cast.")
            return
        }
        saveUrlToHistory(url)
        sendNetworkCommand("/play", mapOf("url" to url))
    }

    fun castPause() {
        sendNetworkCommand("/pause")
    }

    fun castResume() {
        sendNetworkCommand("/resume")
    }

    fun castStop() {
        sendNetworkCommand("/stop")
    }

    fun castVolume(level: Float) {
        sendNetworkCommand("/volume", mapOf("level" to level.toString()))
    }

    fun castSeek(positionMs: Long) {
        sendNetworkCommand("/seek", mapOf("position" to positionMs.toString()))
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null) return host
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("PlaybackViewModel", "Error getting IP", ex)
        }
        return "127.0.0.1"
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiverServer()
    }
}
