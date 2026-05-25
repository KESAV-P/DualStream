package com.dualstream.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dualstream.model.ConnectionState
import com.dualstream.ui.theme.CardBackground
import com.dualstream.ui.theme.ElectricBlue
import com.dualstream.ui.theme.ErrorRed
import com.dualstream.ui.theme.GreyText
import com.dualstream.ui.theme.LightGrey
import com.dualstream.ui.theme.OnSurfaceColor
import com.dualstream.ui.theme.SuccessGreen
import com.dualstream.ui.theme.Typography
import com.dualstream.ui.theme.WarningAmber

@Composable
fun ConnectionStatusCard(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (state) {
        is ConnectionState.Connected -> Color(0xFF0F261D) // subtle green tint
        is ConnectionState.Error -> Color(0xFF2C1418) // subtle red tint
        is ConnectionState.Discovering, is ConnectionState.Connecting -> Color(0xFF0F202E) // subtle blue tint
        else -> CardBackground
    }

    val statusColor = when (state) {
        is ConnectionState.Connected -> SuccessGreen
        is ConnectionState.Error -> ErrorRed
        is ConnectionState.Discovering -> ElectricBlue
        is ConnectionState.Connecting -> WarningAmber
        else -> GreyText
    }

    val statusText = when (state) {
        is ConnectionState.Idle -> "Idle — Ready to connect"
        is ConnectionState.Discovering -> "Discovering — Looking for peers..."
        is ConnectionState.Connecting -> "Connecting to ${state.deviceName}..."
        is ConnectionState.Connected -> "Connected to ${state.deviceName} ✓"
        is ConnectionState.Error -> "Error: ${state.message}"
        is ConnectionState.Disconnected -> "Disconnected. Reconnecting..."
    }

    val isPulsing = state is ConnectionState.Discovering || state is ConnectionState.Connecting
    
    val infiniteTransition = rememberInfiniteTransition(label = "StatusDotPulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .alpha(if (isPulsing) alphaAnim else 1f)
                    .background(statusColor, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "Connection Status",
                    style = Typography.labelMedium,
                    color = GreyText
                )
                Text(
                    text = statusText,
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceColor
                )
            }
        }
    }
}
