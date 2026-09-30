package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TraceabilityEventType {
    STAGE_TRANSITION,
    PERIODIC_CHECKPOINT,
    TEMPERATURE_EXCURSION,
    QUALITY_INSPECTION,
    CUSTODY_TRANSFER
}

enum class BlockchainSyncStatus {
    CONFIRMED,
    PENDING,
    REJECTED
}

@Entity(
    tableName = "traceability_events",
    indices = [
        Index("batchId"),
        Index("timestamp"),
        Index("blockchainTxId")
    ]
)
data class TraceabilityEvent(
    @PrimaryKey val eventId: String,
    val batchId: String,
    val eventType: TraceabilityEventType,
    val stage: SupplyChainStage,
    val location: String,
    val operator: String,
    val timestamp: Long,
    val deviceId: String,
    val environmentalConditions: String, // e.g. "Temp: 4.2°C, RH: 88%, Ethylene: 0.08ppm"
    val blockchainTxId: String,
    val previousHash: String,
    val currentHash: String,
    val status: BlockchainSyncStatus = BlockchainSyncStatus.CONFIRMED
)
