package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Device
import com.example.data.model.DeviceStatus
import com.example.data.model.SensorReading
import com.example.network.BrokerConnectionState
import com.example.ui.components.ConnectionStateBadge
import com.example.ui.components.DeviceStatusBadge
import com.example.ui.components.EnvironmentalStreamHeroGauge
import com.example.ui.components.HashNodeItem
import com.example.ui.components.LiveStreamPulseIndicator
import com.example.ui.components.MetricCard
import com.example.ui.components.MultiStreamTelemetryViewer
import com.example.ui.components.StreamTrend
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.AlertWarning
import com.example.ui.theme.BatteryColor
import com.example.ui.theme.BlockchainGold
import com.example.ui.theme.EthyleneColor
import com.example.ui.theme.HumidityColor
import com.example.ui.theme.SolarColor
import com.example.ui.theme.TempColor
import com.example.ui.theme.VerifiedGreen

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToBatches: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToTraceability: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val events by viewModel.events.collectAsState()
    val unreadAlertsCount by viewModel.unacknowledgedAlertCount.collectAsState()

    // Local stream filter state: null means all connected nodes (aggregate), or specific deviceId
    var selectedStreamDeviceId by remember { mutableStateOf<String?>(null) }

    // Filter readings based on selected node
    val streamReadings = if (selectedStreamDeviceId == null) {
        readings
    } else {
        readings.filter { it.deviceId == selectedStreamDeviceId }
    }

    val selectedDevice = devices.firstOrNull { it.deviceId == selectedStreamDeviceId }
    val latestReading = streamReadings.firstOrNull()
    val previousReading = streamReadings.getOrNull(1)

    // Calculate dynamic stream trends
    val tempTrend = when {
        latestReading == null || previousReading == null -> StreamTrend.STEADY
        latestReading.temperature - previousReading.temperature > 0.15 -> StreamTrend.RISING
        previousReading.temperature - latestReading.temperature > 0.15 -> StreamTrend.FALLING
        else -> StreamTrend.STEADY
    }

    val humTrend = when {
        latestReading == null || previousReading == null -> StreamTrend.STEADY
        latestReading.humidity - previousReading.humidity > 0.5 -> StreamTrend.RISING
        previousReading.humidity - latestReading.humidity > 0.5 -> StreamTrend.FALLING
        else -> StreamTrend.STEADY
    }

    val ethyleneTrend = when {
        latestReading == null || previousReading == null -> StreamTrend.STEADY
        latestReading.ethylene - previousReading.ethylene > 0.003 -> StreamTrend.RISING
        previousReading.ethylene - latestReading.ethylene > 0.003 -> StreamTrend.FALLING
        else -> StreamTrend.STEADY
    }

    // Environmental buffer min/max metrics
    val tempMinRecorded = streamReadings.minOfOrNull { it.temperature }
    val tempMaxRecorded = streamReadings.maxOfOrNull { it.temperature }
    val humMinRecorded = streamReadings.minOfOrNull { it.humidity }
    val humMaxRecorded = streamReadings.maxOfOrNull { it.humidity }
    val ethyleneMinRecorded = streamReadings.minOfOrNull { it.ethylene }
    val ethyleneMaxRecorded = streamReadings.maxOfOrNull { it.ethylene }

    // Temperature status evaluation (Standard Cold Chain SLA: 2.0°C to 8.0°C)
    val currentTemp = latestReading?.temperature
    val isTempNormal = currentTemp != null && currentTemp in 2.0..8.0
    val tempStatusText = when {
        currentTemp == null -> "AWAITING SENSOR"
        currentTemp in 2.0..8.0 -> "SAFE COLD CHAIN (2-8°C)"
        currentTemp < 2.0 -> "FREEZING RISK (<2°C)"
        else -> "WARM EXCURSION (>8°C)"
    }

    // Humidity status evaluation (Standard agricultural fresh produce: 85% to 95% RH)
    val currentHum = latestReading?.humidity
    val isHumNormal = currentHum != null && currentHum in 85.0..95.0
    val humStatusText = when {
        currentHum == null -> "AWAITING SENSOR"
        currentHum in 85.0..95.0 -> "OPTIMAL MOISTURE"
        currentHum < 85.0 -> "DESICCATION RISK (<85%)"
        else -> "CONDENSATION HAZARD"
    }

    // Ethylene gas status evaluation (Safe Threshold: < 0.050 ppm)
    val currentEthylene = latestReading?.ethylene
    val isEthyleneNormal = currentEthylene != null && currentEthylene <= 0.050
    val ethyleneStatusText = when {
        currentEthylene == null -> "AWAITING SENSOR"
        currentEthylene <= 0.050 -> "SAFE (< 0.050 ppm)"
        currentEthylene <= 0.100 -> "ELEVATED RIPENING"
        else -> "CRITICAL SPOILAGE RISK"
    }

    val onlineDevicesCount = devices.count { it.status == DeviceStatus.ONLINE }
    val offlineDevicesCount = devices.count { it.status == DeviceStatus.OFFLINE }
    val activeBatchesCount = batches.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Brand & Live Connection Hub
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.farmtrace_hero_banner),
                            contentDescription = "FarmTrace Hero Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.38f))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "FARMTRACE",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                LiveStreamPulseIndicator(
                                    isStreaming = uiState.connectionState == BrokerConnectionState.CONNECTED || readings.isNotEmpty()
                                )
                            }
                            Text(
                                text = "Real-Time IoT Blockchain Environmental Stream Hub",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    // Broker Connection & Protocol Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConnectionStateBadge(state = uiState.connectionState)

                        if (uiState.connectionState == BrokerConnectionState.CONNECTED) {
                            Text(
                                text = uiState.brokerConfig.brokerHost,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.connectBroker() },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("Connect Broker", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Connected FarmTrace IoT Nodes Stream Selector Filter
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connected Sensor Nodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedStreamDeviceId == null) "Viewing All Nodes" else "Filtered by Node",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Global Stream Chip
                    item {
                        val isAllSelected = selectedStreamDeviceId == null
                        NodeStreamFilterChip(
                            label = "All Nodes (${devices.size})",
                            subtitle = "${readings.size} pkts",
                            isSelected = isAllSelected,
                            isOnline = onlineDevicesCount > 0,
                            onClick = { selectedStreamDeviceId = null }
                        )
                    }

                    // Per-device Stream Chips
                    items(devices) { dev ->
                        val isSelected = selectedStreamDeviceId == dev.deviceId
                        val devLatestReading = readings.firstOrNull { it.deviceId == dev.deviceId }
                        val subtitle = if (devLatestReading != null) {
                            "%.1f°C | %d%%".format(devLatestReading.temperature, dev.batteryPercent)
                        } else {
                            dev.deviceId.takeLast(6)
                        }

                        NodeStreamFilterChip(
                            label = dev.name.ifBlank { dev.deviceId },
                            subtitle = subtitle,
                            isSelected = isSelected,
                            isOnline = dev.status == DeviceStatus.ONLINE,
                            onClick = { selectedStreamDeviceId = dev.deviceId }
                        )
                    }
                }
            }
        }

        // 3. Active Stream Telemetry Metadata Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = selectedDevice?.name ?: "All Connected FarmTrace Nodes",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedDevice != null) {
                                    "ID: ${selectedDevice.deviceId} • ${selectedDevice.type.name}"
                                } else {
                                    "Aggregated Environmental Stream • ${streamReadings.size} packets"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (latestReading != null) {
                        val ageSec = ((System.currentTimeMillis() - latestReading.timestamp) / 1000).coerceAtLeast(0)
                        Text(
                            text = if (ageSec < 60) "${ageSec}s ago" else "${ageSec / 60}m ago",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Three Hero Environmental Data Stream Gauges (Temperature, Humidity, Ethylene)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Temperature Real-Time Stream Card
                EnvironmentalStreamHeroGauge(
                    metricName = "Ambient & Core Temperature",
                    currentValue = currentTemp,
                    unit = "°C",
                    icon = Icons.Default.Thermostat,
                    accentColor = TempColor,
                    minThreshold = 2.0,
                    maxThreshold = 8.0,
                    trend = tempTrend,
                    minRecorded = tempMinRecorded,
                    maxRecorded = tempMaxRecorded,
                    statusText = tempStatusText,
                    isNormal = isTempNormal
                )

                // Relative Humidity Real-Time Stream Card
                EnvironmentalStreamHeroGauge(
                    metricName = "Relative Humidity (RH)",
                    currentValue = currentHum,
                    unit = "%",
                    icon = Icons.Default.WaterDrop,
                    accentColor = HumidityColor,
                    minThreshold = 85.0,
                    maxThreshold = 95.0,
                    trend = humTrend,
                    minRecorded = humMinRecorded,
                    maxRecorded = humMaxRecorded,
                    statusText = humStatusText,
                    isNormal = isHumNormal
                )

                // Ethylene Gas (C₂H₄) Real-Time Stream Card
                EnvironmentalStreamHeroGauge(
                    metricName = "Ethylene Ripening Gas (C₂H₄)",
                    currentValue = currentEthylene,
                    unit = "ppm",
                    icon = Icons.Default.FilterDrama,
                    accentColor = EthyleneColor,
                    minThreshold = null,
                    maxThreshold = 0.050,
                    trend = ethyleneTrend,
                    minRecorded = ethyleneMinRecorded,
                    maxRecorded = ethyleneMaxRecorded,
                    statusText = ethyleneStatusText,
                    isNormal = isEthyleneNormal
                )
            }
        }

        // 5. Interactive Multi-Stream Telemetry Time-Series Chart
        item {
            MultiStreamTelemetryViewer(
                readings = streamReadings
            )
        }

        // 6. Connected IoT Hardware Nodes Status Matrix
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Nodes Status (${devices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onNavigateToDevices,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Manage", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(devices) { device ->
                        val devReading = readings.firstOrNull { it.deviceId == device.deviceId }
                        HardwareNodeCard(
                            device = device,
                            latestReading = devReading,
                            isSelected = selectedStreamDeviceId == device.deviceId,
                            onSelect = {
                                selectedStreamDeviceId = if (selectedStreamDeviceId == device.deviceId) null else device.deviceId
                            },
                            onInspect = {
                                viewModel.selectDevice(device.deviceId)
                            }
                        )
                    }
                }
            }
        }

        // 7. Live Stream Ingestion & Testing Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Stream Testing & Verification",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ESP32 SHT31/ZE03 Physics",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.emitSimulatedReading(targetDeviceId = selectedStreamDeviceId)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("transmit_telemetry_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Transmit Pkt", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.emitSimulatedReading(
                                    targetDeviceId = selectedStreamDeviceId,
                                    isExcursion = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertWarning),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("simulate_excursion_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excursion Test", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 8. Key Performance Indicators (KPIs)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Active Batches",
                        value = "$activeBatchesCount",
                        unit = "Lots",
                        icon = Icons.Default.Eco,
                        accentColor = MaterialTheme.colorScheme.primary,
                        subtext = "Traceable on ledger",
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToBatches() }
                    )
                    MetricCard(
                        title = "IoT Nodes",
                        value = "$onlineDevicesCount/${devices.size}",
                        unit = "Online",
                        icon = Icons.Default.Sensors,
                        accentColor = if (onlineDevicesCount > 0) VerifiedGreen else Color.Gray,
                        subtext = if (offlineDevicesCount > 0) "$offlineDevicesCount node(s) offline" else "All nodes active",
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToDevices() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Active Alerts",
                        value = "$unreadAlertsCount",
                        unit = "Excursions",
                        icon = Icons.Default.NotificationsActive,
                        accentColor = if (unreadAlertsCount > 0) AlertCritical else VerifiedGreen,
                        subtext = if (unreadAlertsCount > 0) "Requires inspection" else "Within safe limits",
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToAlerts() }
                    )
                    MetricCard(
                        title = "Blockchain Proofs",
                        value = "${events.size}",
                        unit = "Blocks",
                        icon = Icons.Default.Hub,
                        accentColor = BlockchainGold,
                        subtext = "SHA-256 chained",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 9. Latest Cryptographic Immutable Ledger Events
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Immutable Ledger Stream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (batches.isNotEmpty()) {
                    Button(
                        onClick = { onNavigateToTraceability(batches.first().batchId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify Chain", fontSize = 11.sp)
                    }
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No ledger events recorded yet.\nTransmit an IoT packet to anchor verifiable environmental telemetry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(events.take(4)) { event ->
                HashNodeItem(
                    event = event,
                    index = events.indexOf(event) + 1,
                    verification = null
                )
            }
        }
    }
}

@Composable
fun NodeStreamFilterChip(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    isOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) VerifiedGreen else Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun HardwareNodeCard(
    device: Device,
    latestReading: SensorReading?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name.ifBlank { device.deviceId },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                DeviceStatusBadge(status = device.status)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ID: ${device.deviceId}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Environmental mini readings
            if (latestReading != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniStreamBadge(
                        label = "%.1f°C".format(latestReading.temperature),
                        color = TempColor
                    )
                    MiniStreamBadge(
                        label = "%.1f%%".format(latestReading.humidity),
                        color = HumidityColor
                    )
                    MiniStreamBadge(
                        label = "%.3fppm".format(latestReading.ethylene),
                        color = EthyleneColor
                    )
                }
            } else {
                Text(
                    text = "No live packet received",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Power & Energy info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = BatteryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${device.batteryPercent}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (latestReading != null && latestReading.solarVoltage > 0.5) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = SolarColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "%.1fV Solar".format(latestReading.solarVoltage),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = if (isSelected) "Filtering" else "Select",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MiniStreamBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
