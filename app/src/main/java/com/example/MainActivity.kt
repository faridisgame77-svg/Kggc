package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.player.VideoPlayer
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppMode
import com.example.viewmodel.PlaybackViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: PlaybackViewModel = viewModel()
                val currentMode by viewModel.currentMode.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentMode) {
                            AppMode.SELECT_MODE -> ModeSelectionScreen(
                                onSelectMode = { mode -> viewModel.setAppMode(mode) }
                            )
                            AppMode.TV_RECEIVER -> TvReceiverScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.setAppMode(AppMode.SELECT_MODE) }
                            )
                            AppMode.MOBILE_CONTROLLER -> MobileControllerScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.setAppMode(AppMode.SELECT_MODE) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onSelectMode: (AppMode) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Slick dark background
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "EchoPlayer Sync",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF6366F1), // Electric Indigo
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Universal Lokal Yayım Qoşulma Sistemi",
                fontSize = 16.sp,
                color = Color(0xFF94A3B8), // Cool Grey
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // TV Mode Card Choice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .clickable { onSelectMode(AppMode.TV_RECEIVER) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📺 TELEVİZOR REJİMİ (TV Receiver)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bu cihazı televizor qəbuledicisi kimi istifadə edin. Ekranın ortasında yerləşən IP server ünvanı vasitəsilə telefondan göndərilən medianı dərhal oxudacaq.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Phone Mode Card Choice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .clickable { onSelectMode(AppMode.MOBILE_CONTROLLER) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📱 MOBİL REJİM (Phone Controller)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Televizorda görünən server IP ünvanını yazın, HLS/MP4/MKV/TS yayım linkini yerləşdirin və tək toxunuşla televizor ekranına ötürün.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Dəstəklənən formatlar: Transport Stream (TS), M3U8, MPD (DASH), MP4, MKV",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TvReceiverScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val ipAddress by viewModel.receiverIp.collectAsState()
    val port by viewModel.receiverPort.collectAsState()
    val isRunning by viewModel.receiverIsRunning.collectAsState()
    val activePlayingUrl by viewModel.activePlayingUrl.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // If there is an active video being casted, render the fullscreen VideoPlayer directly!
    if (activePlayingUrl != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoPlayer(
                playUrl = activePlayingUrl!!,
                commands = viewModel.playbackCommands,
                onPlaybackEnded = {
                    viewModel.setReceiverActiveUrl(null)
                    errorMessage = null
                },
                onError = { err ->
                    errorMessage = err
                    viewModel.setReceiverActiveUrl(null)
                }
            )

            // Small overlays for TV Remote control indicators on screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Yayım Aktivdir",
                        color = Color.Green,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Button(
                    onClick = {
                        viewModel.setReceiverActiveUrl(null)
                        errorMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text("Dayandır (Exit TV Player)", fontSize = 12.sp)
                }
            }
        }
    } else {
        // TV Standby Info Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Return button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Error message banner if previous play errored out
                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = "🖥️ ECHO PLAYER TV QƏBULEDİCİ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF818CF8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Huge local server host card
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, Color(0xFF4F46E5))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "LOKAL SERVER ÜNVANI",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "http://$ipAddress:$port",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "İstifadə Qaydası:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "1. Telefonu və bu televizoru eyni Wi-Fi şəbəkəsinə qoşun.",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Telefonda 'EchoPlayer Sync' tətbiqini açıb '📱 MOBİL REJİM' seçin.",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3. Yuxarıdakı IP ünvanını telefona daxil edin və istədiyiniz yayımı bura göndərin.",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(1.5f))

                CircularProgressIndicator(
                    color = Color(0xFF6366F1),
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Telefondan qoşulma gözlənilir...",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun MobileControllerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val targetIp by viewModel.targetIp.collectAsState()
    val targetPort by viewModel.targetPort.collectAsState()
    val inputUrl by viewModel.inputUrl.collectAsState()
    val logs by viewModel.connectionLogs.collectAsState()
    val history by viewModel.historyUrls.collectAsState()

    var manualIp by remember(targetIp) { mutableStateOf(targetIp) }
    var manualPort by remember(targetPort) { mutableStateOf(targetPort.toString()) }
    var manualUrl by remember(inputUrl) { mutableStateOf(inputUrl) }

    var selectedHistoryUrl by remember { mutableStateOf("") }
    var currentVolumeSlider by remember { mutableFloatStateOf(0.7f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1329))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color.White
                    )
                }
                Text(
                    text = "📱 ECHO CONTROLLER MANAGER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Scrollable Settings Layer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // TV Connection Credentials Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B264F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "1. Televizor Lokal Server Ünvanı",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = {
                                    manualIp = it
                                    viewModel.updateTargetIp(it)
                                },
                                label = { Text("TV IP (Nüm: 192.168.1.50)") },
                                textStyle = LocalTextStyle.current.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedLabelColor = Color(0xFF38BDF8),
                                    unfocusedLabelColor = Color(0xFF94A3B8)
                                ),
                                modifier = Modifier.weight(0.7f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = manualPort,
                                onValueChange = {
                                    manualPort = it
                                    val checked = it.toIntOrNull()
                                    if (checked != null) viewModel.updateTargetPort(checked)
                                },
                                label = { Text("Port") },
                                textStyle = LocalTextStyle.current.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF475569)
                                ),
                                modifier = Modifier.weight(0.3f),
                                singleLine = true
                            )
                        }
                    }
                }

                // Streaming Media Input Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "2. Yayım Linkini Yerləşdirin (Url source)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = manualUrl,
                            onValueChange = {
                                manualUrl = it
                                viewModel.updateInputUrl(it)
                            },
                            label = { Text("Media (M3U8 / MPD / MP4 / TS / MKV)") },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF818CF8),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedLabelColor = Color(0xFF818CF8),
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.castPlay(manualUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📺 Televisionda Oxut (Cast Play)", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Remote Command Control Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF374151))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "3. Sinxron İdarəetmə Paneli (Remote Buttons)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.castResume() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Davam", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.castPause() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Pauza", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.castStop() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Stop", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Volume Bar Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Səs (Vol):",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.width(60.dp)
                            )

                            Slider(
                                value = currentVolumeSlider,
                                onValueChange = {
                                    currentVolumeSlider = it
                                    viewModel.castVolume(it)
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF38BDF8)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "${(currentVolumeSlider * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Public Live Testing Demo streams
                Text(
                    text = "Tez Test Üçün Hazır Linklər (Public Demo Streams):",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                viewModel.demoStreams.forEach { (name, url) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable {
                                manualUrl = url
                                viewModel.updateInputUrl(url)
                                viewModel.addLog("Demo seçildi: $name")
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = url, color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1)
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Yüklə",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Cast History
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Son Göndərilənlər (History):",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Təmizlə", color = Color.Red, fontSize = 12.sp)
                        }
                    }

                    history.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable {
                                    manualUrl = item
                                    viewModel.updateInputUrl(item)
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = item,
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                maxLines = 2,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Real-time Logging Terminal Block
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                color = Color(0xFF020617),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "CONSOLE LOGS (Sinxronizasiya jurnalı)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = false
                    ) {
                        items(logs) { logMsg ->
                            Text(
                                text = logMsg,
                                fontFamily = FontFamily.Monospace,
                                color = if (logMsg.contains("Failed") || logMsg.contains("Error")) Color(0xFFEF4444) else if (logMsg.contains("Success")) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
