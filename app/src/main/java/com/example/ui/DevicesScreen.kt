package com.example.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Device
import com.example.data.model.DeviceStatus
import com.example.data.model.DeviceType
import com.example.ui.components.DeviceStatusBadge
import com.example.ui.theme.BatteryColor
import com.example.ui.theme.SolarColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onSelectDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val batches by viewModel.batches.collectAsState()

    var showRegisterDialog by remember { mutableStateOf(false) }
    var showIngestDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("devices_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showRegisterDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("register_device_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Register Device")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "IoT Hardware Nodes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ESP32 rugged multi-sensor cold-chain loggers with cryptographic storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { showIngestDialog = true },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manual Ingress", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No IoT Nodes Registered",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Register an ESP32 hardware device or submit a telemetry packet via Manual Ingress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(devices) { device ->
                        DeviceCardItem(
                            device = device,
                            onClick = { onSelectDevice(device.deviceId) }
                        )
                    }
                }
            }
        }
    }

    if (showRegisterDialog) {
        RegisterDeviceDialog(
            availableBatches = batches.map { it.batchId },
            onDismiss = { showRegisterDialog = false },
            onRegister = { id, name, type, batchId ->
                viewModel.registerDevice(id, name, type, batchId)
                showRegisterDialog = false
            }
        )
    }

    if (showIngestDialog) {
        ManualIngressDialog(
            defaultDeviceId = devices.firstOrNull()?.deviceId ?: "ESP32-NODE-01",
            defaultBatchId = batches.firstOrNull()?.batchId ?: "FT-BATCH-2026-0891",
            onDismiss = { showIngestDialog = false },
            onSubmitJson = { json ->
                viewModel.ingestRawPayload(json)
                showIngestDialog = false
            }
        )
    }
}

@Composable
fun DeviceCardItem(
    device: Device,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    val lastSeenStr = if (device.lastSeenTimestamp > 0) {
        val diffSec = (System.currentTimeMillis() - device.lastSeenTimestamp) / 1000
        if (diffSec < 60) "${diffSec}s ago" else "${diffSec / 60}m ago"
    } else "Never seen"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("device_item_${device.deviceId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${device.deviceId}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DeviceStatusBadge(status = device.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = BatteryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.batteryPercent}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Signal",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.signalDbm} dBm",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "FW: ${device.firmwareVersion}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (device.assignedBatchId != null) "Batch: ${device.assignedBatchId}" else "Unassigned",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (device.assignedBatchId != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )

                Text(
                    text = "Last seen: $lastSeenStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RegisterDeviceDialog(
    availableBatches: List<String>,
    onDismiss: () -> Unit,
    onRegister: (id: String, name: String, type: DeviceType, batchId: String?) -> Unit
) {
    val defaultId = remember { "ESP32-NODE-" + UUID.randomUUID().toString().take(4).uppercase() }
    var id by remember { mutableStateOf(defaultId) }
    var name by remember { mutableStateOf("Reefer Container Node 104") }
    var type by remember { mutableStateOf(DeviceType.ESP32_CELLULAR_NODE) }
    var batchId by remember { mutableStateOf(availableBatches.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provision IoT Hardware Node", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Device ID (Hardware MAC/EUI)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Descriptive Device Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = batchId,
                    onValueChange = { batchId = it },
                    label = { Text("Assign to Batch ID (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRegister(id, name, type, batchId.ifBlank { null }) },
                modifier = Modifier.testTag("confirm_register_device_button")
            ) {
                Text("Provision Node")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ManualIngressDialog(
    defaultDeviceId: String,
    defaultBatchId: String,
    onDismiss: () -> Unit,
    onSubmitJson: (String) -> Unit
) {
    val samplePayload = remember {
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        """
        {
          "event_id": "$eventId",
          "device_id": "$defaultDeviceId",
          "batch_id": "$defaultBatchId",
          "timestamp": $now,
          "temperature": 4.8,
          "humidity": 89.2,
          "ethylene": 0.032,
          "battery": 94,
          "solar_voltage": 5.12,
          "solar_current": 180.5,
          "solar_energy": 924.1,
          "power_consumption": 45.0,
          "latitude": 34.0522,
          "longitude": -118.2437,
          "signal_strength": -68,
          "firmware_version": "v1.4.2",
          "signature": "MEQCIFz...cryptographic_signature"
        }
        """.trimIndent()
    }

    var jsonText by remember { mutableStateOf(samplePayload) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hardware Telemetry Ingress", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Submit a real ESP32 JSON telemetry packet directly to test MQTT ingestion, signature hashing, idempotency, and blockchain anchoring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("Raw JSON Packet") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmitJson(jsonText) }) {
                Text("Ingest Telemetry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
