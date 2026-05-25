package com.dualstream.ui.screens

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.dualstream.model.ConnectionState
import com.dualstream.ui.components.AudioLevelBar
import com.dualstream.ui.components.ConnectionStatusCard
import com.dualstream.ui.theme.*
import com.dualstream.viewmodel.ReceiverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReceiverViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val audioStats by viewModel.audioStats.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var useWebView by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.playLocalFile(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone A — Receiver Mode", style = Typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurfaceColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Connection Status
            ConnectionStatusCard(state = connectionState)

            // 2. Dual Audio Level Visualizer (Side-by-Side)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Earbud Output Channels",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AudioLevelBar(
                            level = audioStats.leftChannelLevel,
                            label = "← Phone A (Left Earbud)",
                            modifier = Modifier.weight(1f)
                        )
                        AudioLevelBar(
                            level = audioStats.rightChannelLevel,
                            label = "Phone B (Right Earbud) →",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Local Audio Source Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Audio Source (Phone A)",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use WebView Player", style = Typography.bodyMedium)
                        Switch(
                            checked = useWebView,
                            onCheckedChange = { 
                                useWebView = it
                                if (!it) viewModel.stopLocalPlayback()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (useWebView) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    webViewClient = WebViewClient()
                                    loadUrl("https://music.youtube.com")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch("audio/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Select Audio File", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            if (isPlaying) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Playing local audio file...", style = Typography.bodyMedium, color = SuccessGreen)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    IconButton(onClick = { viewModel.stopLocalPlayback() }) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = ErrorRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startReceiver() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("START RECEIVER", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                val isConnected = connectionState is ConnectionState.Connected
                Button(
                    onClick = { viewModel.sendStartStreamCommand() },
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurpleAccent,
                        disabledContainerColor = PurpleAccent.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("STREAM REMOTE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.stopReceiver() },
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // 5. Telemetry Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live Telemetry", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                    Divider(color = SurfaceColor, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Latency", style = Typography.bodyMedium)
                        Text("${audioStats.latencyMs} ms", style = Typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Jitter Buffer Health", style = Typography.bodyMedium)
                            Text("${audioStats.bufferHealth}%", style = Typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = audioStats.bufferHealth / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = ElectricBlue,
                            trackColor = SurfaceColor
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Packets Received", style = Typography.bodyMedium)
                        Text("${audioStats.packetsReceived}", style = Typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Packets Dropped", style = Typography.bodyMedium)
                        Text("${audioStats.packetsDropped}", style = Typography.labelLarge, color = if (audioStats.packetsDropped > 0) ErrorRed else SuccessGreen)
                    }
                }
            }
        }
    }
}
