package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MqttTelemetryPayload(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "batch_id") val batchId: String,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "temperature") val temperature: Double,
    @Json(name = "humidity") val humidity: Double,
    @Json(name = "ethylene") val ethylene: Double,
    @Json(name = "battery") val battery: Int,
    @Json(name = "solar_voltage") val solarVoltage: Double? = 0.0,
    @Json(name = "solar_current") val solarCurrent: Double? = 0.0,
    @Json(name = "solar_energy") val solarEnergy: Double? = 0.0,
    @Json(name = "power_consumption") val powerConsumption: Double? = 0.0,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "signal_strength") val signalStrength: Int? = -75,
    @Json(name = "firmware_version") val firmwareVersion: String? = "v1.4.2",
    @Json(name = "signature") val signature: String? = null
)
