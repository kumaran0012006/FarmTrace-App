package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeviceStatus {
    ONLINE,
    OFFLINE,
    WARNING,
    ERROR
}

enum class DeviceType {
    ESP32_LORA_NODE,
    ESP32_CELLULAR_NODE,
    ESP32_WIFI_GATEWAY,
    BLE_BEACON_NODE
}

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val deviceId: String,
    val name: String,
    val type: DeviceType = DeviceType.ESP32_CELLULAR_NODE,
    val assignedBatchId: String? = null,
    val status: DeviceStatus = DeviceStatus.OFFLINE,
    val lastSeenTimestamp: Long = 0L,
    val batteryPercent: Int = 100,
    val signalDbm: Int = -75,
    val firmwareVersion: String = "v1.4.2",
    val authTokenHash: String = "",
    val registeredAt: Long = System.currentTimeMillis()
)
