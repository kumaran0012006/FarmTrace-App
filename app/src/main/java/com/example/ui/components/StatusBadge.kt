package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceStatus
import com.example.network.BrokerConnectionState
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.AlertWarning
import com.example.ui.theme.VerifiedGreen

@Composable
fun DeviceStatusBadge(status: DeviceStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor, dotColor, label) = when (status) {
        DeviceStatus.ONLINE -> Quadruple(
            Color(0xFFE8F5E9),
            Color(0xFF1B5E20),
            VerifiedGreen,
            "ONLINE"
        )
        DeviceStatus.OFFLINE -> Quadruple(
            Color(0xFFEEEEEE),
            Color(0xFF616161),
            Color(0xFF9E9E9E),
            "OFFLINE"
        )
        DeviceStatus.WARNING -> Quadruple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            AlertWarning,
            "WARNING"
        )
        DeviceStatus.ERROR -> Quadruple(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            AlertCritical,
            "ERROR"
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun ConnectionStateBadge(state: BrokerConnectionState, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (state) {
        BrokerConnectionState.CONNECTED -> Triple(Color(0xFFE8F5E9), Color(0xFF1B5E20), "MQTT CONNECTED")
        BrokerConnectionState.CONNECTING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "MQTT CONNECTING...")
        BrokerConnectionState.DISCONNECTED -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "MQTT DISCONNECTED")
        BrokerConnectionState.ERROR -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "MQTT ERROR")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
