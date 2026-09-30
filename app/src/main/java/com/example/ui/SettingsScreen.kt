package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.network.BrokerConfig
import com.example.network.BrokerConnectionState
import com.example.ui.components.ConnectionStateBadge
import com.example.ui.theme.VerifiedGreen

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
    val isConnected = uiState.connectionState == BrokerConnectionState.CONNECTED

    var host by remember { mutableStateOf(uiState.brokerConfig.brokerHost) }
    var portStr by remember { mutableStateOf(uiState.brokerConfig.port.toString()) }
    var path by remember { mutableStateOf(uiState.brokerConfig.path) }
    var useTls by remember { mutableStateOf(uiState.brokerConfig.useTls) }
    var topicFilter by remember { mutableStateOf(uiState.brokerConfig.topicFilter) }
    var clientId by remember { mutableStateOf(uiState.brokerConfig.clientId) }

    val hasGeminiKey = BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "System Settings & Connectivity",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure real MQTT Broker, TLS settings, AI Studio secrets, and ESP32 hardware contracts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Room-to-Firestore Cloud Synchronization Card
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Cloud Sync (Room to Firestore)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Farm-to-fork environmental telemetry sync",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (cloudSyncStatus.isCloudConnected) VerifiedGreen.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.errorContainer
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (cloudSyncStatus.isCloudConnected) "Ready" else "Offline",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cloudSyncStatus.isCloudConnected) VerifiedGreen else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Sync Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Pending Sync", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${cloudSyncStatus.pendingReadingsCount} pkts",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Total Uploaded", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${cloudSyncStatus.totalSyncedReadings} pkts",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VerifiedGreen
                                )
                            }
                        }
                    }

                    // Firestore collections breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Firestore Target Collections:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("• farm_batches (Metadata, origins, cold-chain SLA)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• environmental_telemetry (SHT31/ZE03 readings & hashes)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• traceability_chain (SHA-256 chained stage proofs)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• environmental_alerts (Temperature & gas excursions)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (cloudSyncStatus.lastSyncSummary != null) {
                        Text(
                            text = cloudSyncStatus.lastSyncSummary ?: "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (cloudSyncStatus.lastError != null) {
                        Text(
                            text = "Note: ${cloudSyncStatus.lastError}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = { viewModel.triggerCloudSync() },
                        enabled = !cloudSyncStatus.isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("sync_to_firestore_btn")
                    ) {
                        if (cloudSyncStatus.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing to Cloud...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Room to Cloud Firestore", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // MQTT Connection Management Card
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MQTT / WebSocket Broker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        ConnectionStateBadge(state = uiState.connectionState)
                    }

                    if (uiState.connectionStatusMessage != null) {
                        Text(
                            text = "Status: ${uiState.connectionStatusMessage}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Broker Host / IP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = portStr,
                            onValueChange = { portStr = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = { Text("WebSocket Path") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable TLS (WSS / SSL)", fontSize = 13.sp)
                        Switch(
                            checked = useTls,
                            onCheckedChange = { useTls = it }
                        )
                    }

                    OutlinedTextField(
                        value = topicFilter,
                        onValueChange = { topicFilter = it },
                        label = { Text("Telemetry Topic Pattern") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isConnected) {
                            Button(
                                onClick = { viewModel.disconnectBroker() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Disconnect Broker")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val newConf = BrokerConfig(
                                        brokerHost = host.trim(),
                                        port = portStr.toIntOrNull() ?: 8083,
                                        path = path.trim(),
                                        useTls = useTls,
                                        topicFilter = topicFilter.trim(),
                                        clientId = clientId.trim()
                                    )
                                    viewModel.connectBroker(newConf)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("connect_broker_button")
                            ) {
                                Text("Connect to Broker")
                            }
                        }
                    }
                }
            }
        }

        // Gemini AI Studio Secrets Status
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Studio Secrets Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GEMINI_API_KEY Status:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasGeminiKey) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasGeminiKey) "CONFIGURED" else "PENDING SECRET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasGeminiKey) VerifiedGreen else Color(0xFFE65100)
                            )
                        }
                    }

                    Text(
                        text = "Configured securely through the Secrets panel in AI Studio UI and injected via BuildConfig. Never hardcoded in source files.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ESP32 Integration Contract
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cable, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ESP32 Hardware Node Contract",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Real ESP32 nodes publish JSON payloads to topic:\nfarmtrace/{deviceId}/telemetry",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = """
                            {
                              "event_id": "UUID",
                              "device_id": "ESP32-NODE-XXX",
                              "batch_id": "FT-BATCH-XXX",
                              "timestamp": 1727500000000,
                              "temperature": 4.5,
                              "humidity": 88.0,
                              "ethylene": 0.035,
                              "battery": 92,
                              "solar_voltage": 5.1,
                              "solar_current": 150.0,
                              "signature": "ECDSA_HEX"
                            }
                            """.trimIndent(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
