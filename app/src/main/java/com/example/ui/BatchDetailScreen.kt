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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SupplyChainStage
import com.example.ui.components.BatchQrCode
import com.example.ui.components.MetricCard
import com.example.ui.components.SensorTelemetryChart
import com.example.ui.components.StageTimeline
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.BatteryColor
import com.example.ui.theme.BlockchainGold
import com.example.ui.theme.EthyleneColor
import com.example.ui.theme.HumidityColor
import com.example.ui.theme.TempColor
import com.example.ui.theme.VerifiedGreen

@Composable
fun BatchDetailScreen(
    batchId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToTraceability: (String) -> Unit,
    onNavigateToAiInsights: (String) -> Unit,
    onNavigateToPublicVerify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val batches by viewModel.batches.collectAsState()
    val allReadings by viewModel.readings.collectAsState()
    val allAlerts by viewModel.alerts.collectAsState()

    val batch = batches.firstOrNull { it.batchId == batchId }
    val batchReadings = allReadings.filter { it.batchId == batchId }.sortedBy { it.timestamp }
    val batchAlerts = allAlerts.filter { it.batchId == batchId }
    val latestReading = batchReadings.lastOrNull()

    var showAdvanceDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    if (batch == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Batch not found: $batchId", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Back to Batches") }
            }
        }
        return
    }

    Scaffold(
        modifier = modifier.testTag("batch_detail_screen"),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = batch.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = batch.batchId,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showQrDialog = true }) {
                    Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stage Progression Card
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
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current Supply Chain Stage",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = batch.currentStage.displayName.uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val nextStage = SupplyChainStage.values().firstOrNull { it.order == batch.currentStage.order + 1 }
                            if (nextStage != null) {
                                Button(
                                    onClick = { showAdvanceDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("advance_stage_button")
                                ) {
                                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Next: ${nextStage.displayName}", fontSize = 12.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VerifiedGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "FINAL DESTINATION REACHED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VerifiedGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        StageTimeline(currentStage = batch.currentStage)
                    }
                }
            }

            // Batch Overview & Thresholds Card
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
                        Text(
                            text = "Batch Metadata & Environmental SLA",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Product Variety / Lot:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${batch.variety} (${batch.quantity} ${batch.unit})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Origin & Destination:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${batch.farmOrigin} → ${batch.destination}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Required Cold-Chain Temp:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${batch.tempMin}°C to ${batch.tempMax}°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Permissible Humidity (RH):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${batch.humidityMin}% to ${batch.humidityMax}% RH", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Max Ethylene (C₂H₄):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${batch.maxEthylenePpm} ppm", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Assigned IoT Hardware Node:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(batch.assignedDeviceId ?: "None Assigned", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Action Quick Links: Blockchain Integrity & AI Quality
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigateToTraceability(batch.batchId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("verify_blockchain_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ledger Proof", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onNavigateToAiInsights(batch.batchId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("run_ai_analytics_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Quality", fontSize = 12.sp)
                    }
                }
            }

            // Real Live Telemetry for this Batch
            item {
                Text(
                    text = "Batch Telemetry & Sensors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (latestReading != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Temperature",
                            value = "%.1f".format(latestReading.temperature),
                            unit = "°C",
                            icon = Icons.Default.Thermostat,
                            accentColor = if (latestReading.temperature in batch.tempMin..batch.tempMax) VerifiedGreen else AlertCritical,
                            subtext = if (latestReading.temperature in batch.tempMin..batch.tempMax) "In Safe SLA Range" else "Out of SLA Range",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Humidity",
                            value = "%.1f".format(latestReading.humidity),
                            unit = "%",
                            icon = Icons.Default.WaterDrop,
                            accentColor = HumidityColor,
                            subtext = "${latestReading.humidity.toInt()}% Relative Humidity",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Ethylene Gas",
                            value = "%.3f".format(latestReading.ethylene),
                            unit = "ppm",
                            icon = Icons.Default.Sensors,
                            accentColor = EthyleneColor,
                            subtext = if (latestReading.ethylene <= batch.maxEthylenePpm) "Normal ripening" else "High ethylene surge",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Node Battery",
                            value = "${latestReading.battery}",
                            unit = "%",
                            icon = Icons.Default.Sensors,
                            accentColor = BatteryColor,
                            subtext = "Solar harvesting active",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Charts for this specific batch
            item {
                SensorTelemetryChart(
                    title = "Temperature Tracking",
                    unit = "°C",
                    lineColor = TempColor,
                    readings = batchReadings,
                    valueExtractor = { it.temperature }
                )
            }

            item {
                SensorTelemetryChart(
                    title = "Relative Humidity Tracking",
                    unit = "%",
                    lineColor = HumidityColor,
                    readings = batchReadings,
                    valueExtractor = { it.humidity }
                )
            }
        }
    }

    // Dialog for Advancing Stage
    if (showAdvanceDialog) {
        val nextStage = SupplyChainStage.values().firstOrNull { it.order == batch.currentStage.order + 1 }
        if (nextStage != null) {
            var location by remember { mutableStateOf(batch.destination) }
            var operator by remember { mutableStateOf("Cold-Chain Logistics Officer") }

            AlertDialog(
                onDismissRequest = { showAdvanceDialog = false },
                title = { Text("Advance to ${nextStage.displayName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "This will advance ${batch.batchId} from ${batch.currentStage.displayName} to ${nextStage.displayName} and record a cryptographic SHA-256 event block on the ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Current Location / Checkpoint") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = operator,
                            onValueChange = { operator = it },
                            label = { Text("Certified Operator / Officer") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.advanceBatchStage(batch.batchId, nextStage, location, operator)
                            showAdvanceDialog = false
                        }
                    ) {
                        Text("Confirm Transition")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdvanceDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Public Consumer Traceability QR", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(batch.productName, fontWeight = FontWeight.Bold)
                    Text(batch.batchId, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(14.dp))
                    BatchQrCode(batchId = batch.batchId, size = 180.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Point camera to verify authenticity, cold-chain compliance, and blockchain audit proof.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showQrDialog = false
                    onNavigateToPublicVerify(batch.batchId)
                }) {
                    Text("Open Public Verification")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Close") }
            }
        )
    }
}
