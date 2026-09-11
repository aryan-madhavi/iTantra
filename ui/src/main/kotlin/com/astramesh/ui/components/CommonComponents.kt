package com.astramesh.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astramesh.ui.i18n.appStrings
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.astramesh.ui.theme.AstraOutline
import com.astramesh.ui.theme.AstraSurface
import com.astramesh.ui.theme.AstraTextPrimary

@Composable
fun PulsingStatusDot(
    isActive: Boolean,
    activeColor: Color = AstraEmerald,
    inactiveColor: Color = AstraOutline,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .scale(if (isActive) scale else 1f)
            .clip(CircleShape)
            .background(if (isActive) activeColor else inactiveColor)
    )
}

@Composable
fun SignalStrengthIndicator(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    // 4 bars: <= -85 (1), <= -75 (2), <= -65 (3), > -65 (4)
    val bars = when {
        rssi >= -65 -> 4
        rssi >= -75 -> 3
        rssi >= -85 -> 2
        else -> 1
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val barColor = if (i <= bars) AstraCyan else AstraOutline
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraTopBar(
    title: String,
    connectedPeersCount: Int = 0,
    actions: @Composable () -> Unit = {}
) {
    val strings = appStrings()
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = AstraTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                PulsingStatusDot(isActive = connectedPeersCount > 0)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$connectedPeersCount ${strings.availableNodes}",
                    color = AstraCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AstraSurface
        )
    )
}
