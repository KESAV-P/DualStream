package com.dualstream.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dualstream.model.ConnectionState
import com.dualstream.ui.components.AudioLevelBar
import com.dualstream.ui.components.ConnectionStatusCard
import com.dualstream.viewmodel.SenderViewModel
import com.dualstream.ui.theme.*
import com.dualstream.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(
    onNavigateBack: () -> Unit,
    viewModel: SenderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val isSilenceDetected by viewModel.isSilenceDetected.collectAsState()
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

    val recordAudioGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val nearbyWifiGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val notificationsGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Receiver", style = MaterialTheme.typography.titleMedium, color = iOSWhite)
                        Text("Phone B", fontSize = 12.sp, color = iOSSecondary)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── 1. Connection Status ──────────────────────────────────────────
            ConnectionStatusCard(state = connectionState, isRemoteAudioFlowing = isRemoteAudioFlowing)

            if (isStreaming && isSilenceDetected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = iOSRed.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, iOSRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
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
                                text = "Silence Detected (Potential DRM)",
                                fontWeight = FontWeight.Bold,
                                color = iOSWhite,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Apps like Spotify, Netflix, or Apple Music block audio capture. Try playing audio in Chrome/browser instead.",
                                color = iOSSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ── 2. Central Stream Button ──────────────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))

            StreamButton(
                isStreaming = isStreaming,
                onToggle = {
                    if (isStreaming) {
                        viewModel.stopSender()
                    } else {
                        val intent = mediaProjectionManager.createScreenCaptureIntent()
                        captureLauncher.launch(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 3. Audio Level ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                IosGroupHeader(text = "AUDIO OUTPUT")
                Spacer(modifier = Modifier.height(10.dp))
                AudioLevelBar(
                    level = audioLevel,
                    label = "Captured System Audio",
                    activeColor = iOSPurple
                )
            }

            // ── 4. Stats ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
            ) {
                IosGroupHeader(
                    text = "STREAM STATUS",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                IosStatRow(
                    label = "Bitrate",
                    value = if (isStreaming) "768 kbps (Raw PCM)" else "—",
                    valueColor = if (isStreaming) iOSGreen else iOSSecondary,
                    isFirst = true,
                    isLast = false
                )

                IosStatRow(
                    label = "Active Link",
                    value = if (connectionState is ConnectionState.Connected) "Good" else "No Link",
                    valueColor = if (connectionState is ConnectionState.Connected) iOSGreen else iOSSecondary,
                    isFirst = false,
                    isLast = true
                )
            }

            if (BuildConfig.DEBUG && isStreaming) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(iOSGrayBg, RoundedCornerShape(14.dp))
                ) {
                    IosGroupHeader(text = "DEBUG OVERLAY")
                    IosStatRow(label = "Strategy", value = if (viewModel.isAlternateOutputAvailable()) "A (Native)" else "B (Gain Makeup)")
                    IosStatRow(label = "Remote Flow", value = isRemoteAudioFlowing.toString(), isLast = true)
                }
            }

            // ── 5. Permissions ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(iOSGrayBg, RoundedCornerShape(14.dp))
            ) {
                IosGroupHeader(
                    text = "PERMISSIONS",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                PermissionRow(
                    label = "Record System Audio",
                    granted = recordAudioGranted,
                    isFirst = true,
                    isLast = false
                )
                IosRowDivider()
                PermissionRow(
                    label = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                        "Nearby Wi-Fi Devices" else "Location Access",
                    granted = nearbyWifiGranted,
                    isFirst = false,
                    isLast = false
                )
                IosRowDivider()
                PermissionRow(
                    label = "Notifications",
                    granted = notificationsGranted,
                    isFirst = false,
                    isLast = true
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Large pulsing circular stream button ────────────────────────────────────────
@Composable
private fun StreamButton(
    isStreaming: Boolean,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "StreamPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val activeColor = if (isStreaming) iOSRed else iOSBlue

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        // Animated glow ring (only when streaming)
        if (isStreaming) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .background(activeColor.copy(alpha = pulseAlpha), CircleShape)
            )
        }
        // Outer ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(activeColor.copy(alpha = 0.12f), CircleShape)
        )
        // Main button
        Button(
            onClick = onToggle,
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape),
            colors = ButtonDefaults.buttonColors(containerColor = activeColor),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isStreaming) "■" else "▶",
                    fontSize = 24.sp,
                    color = iOSWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isStreaming) "Stop" else "Start",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iOSWhite
                )
            }
        }
    }
}

// ── Shared iOS-style grouped list helpers ───────────────────────────────────────

@Composable
private fun IosGroupHeader(text: String, modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
private fun IosRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(iOSSeparator)
    )
}

@Composable
private fun IosStatRow(
    label: String,
    value: String,
    valueColor: Color = iOSWhite,
    isFirst: Boolean = false,
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
    if (!isLast) IosRowDivider()
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 13.sp, color = iOSSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = iOSWhite)
    }
}

@Composable
fun PermissionRow(
    label: String,
    granted: Boolean,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp, color = iOSWhite, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (granted) "Granted" else "Denied",
            tint = if (granted) iOSGreen else iOSRed,
            modifier = Modifier.size(20.dp)
        )
    }
    if (!isLast) IosRowDivider()
}
