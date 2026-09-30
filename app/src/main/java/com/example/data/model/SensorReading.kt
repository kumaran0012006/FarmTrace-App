package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncStatus {
    SYNCED,
    PENDING_SYNC,
    FAILED
}

@Entity(
    tableName = "sensor_readings",
    indices = [
        Index("deviceId"),
        Index("batchId"),
        Index("timestamp"),
        Index("eventId", unique = true)
    ]
)
data class SensorReading(
    @PrimaryKey val eventId: String, // Unique UUID for duplicate detection
    val deviceId: String,
    val batchId: String,
    val timestamp: Long, // UTC timestamp in milliseconds
    val temperature: Double, // Celsius
    val humidity: Double, // % RH
    val ethylene: Double, // ppm
    val battery: Int, // %
    val solarVoltage: Double = 0.0, // Volts
    val solarCurrent: Double = 0.0, // mA
    val solarEnergy: Double = 0.0, // mWh
    val powerConsumption: Double = 0.0, // mW
    val latitude: Double? = null,
    val longitude: Double? = null,
    val signalStrength: Int = -75, // dBm
    val firmwareVersion: String = "v1.4.2",
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    val dataHash: String = "", // SHA-256 of reading
    val rawSignature: String = "" // Cryptographic signature from IoT node
)
