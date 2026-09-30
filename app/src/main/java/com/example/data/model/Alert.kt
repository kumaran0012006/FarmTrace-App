package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AlertType {
    TEMPERATURE_HIGH,
    TEMPERATURE_LOW,
    HUMIDITY_HIGH,
    HUMIDITY_LOW,
    ETHYLENE_HIGH,
    BATTERY_LOW,
    INTEGRITY_TAMPER,
    DEVICE_DROPOUT
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

@Entity(
    tableName = "alerts",
    indices = [
        Index("batchId"),
        Index("deviceId"),
        Index("timestamp")
    ]
)
data class Alert(
    @PrimaryKey val alertId: String,
    val deviceId: String,
    val batchId: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val measurement: String,
    val threshold: String,
    val actualValue: String,
    val timestamp: Long,
    val acknowledged: Boolean = false,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: Long? = null
)
