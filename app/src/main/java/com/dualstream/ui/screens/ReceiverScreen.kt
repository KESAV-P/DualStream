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
    val connectionState by viewModel.connectionState.collectAsState()
    val audioStats by viewModel.audioStats.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var useWebView by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.playLocalFile(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Receiver", style = MaterialTheme.typography.titleMedium, color = iOSWhite)
                        Text("Phone A — Master Device", fontSize = 12.sp, color = iOSSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = iOSBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = iOSBlack)
            )
        },
        containerColor = iOSBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── 1. Connection Status ──────────────────────────────────────────
            ConnectionStatusCard(state = connectionState)

            // ── 2. Earbud Channel Visualizer ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                ReceiverGroupHeader(text = "EARBUD OUTPUT CHANNELS")
                Spacer(modifier = Modifier.height(14.dp))

                AudioLevelBar(
                    level = audioStats.leftChannelLevel,
                    label = "Left  ◀  Phone A",
                    activeColor = iOSBlue,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                AudioLevelBar(
                    level = audioStats.rightChannelLevel,
                    label = "Right  ▶  Phone B",
                    activeColor = iOSPurple,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 3. Control Actions ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReceiverGroupHeader(text = "CONTROLS")
                Spacer(modifier = Modifier.height(2.dp))

                val isConnected = connectionState is ConnectionState.Connected

                // Start Receiver — primary full-width button
                Button(
                    onClick = { viewModel.startReceiver() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = iOSBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Start Receiver",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSWhite
                    )
                }

                // Stream Remote — secondary full-width button
                Button(
                    onClick = { viewModel.sendStartStreamCommand() },
                    enabled = isConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iOSPurple,
                        disabledContainerColor = iOSPurple.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Stream Remote Audio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) iOSWhite else iOSWhite.copy(alpha = 0.35f)
                    )
                }

                // Stop — destructive button
                Button(
                    onClick = { viewModel.stopReceiver() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = iOSRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Stop",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSWhite
                    )
                }
            }

            // ── 4. Local Audio Source ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
                    .padding(bottom = if (useWebView) 0.dp else 16.dp)
            ) {
                ReceiverGroupHeader(
                    text = "LOCAL AUDIO SOURCE",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // Switch row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("YouTube Music", fontSize = 16.sp, color = iOSWhite)
                        Text("Open web player in-app", fontSize = 13.sp, color = iOSSecondary)
                    }
                    Switch(
                        checked = useWebView,
                        onCheckedChange = {
                            useWebView = it
                            if (!it) viewModel.stopLocalPlayback()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = iOSWhite,
                            checkedTrackColor = iOSGreen,
                            uncheckedThumbColor = iOSWhite,
                            uncheckedTrackColor = iOSLightGrayBg
                        )
                    )
                }

                if (useWebView) {
                    Spacer(modifier = Modifier.height(4.dp))
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
                            .height(480.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 0.dp, topEnd = 0.dp,
                                    bottomStart = 14.dp, bottomEnd = 14.dp
                                )
                            )
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    // File picker section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = iOSBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Choose Audio File", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = iOSWhite)
                        }

                        if (isPlaying) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = iOSGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Playing…", fontSize = 14.sp, color = iOSGreen)
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = { viewModel.stopLocalPlayback() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = iOSRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. Live Telemetry ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
            ) {
                ReceiverGroupHeader(
                    text = "LIVE TELEMETRY",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                TelemetryRow(
                    label = "Latency",
                    value = "${audioStats.latencyMs} ms",
                    valueColor = when {
                        audioStats.latencyMs > 150 -> iOSRed
                        audioStats.latencyMs > 80  -> iOSOrange
                        else                       -> iOSGreen
                    },
                    isLast = false
                )

                // Buffer health with inline progress bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Jitter Buffer", fontSize = 16.sp, color = iOSWhite)
                        Text(
                            "${audioStats.bufferHealth}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                audioStats.bufferHealth < 30 -> iOSRed
                                audioStats.bufferHealth < 60 -> iOSOrange
                                else                         -> iOSGreen
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { audioStats.bufferHealth / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = iOSBlue,
                        trackColor = iOSSeparator
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(iOSSeparator))

                TelemetryRow(
                    label = "Packets Received",
                    value = "${audioStats.packetsReceived}",
                    valueColor = iOSWhite,
                    isLast = false
                )

                TelemetryRow(
                    label = "Packets Dropped",
                    value = "${audioStats.packetsDropped}",
                    valueColor = if (audioStats.packetsDropped > 0) iOSRed else iOSGreen,
                    isLast = true
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReceiverGroupHeader(
    text: String,
    modifier: Modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = iOSSecondary,
        letterSpacing = 0.5.sp,
        modifier = modifier
    )
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color = iOSWhite,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, color = iOSWhite)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(0.5.dp)
                .background(iOSSeparator)
        )
    }
}
