package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SupplyChainStage(val displayName: String, val order: Int) {
    FARM("Farm", 1),
    HARVEST("Harvest", 2),
    PROCESSING("Processing", 3),
    PACKAGING("Packaging", 4),
    TRANSPORT("Transport", 5),
    WAREHOUSE("Warehouse", 6),
    DISTRIBUTION("Distribution", 7),
    RETAIL("Retail", 8)
}

enum class BatchStatus {
    ACTIVE,
    COMPLETED,
    FLAGGED,
    RECALLED
}

@Entity(tableName = "batches")
data class Batch(
    @PrimaryKey val batchId: String, // e.g. FT-BATCH-2026-0891
    val productName: String,
    val variety: String,
    val farmOrigin: String,
    val harvestDate: String,
    val quantity: Double,
    val unit: String = "kg",
    val destination: String,
    val currentStage: SupplyChainStage = SupplyChainStage.FARM,
    val status: BatchStatus = BatchStatus.ACTIVE,
    val assignedDeviceId: String? = null,
    // Thresholds
    val tempMin: Double = 2.0,
    val tempMax: Double = 8.0,
    val humidityMin: Double = 85.0,
    val humidityMax: Double = 95.0,
    val maxEthylenePpm: Double = 0.5,
    val createdAt: Long = System.currentTimeMillis()
)
