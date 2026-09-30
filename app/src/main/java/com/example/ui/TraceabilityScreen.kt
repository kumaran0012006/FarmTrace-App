package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.components.HashNodeItem
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.AlertCriticalContainer
import com.example.ui.theme.BlockchainGold
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceabilityScreen(
    initialBatchId: String?,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val batches by viewModel.batches.collectAsState()
    val allEvents by viewModel.events.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var selectedBatchId by remember(initialBatchId, batches) {
        mutableStateOf(
            initialBatchId
                ?: uiState.selectedBatchId
                ?: batches.firstOrNull()?.batchId
                ?: ""
        )
    }

    var dropdownExpanded by remember { mutableStateOf(false) }

    val batchEvents = allEvents.filter { it.batchId == selectedBatchId }.sortedBy { it.timestamp }
    val currentBatch = batches.firstOrNull { it.batchId == selectedBatchId }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("traceability_screen"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Cryptographic Traceability Chain",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tamper-evident SHA-256 hash chaining anchored to distributed ledger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Batch Selector Dropdown
        item {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = if (currentBatch != null) "${currentBatch.productName} (${currentBatch.batchId})" else "Select a batch",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Traceable Batch") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    batches.forEach { b ->
                        DropdownMenuItem(
                            text = { Text("${b.productName} — ${b.batchId}") },
                            onClick = {
                                selectedBatchId = b.batchId
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Interactive "Verify Integrity" Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        uiState.verificationResult == null -> MaterialTheme.colorScheme.surface
                        uiState.verificationResult!!.isValid -> Color(0xFFE8F5E9)
                        else -> AlertCriticalContainer
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = when {
                                    uiState.verificationResult == null -> MaterialTheme.colorScheme.primary
                                    uiState.verificationResult!!.isValid -> VerifiedGreen
                                    else -> AlertCritical
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Data Integrity Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (selectedBatchId.isNotBlank()) {
                                    viewModel.verifyIntegrity(selectedBatchId)
                                }
                            },
                            enabled = selectedBatchId.isNotBlank() && !uiState.isVerifyingIntegrity,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("verify_integrity_button")
                        ) {
                            if (uiState.isVerifyingIntegrity) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verifying...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify Integrity")
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.QR_SCANNER) },
                            modifier = Modifier.testTag("traceability_scan_qr_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Node QR")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Recalculates every SHA-256 hash along the event sequence: current = SHA256(prev + id + dev + batch + time + sensor). Detects unauthorized record alteration or deletion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Verification Output
                    if (uiState.verificationResult != null) {
                        val res = uiState.verificationResult!!
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (res.isValid) VerifiedGreen.copy(alpha = 0.15f) else AlertCritical.copy(alpha = 0.15f))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (res.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (res.isValid) VerifiedGreen else AlertCritical,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (res.isValid) "VERIFIED: Cryptographic Chain Intact" else "INTEGRITY VIOLATION DETECTED",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = if (res.isValid) VerifiedGreen else AlertCritical
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Inspected: ${res.verifiedNodesCount} / ${res.totalNodesChecked} blocks verified link-by-link against SHA-256 proofs.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (!res.isValid && res.failureReason != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Diagnostic: ${res.failureReason}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AlertCritical
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chronological Chain Events
        item {
            Text(
                text = "Chronological Chain Links (${batchEvents.size} Blocks)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (batchEvents.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No events logged for this batch yet.\nAdvance batch stage or send sensor telemetry to generate ledger blocks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(batchEvents) { index, event ->
                val auditMatch = uiState.verificationResult?.nodeAuditTrail?.firstOrNull { it.eventId == event.eventId }
                HashNodeItem(
                    event = event,
                    index = index + 1,
                    verification = auditMatch
                )
            }
        }
    }
}
