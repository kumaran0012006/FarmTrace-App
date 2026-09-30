package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.AlertWarning
import com.example.ui.theme.BatteryColor
import com.example.ui.theme.EthyleneColor
import com.example.ui.theme.HumidityColor
import com.example.ui.theme.SolarColor
import com.example.ui.theme.TempColor
import com.example.ui.theme.VerifiedGreen

enum class StreamTrend {
    RISING,
    FALLING,
    STEADY
}

@Composable
fun EnvironmentalStreamHeroGauge(
    metricName: String,
    currentValue: Double?,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    minThreshold: Double?,
    maxThreshold: Double?,
    trend: StreamTrend = StreamTrend.STEADY,
    minRecorded: Double? = null,
    maxRecorded: Double? = null,
    statusText: String,
    isNormal: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Icon, Metric Title, Live Pulse Dot, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = metricName,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = metricName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (minThreshold != null && maxThreshold != null)
                                "SLA: %.1f%s - %.1f%s".format(minThreshold, unit, maxThreshold, unit)
                            else if (maxThreshold != null)
                                "Max Allowed: < %.3f%s".format(maxThreshold, unit)
                            else "Environmental stream",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip
                val statusBg = if (isNormal) VerifiedGreen.copy(alpha = 0.15f) else AlertCritical.copy(alpha = 0.15f)
                val statusTextColor = if (isNormal) VerifiedGreen else AlertCritical
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Value + Trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                if (currentValue != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (unit == "ppm") "%.3f".format(currentValue) else "%.1f".format(currentValue),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isNormal) MaterialTheme.colorScheme.onSurface else AlertCritical
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = unit,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Trend Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        val (trendIcon, trendText, trendColor) = when (trend) {
                            StreamTrend.RISING -> Triple(Icons.Default.ArrowUpward, "Rising", AlertWarning)
                            StreamTrend.FALLING -> Triple(Icons.Default.ArrowDownward, "Falling", accentColor)
                            StreamTrend.STEADY -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, "Steady", VerifiedGreen)
                        }
                        Icon(
                            imageVector = trendIcon,
                            contentDescription = trendText,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = trendText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = trendColor
                        )
                    }
                } else {
                    Text(
                        text = "No stream data",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Visual Range Bar
            if (currentValue != null && minThreshold != null && maxThreshold != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val totalSpan = (maxThreshold - minThreshold).coerceAtLeast(1.0)
                val progress = ((currentValue - minThreshold) / totalSpan).toFloat().coerceIn(0f, 1f)

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isNormal) accentColor else AlertCritical,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-metrics: Min/Max recorded in buffer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Buffer Min: " + (if (minRecorded != null) (if (unit == "ppm") "%.3f %s".format(minRecorded, unit) else "%.1f %s".format(minRecorded, unit)) else "--"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Buffer Max: " + (if (maxRecorded != null) (if (unit == "ppm") "%.3f %s".format(maxRecorded, unit) else "%.1f %s".format(maxRecorded, unit)) else "--"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LiveStreamPulseIndicator(
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isStreaming) VerifiedGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isStreaming) VerifiedGreen.copy(alpha = alpha) else Color.Gray
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isStreaming) "LIVE STREAMING" else "IDLE STREAM",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isStreaming) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
