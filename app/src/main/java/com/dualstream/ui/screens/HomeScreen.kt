package com.dualstream.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualstream.ui.components.PermissionRationaleDialog
import com.dualstream.ui.theme.*
import com.dualstream.util.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToSender: () -> Unit,
    onNavigateToReceiver: () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = remember { PermissionUtils.getRequiredPermissions(context) }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)
    
    val showRationale = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    if (showRationale.value) {
        PermissionRationaleDialog(
            onDismiss = { showRationale.value = false },
            onConfirm = {
                showRationale.value = false
                permissionState.launchMultiplePermissionRequest()
            },
            rationaleText = "DualStream needs Nearby Devices, Notifications, and Audio recording permissions to sync and stream audio."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Headset,
                contentDescription = "Earbuds Logo",
                tint = ElectricBlue,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "DualStream",
                style = Typography.titleLarge,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = ElectricBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sync your earbuds. Split your world.",
                style = Typography.bodyMedium,
                color = LightGrey
            )
        }

        // Choice Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModeCard(
                title = "📡 Sender Mode",
                subtitle = "Stream your phone's audio to the other device",
                tag = "Phone B",
                colors = listOf(PurpleAccent.copy(alpha = 0.85f), CardBackground),
                onClick = {
                    if (permissionState.allPermissionsGranted) {
                        onNavigateToSender()
                    } else {
                        showRationale.value = true
                    }
                }
            )

            ModeCard(
                title = "🎧 Receiver Mode",
                subtitle = "Receive, mix and play both audio streams",
                tag = "Phone A — Master Device",
                colors = listOf(ElectricBlue.copy(alpha = 0.85f), CardBackground),
                onClick = {
                    if (permissionState.allPermissionsGranted) {
                        onNavigateToReceiver()
                    } else {
                        showRationale.value = true
                    }
                }
            )
        }

        // Footnote
        Text(
            text = "Both phones must be on the same Wi-Fi network",
            style = Typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
fun ModeCard(
    title: String,
    subtitle: String,
    tag: String,
    colors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = tag,
                            style = Typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Text(
                    text = subtitle,
                    style = Typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
