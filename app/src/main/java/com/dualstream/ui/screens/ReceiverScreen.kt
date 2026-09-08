package com.dualstream.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.dualstream.model.ConnectionState
import com.dualstream.ui.components.AudioLevelBar
import com.dualstream.ui.components.ConnectionStatusCard
import com.dualstream.ui.theme.*
import com.dualstream.viewmodel.ReceiverViewModel
import com.dualstream.BuildConfig

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
    val isCallActive by viewModel.isRemoteCallActive.collectAsState()
    val isSilenceDetected by viewModel.isRemoteSilenceDetected.collectAsState()
    val isRemoteAudioFlowing by viewModel.isRemoteAudioFlowing.collectAsState()

    val scrollState = rememberScrollState()

    var showFallbackDialog by remember { mutableStateOf(false) }
    var pendingProjectionData by remember { mutableStateOf<android.content.Intent?>(null) }
    var pendingProjectionCode by remember { mutableStateOf(-1) }

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (viewModel.isAlternateOutputAvailable()) {
                viewModel.onMediaProjectionResult(result.resultCode, result.data!!, useFallbackGainMakeup = false)
            } else {
                pendingProjectionCode = result.resultCode
                pendingProjectionData = result.data
                showFallbackDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sender", style = MaterialTheme.typography.titleMedium, color = iOSWhite)
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
        if (showFallbackDialog) {
            AlertDialog(
                onDismissRequest = { showFallbackDialog = false },
                title = { Text("Audio Leakage Warning") },
                text = { Text("No wired or Bluetooth headset detected on this device. If we proceed, the audio will bleed through the phone's speaker. We can use a 'Gain Make-up' fallback that minimizes the speaker volume while digitally restoring the stream volume, but it may cause slight clipping. Proceed?") },
                confirmButton = {
                    TextButton(onClick = {
                        showFallbackDialog = false
                        viewModel.onMediaProjectionResult(pendingProjectionCode, pendingProjectionData!!, useFallbackGainMakeup = true)
                    }) {
                        Text("Use Fallback")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFallbackDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Call State Banner ─────────────────────────────────────────────
            if (isCallActive) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Phone B (Receiver) is on a call — streaming paused",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isSilenceDetected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = iOSRed.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, iOSRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "DRM Warning",
                            tint = iOSRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Silence Detected on Phone A",
                                fontWeight = FontWeight.Bold,
                                color = iOSWhite,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Spotify, Apple Music, and Netflix block streaming. Ask Phone A user to play via Chrome/browser instead.",
                                color = iOSSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ── 1. Connection Status ──────────────────────────────────────────
            ConnectionStatusCard(state = connectionState, isRemoteAudioFlowing = isRemoteAudioFlowing)

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
                    onClick = {
                        val intent = mediaProjectionManager.createScreenCaptureIntent()
                        captureLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = iOSBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Start Sender",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iOSWhite
                    )
                }

                // Stream Remote — auto-stream status display
                if (isConnected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = iOSPurple.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (audioStats.packetsReceived == 0L) {
                                CircularProgressIndicator(
                                    color = iOSPurple,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Phone, // or some other icon
                                    contentDescription = "Active",
                                    tint = iOSPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (audioStats.packetsReceived > 0) "Streaming active from Phone A" else "Waiting for Phone A...",
                                color = iOSWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
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

            if (BuildConfig.DEBUG && isPlaying) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(iOSGrayBg, RoundedCornerShape(14.dp))
                ) {
                    ReceiverGroupHeader(
                        text = "DEBUG OVERLAY",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    TelemetryRow(label = "Strategy", value = if (viewModel.isAlternateOutputAvailable()) "A (Native)" else "B (Gain Makeup)")
                    TelemetryRow(label = "Remote Flow", value = isRemoteAudioFlowing.toString())
                    TelemetryRow(label = "Real Bitrate", value = "${audioStats.bitrateKbps} Kbps", isLast = true)
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

