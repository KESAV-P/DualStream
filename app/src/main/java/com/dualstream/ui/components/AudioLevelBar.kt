package com.dualstream.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dualstream.ui.theme.ErrorRed
import com.dualstream.ui.theme.GreyText
import com.dualstream.ui.theme.SuccessGreen
import com.dualstream.ui.theme.Typography
import com.dualstream.ui.theme.WarningAmber

@Composable
fun AudioLevelBar(
    level: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    // Smooth transition over 100ms
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100),
        label = "AudioLevel"
    )

    Column(modifier = modifier) {
        Text(
            text = label,
            style = Typography.bodyMedium,
            color = GreyText
        )
        Spacer(modifier = Modifier.height(6.dp))
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            val width = size.width
            val height = size.height

            // Background track
            drawRect(
                color = Color(0xFF1E2436),
                size = Size(width, height)
            )

            // Active bar width
            val activeWidth = width * animatedLevel

            // Gradient brush for the level indicator
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(
                    SuccessGreen,
                    WarningAmber,
                    ErrorRed
                ),
                startX = 0f,
                endX = width
            )

            // Draw active level
            if (activeWidth > 0f) {
                drawRect(
                    brush = gradientBrush,
                    size = Size(activeWidth, height)
                )
            }
        }
    }
}
