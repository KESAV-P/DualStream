package com.dualstream.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.accompanist.permissions.PermissionStatus
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

    val prefs = remember(context) { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val hasRequested = remember { mutableStateOf(prefs.getBoolean("has_requested_permissions", false)) }

    val isPermanentlyDenied = remember(permissionState.permissions, hasRequested.value) {
        hasRequested.value && permissionState.permissions.any { permission ->
            val status = permission.status
            status is PermissionStatus.Denied && !status.shouldShowRationale
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            prefs.edit().putBoolean("has_requested_permissions", true).apply()
            hasRequested.value = true
        }
    }

    if (showRationale.value) {
        PermissionRationaleDialog(
            onDismiss = { showRationale.value = false },
            onConfirm = {
                showRationale.value = false
                if (isPermanentlyDenied) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    prefs.edit().putBoolean("has_requested_permissions", true).apply()
                    hasRequested.value = true
                    permissionState.launchMultiplePermissionRequest()
                }
            },
            rationaleText = if (isPermanentlyDenied) {
                "Permissions have been permanently denied. Please tap 'Open Settings' to enable Nearby Devices, Bluetooth, Notifications, and Audio recording permissions."
            } else {
                "DualStream needs Nearby Devices, Bluetooth, Notifications, and Audio recording permissions to sync and stream audio."
            },
            confirmButtonText = if (isPermanentlyDenied) "Open Settings" else "Grant Permissions"
        )
    }

    // Subtle breathing animation on the logo icon
    val infiniteTransition = rememberInfiniteTransition(label = "LogoBreath")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )
    val logoAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iOSBlack)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // ── App Identity ──────────────────────────────────────────────────────
        Box(contentAlignment = Alignment.Center) {
            // Glow halo behind the icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(logoScale)
                    .background(
                        iOSBlue.copy(alpha = logoAlpha * 0.18f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(iOSGrayBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎧", fontSize = 32.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DualStream",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = iOSWhite,
            letterSpacing = 0.37.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Sync your earbuds. Split your world.",
            fontSize = 15.sp,
            color = iOSSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Mode Selection (iOS-style grouped list) ───────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(iOSGrayBg, RoundedCornerShape(14.dp))
        ) {
            ModeRow(
                emoji = "🎧",
                iconBg = iOSPurple,
                title = "Receiver",
                subtitle = "Mix and play both streams",
                tag = "Phone A",
                isFirst = true,
                isLast = false,
                onClick = {
                    if (permissionState.allPermissionsGranted) onNavigateToReceiver()
                    else showRationale.value = true
                }
            )

            // Thin divider (inset, iOS style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp)
                    .height(0.5.dp)
                    .background(iOSSeparator)
            )

            ModeRow(
                emoji = "📡",
                iconBg = iOSBlue,
                title = "Sender",
                subtitle = "Stream Phone B's audio",
                tag = "Phone B",
                isFirst = false,
                isLast = true,
                onClick = {
                    if (permissionState.allPermissionsGranted) onNavigateToSender()
                    else showRationale.value = true
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer footnote
        Text(
            text = "Both phones must be on the same Wi-Fi network",
            fontSize = 13.sp,
            color = iOSTertiary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun ModeRow(
    emoji: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    tag: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val topCorner = if (isFirst) 14.dp else 0.dp
    val bottomCorner = if (isLast) 14.dp else 0.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = topCorner, topEnd = topCorner,
                    bottomStart = bottomCorner, bottomEnd = bottomCorner
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored icon badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 17.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Labels
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = iOSWhite
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = iOSSecondary
            )
        }

        // Tag chip + chevron
        Box(
            modifier = Modifier
                .background(iOSLightGrayBg, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = tag,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = iOSSecondary
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = iOSTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}
