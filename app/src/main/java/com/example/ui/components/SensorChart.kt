package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SensorReading
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.BatteryColor
import com.example.ui.theme.EthyleneColor
import com.example.ui.theme.HumidityColor
import com.example.ui.theme.TempColor
import com.example.ui.theme.VerifiedGreen

enum class StreamTabMetric(
    val title: String,
    val unit: String,
    val color: Color
) {
    TEMPERATURE("Temp", "°C", TempColor),
    HUMIDITY("Humidity", "%", HumidityColor),
    ETHYLENE("Ethylene", "ppm", EthyleneColor),
    BATTERY("Battery", "%", BatteryColor)
}

@Composable
fun MultiStreamTelemetryViewer(
    readings: List<SensorReading>,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedRangeLimit by remember { mutableIntStateOf(20) } // 10, 25, 0 (all)

    val currentMetric = when (selectedTabIndex) {
        0 -> StreamTabMetric.TEMPERATURE
        1 -> StreamTabMetric.HUMIDITY
        2 -> StreamTabMetric.ETHYLENE
        else -> StreamTabMetric.BATTERY
    }

    val filteredReadings = if (selectedRangeLimit > 0) {
        readings.take(selectedRangeLimit).reversed()
    } else {
        readings.reversed()
    }

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
            // Header: Title & Window selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Stream Multi-Sensor Visualizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time telemetry buffer analysis",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Range pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(10 to "10p", 25 to "25p", 0 to "All").forEach { (limit, label) ->
                        val isSelected = selectedRangeLimit == limit
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedRangeLimit = limit }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metric Switcher Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .height(44.dp)
            ) {
                listOf(
                    StreamTabMetric.TEMPERATURE to Icons.Default.Thermostat,
                    StreamTabMetric.HUMIDITY to Icons.Default.WaterDrop,
                    StreamTabMetric.ETHYLENE to Icons.Default.FilterDrama,
                    StreamTabMetric.BATTERY to Icons.Default.BatteryChargingFull
                ).forEachIndexed { index, (metric, icon) ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = metric.title,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) metric.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = metric.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) metric.color else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Chart rendering for current selected metric
            when (currentMetric) {
                StreamTabMetric.TEMPERATURE -> {
                    SensorTelemetryChart(
                        title = "Temperature (°C)",
                        unit = "°C",
                        lineColor = TempColor,
                        readings = filteredReadings,
                        valueExtractor = { it.temperature },
                        thresholdMax = 8.0,
                        thresholdMin = 2.0
                    )
                }
                StreamTabMetric.HUMIDITY -> {
                    SensorTelemetryChart(
                        title = "Relative Humidity (% RH)",
                        unit = "%",
                        lineColor = HumidityColor,
                        readings = filteredReadings,
                        valueExtractor = { it.humidity },
                        thresholdMax = 95.0,
                        thresholdMin = 85.0
                    )
                }
                StreamTabMetric.ETHYLENE -> {
                    SensorTelemetryChart(
                        title = "Ethylene Gas (C₂H₄)",
                        unit = "ppm",
                        lineColor = EthyleneColor,
                        readings = filteredReadings,
                        valueExtractor = { it.ethylene },
                        thresholdMax = 0.050
                    )
                }
                StreamTabMetric.BATTERY -> {
                    SensorTelemetryChart(
                        title = "Node Battery Level",
                        unit = "%",
                        lineColor = BatteryColor,
                        readings = filteredReadings,
                        valueExtractor = { it.battery.toDouble() },
                        thresholdMin = 20.0
                    )
                }
            }
        }
    }
}

@Composable
fun SensorTelemetryChart(
    title: String,
    unit: String,
    lineColor: Color,
    readings: List<SensorReading>,
    valueExtractor: (SensorReading) -> Double,
    thresholdMin: Double? = null,
    thresholdMax: Double? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val values = readings.map(valueExtractor)
            val hasData = values.isNotEmpty()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (hasData) {
                    val latest = values.last()
                    Text(
                        text = "%.2f %s".format(latest, unit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = lineColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sensor data received.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val minVal = values.minOrNull() ?: 0.0
                val maxVal = values.maxOrNull() ?: 1.0
                val range = if (maxVal - minVal < 0.001) 1.0 else maxVal - minVal

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val paddingVertical = 16f

                        if (values.size == 1) {
                            // Single point
                            drawCircle(
                                color = lineColor,
                                radius = 6f,
                                center = Offset(width / 2f, height / 2f)
                            )
                        } else {
                            val path = Path()
                            val fillPath = Path()
                            val stepX = width / (values.size - 1)

                            values.forEachIndexed { index, v ->
                                val x = index * stepX
                                val normalizedY = ((v - minVal) / range).toFloat()
                                val y = (height - paddingVertical) - normalizedY * (height - 2 * paddingVertical)

                                if (index == 0) {
                                    path.moveTo(x, y)
                                    fillPath.moveTo(x, height)
                                    fillPath.lineTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }

                                if (index == values.size - 1) {
                                    fillPath.lineTo(x, height)
                                    fillPath.close()
                                }
                            }

                            // Draw gradient fill
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        lineColor.copy(alpha = 0.25f),
                                        lineColor.copy(alpha = 0.02f)
                                    )
                                )
                            )

                            // Draw stroke
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw threshold SLA line if configured and in range
                            if (thresholdMax != null && thresholdMax >= minVal && thresholdMax <= maxVal) {
                                val normThreshY = ((thresholdMax - minVal) / range).toFloat()
                                val threshY = (height - paddingVertical) - normThreshY * (height - 2 * paddingVertical)
                                drawLine(
                                    color = AlertCritical.copy(alpha = 0.75f),
                                    start = Offset(0f, threshY),
                                    end = Offset(width, threshY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            }

                            // Draw last point dot
                            val lastX = (values.size - 1) * stepX
                            val lastNormalizedY = ((values.last() - minVal) / range).toFloat()
                            val lastY = (height - paddingVertical) - lastNormalizedY * (height - 2 * paddingVertical)
                            drawCircle(
                                color = lineColor,
                                radius = 5.dp.toPx(),
                                center = Offset(lastX, lastY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(lastX, lastY)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Min: %.1f %s".format(minVal, unit),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${values.size} points logged",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Max: %.1f %s".format(maxVal, unit),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
