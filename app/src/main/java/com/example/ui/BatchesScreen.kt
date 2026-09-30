package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.Batch
import com.example.ui.components.BatchQrCode
import com.example.ui.components.StageTimeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun BatchesScreen(
    viewModel: MainViewModel,
    onSelectBatch: (String) -> Unit,
    onOpenPublicVerify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val batches by viewModel.batches.collectAsState()
    val devices by viewModel.devices.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var qrBatchToShow by remember { mutableStateOf<Batch?>(null) }

    Scaffold(
        modifier = modifier.testTag("batches_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("create_batch_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Batch")
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

            Text(
                text = "Supply Chain Batches",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "End-to-end tracked produce lots with immutable cryptographic event logs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (batches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Batches Registered",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Click the '+' button to register a new produce batch for Farm-to-Fork traceability.",
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
                    items(batches) { batch ->
                        BatchCardItem(
                            batch = batch,
                            onClick = { onSelectBatch(batch.batchId) },
                            onShowQr = { qrBatchToShow = batch }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBatchDialog(
            availableDevices = devices.map { it.deviceId },
            onDismiss = { showCreateDialog = false },
            onCreate = { bId, prod, varName, farm, hDate, qty, unit, dest, tMin, tMax, hMin, hMax, ethMax, devId ->
                viewModel.createBatch(
                    batchId = bId,
                    productName = prod,
                    variety = varName,
                    farmOrigin = farm,
                    harvestDate = hDate,
                    quantity = qty,
                    unit = unit,
                    destination = dest,
                    tempMin = tMin,
                    tempMax = tMax,
                    humidityMin = hMin,
                    humidityMax = hMax,
                    maxEthylene = ethMax,
                    deviceId = devId
                )
                showCreateDialog = false
            }
        )
    }

    qrBatchToShow?.let { b ->
        AlertDialog(
            onDismissRequest = { qrBatchToShow = null },
            title = {
                Text(
                    text = "Public Traceability QR",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = b.productName + " (${b.variety})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = b.batchId,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BatchQrCode(batchId = b.batchId, size = 180.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scan to verify origin, storage temperature conditions, and blockchain proof.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val batchId = b.batchId
                    qrBatchToShow = null
                    onOpenPublicVerify(batchId)
                }) {
                    Text("Open Consumer Verification")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrBatchToShow = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun BatchCardItem(
    batch: Batch,
    onClick: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("batch_item_${batch.batchId}"),
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
                        text = batch.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${batch.variety} • ${batch.quantity} ${batch.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onShowQr,
                    modifier = Modifier.testTag("batch_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "View QR",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${batch.farmOrigin} → ${batch.destination}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Req: ${batch.tempMin}°C - ${batch.tempMax}°C | RH ${batch.humidityMin}%-${batch.humidityMax}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!batch.assignedDeviceId.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = Color(0xFF00695C),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = batch.assignedDeviceId.takeLast(8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00695C)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stage visualizer
            StageTimeline(currentStage = batch.currentStage)

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${batch.batchId}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View Details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateBatchDialog(
    availableDevices: List<String>,
    onDismiss: () -> Unit,
    onCreate: (
        batchId: String,
        prod: String,
        variety: String,
        farm: String,
        hDate: String,
        qty: Double,
        unit: String,
        dest: String,
        tMin: Double,
        tMax: Double,
        hMin: Double,
        hMax: Double,
        ethMax: Double,
        devId: String?
    ) -> Unit
) {
    val defaultBatchId = remember { "FT-BATCH-" + UUID.randomUUID().toString().take(6).uppercase() }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var batchId by remember { mutableStateOf(defaultBatchId) }
    var product by remember { mutableStateOf("Fresh Hass Avocados") }
    var variety by remember { mutableStateOf("Grade A Export") }
    var farm by remember { mutableStateOf("Green Valley Orchard, Michoacán") }
    var harvestDate by remember { mutableStateOf(today) }
    var quantity by remember { mutableStateOf("2400") }
    var unit by remember { mutableStateOf("kg") }
    var destination by remember { mutableStateOf("Rotterdam Cold Terminal, NL") }
    var tempMin by remember { mutableStateOf("4.0") }
    var tempMax by remember { mutableStateOf("7.5") }
    var humidityMin by remember { mutableStateOf("85.0") }
    var humidityMax by remember { mutableStateOf("95.0") }
    var maxEthylene by remember { mutableStateOf("0.05") }
    var selectedDevice by remember { mutableStateOf(availableDevices.firstOrNull() ?: "ESP32-NODE-01") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Produce Batch", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = batchId,
                    onValueChange = { batchId = it },
                    label = { Text("Batch ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = product,
                    onValueChange = { product = it },
                    label = { Text("Product Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = variety,
                    onValueChange = { variety = it },
                    label = { Text("Product Variety / Grade") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = farm,
                    onValueChange = { farm = it },
                    label = { Text("Farm Origin Location") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Target Destination") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(0.6f),
                        singleLine = true
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempMin,
                        onValueChange = { tempMin = it },
                        label = { Text("Min Temp (°C)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tempMax,
                        onValueChange = { tempMax = it },
                        label = { Text("Max Temp (°C)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = humidityMin,
                        onValueChange = { humidityMin = it },
                        label = { Text("Min RH (%)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = humidityMax,
                        onValueChange = { humidityMax = it },
                        label = { Text("Max RH (%)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = maxEthylene,
                    onValueChange = { maxEthylene = it },
                    label = { Text("Max Ethylene (ppm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = selectedDevice,
                    onValueChange = { selectedDevice = it },
                    label = { Text("Assign IoT Node Device ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        batchId,
                        product,
                        variety,
                        farm,
                        harvestDate,
                        quantity.toDoubleOrNull() ?: 100.0,
                        unit,
                        destination,
                        tempMin.toDoubleOrNull() ?: 2.0,
                        tempMax.toDoubleOrNull() ?: 8.0,
                        humidityMin.toDoubleOrNull() ?: 85.0,
                        humidityMax.toDoubleOrNull() ?: 95.0,
                        maxEthylene.toDoubleOrNull() ?: 0.05,
                        selectedDevice.ifBlank { null }
                    )
                },
                modifier = Modifier.testTag("confirm_create_batch_button")
            ) {
                Text("Register Batch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
