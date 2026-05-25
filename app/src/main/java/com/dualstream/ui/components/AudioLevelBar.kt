package com.dualstream.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualstream.ui.theme.*

@Composable
fun AudioLevelBar(
    level: Float,
    label: String,
    modifier: Modifier = Modifier,
    activeColor: Color = iOSBlue,
    showPercentage: Boolean = true
) {
    // Spring animation for a natural, elastic feel
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "AudioLevel"
    )

    Column(modifier = modifier) {
        // Header row: label on left, percentage on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = iOSSecondary
            )
            if (showPercentage) {
                Text(
                    text = "${(animatedLevel * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (animatedLevel > 0.8f) iOSRed
                            else if (animatedLevel > 0.5f) iOSOrange
                            else activeColor
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Thin pill-shaped level bar (iOS style — 5dp height)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
        ) {
            val width = size.width
            val height = size.height
            val cornerR = CornerRadius(height / 2, height / 2)

            // Track background
            drawRoundRect(
                color = iOSLightGrayBg,
                size = Size(width, height),
                cornerRadius = cornerR
            )

            // Active fill — color shifts green→orange→red near clipping
            val fillColor = when {
                animatedLevel > 0.85f -> iOSRed
                animatedLevel > 0.55f -> iOSOrange
                else                  -> activeColor
            }

            val activeWidth = (width * animatedLevel).coerceAtLeast(0f)
            if (activeWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(fillColor.copy(alpha = 0.7f), fillColor),
                        startX = 0f,
                        endX = activeWidth
                    ),
                    size = Size(activeWidth, height),
                    cornerRadius = cornerR
                )
            }
        }
    }
}
