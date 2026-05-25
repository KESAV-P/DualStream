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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
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
import com.dualstream.ui.theme.*
import com.dualstream.viewmodel.SenderViewModel

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

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onMediaProjectionResult(result.resultCode, result.data!!)
        }
    }

    val scrollState = rememberScrollState()

    // Dynamic checks for permissions
    val recordAudioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val nearbyWifiGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val notificationsGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone B — Sender Mode", style = Typography.titleMedium) },
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Connection Status Card
            ConnectionStatusCard(state = connectionState)

            // 2. Audio Level Visualizer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AudioLevelBar(
                        level = audioLevel,
                        label = "Captured System Audio Level"
                    )
                }
            }

            // 3. Central Stream Action Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "ButtonGlow")
                val glowRadius by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 24f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "GlowAnim"
                )

                val buttonColor = if (isStreaming) ErrorRed else ElectricBlue
                val buttonText = if (isStreaming) "STOP STREAMING" else "START STREAMING"

                Button(
                    onClick = {
                        if (isStreaming) {
                            viewModel.stopSender()
                        } else {
                            val intent = mediaProjectionManager.createScreenCaptureIntent()
                            captureLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .drawBehind {
                            if (isStreaming) {
                                drawCircle(
                                    color = ErrorRed.copy(alpha = 0.35f),
                                    radius = size.minDimension / 2 + glowRadius
                                )
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = CircleShape
                ) {
                    Text(
                        text = buttonText,
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            // 4. Stats Row Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(label = "Bitrate", value = if (isStreaming) "64 kbps" else "0 kbps")
                    StatItem(label = "Active Link", value = if (connectionState is ConnectionState.Connected) "Good" else "No Link")
                }
            }

            // 5. Permissions check Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Service Permissions", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                    Divider(color = SurfaceColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    PermissionRow(label = "Record System Audio", granted = recordAudioGranted)
                    PermissionRow(
                        label = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) "Nearby WiFi Devices" else "Location access",
                        granted = nearbyWifiGranted
                    )
                    PermissionRow(label = "Notifications", granted = notificationsGranted)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = Typography.labelMedium, color = GreyText)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = OnSurfaceColor)
    }
}

@Composable
fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = Typography.bodyMedium, color = OnSurfaceColor)
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (granted) "Granted" else "Denied",
            tint = if (granted) SuccessGreen else ErrorRed,
            modifier = Modifier.size(20.dp)
        )
    }
}
