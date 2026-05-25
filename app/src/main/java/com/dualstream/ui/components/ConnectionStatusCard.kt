package com.dualstream.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualstream.model.ConnectionState
import com.dualstream.ui.theme.*

@Composable
fun ConnectionStatusCard(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val statusColor = when (state) {
        is ConnectionState.Connected   -> iOSGreen
        is ConnectionState.Error       -> iOSRed
        is ConnectionState.Discovering -> iOSBlue
        is ConnectionState.Connecting  -> iOSOrange
        is ConnectionState.Disconnected-> iOSOrange
        else                           -> iOSSecondary
    }

    val statusLabel = when (state) {
        is ConnectionState.Idle        -> "Not Connected"
        is ConnectionState.Discovering -> "Scanning…"
        is ConnectionState.Connecting  -> "Connecting"
        is ConnectionState.Connected   -> "Connected"
        is ConnectionState.Error       -> "Error"
        is ConnectionState.Disconnected-> "Reconnecting"
    }

    val statusDetail = when (state) {
        is ConnectionState.Idle        -> "Ready to connect to a peer"
        is ConnectionState.Discovering -> "Looking for nearby devices…"
        is ConnectionState.Connecting  -> "Connecting to ${state.deviceName}"
        is ConnectionState.Connected   -> state.deviceName
        is ConnectionState.Error       -> state.message
        is ConnectionState.Disconnected-> "Lost link — attempting reconnect"
    }

    val isPulsing = state is ConnectionState.Discovering || state is ConnectionState.Connecting

    // Pulsing ring animation for active states
    val infiniteTransition = rememberInfiniteTransition(label = "StatusDotPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // iOS-style grouped-list card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(iOSGrayBg, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot with optional pulsing halo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(24.dp)
        ) {
            if (isPulsing) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .scale(pulseScale)
                        .background(statusColor.copy(alpha = pulseAlpha), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = iOSWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusDetail,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = iOSSecondary,
                maxLines = 1
            )
        }

        // State badge pill
        Box(
            modifier = Modifier
                .background(
                    statusColor.copy(alpha = 0.15f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = statusLabel.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                letterSpacing = 0.5.sp
            )
        }
    }
}
