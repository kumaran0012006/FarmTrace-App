package com.example.engine

import com.example.data.model.Alert
import com.example.data.model.AlertSeverity
import com.example.data.model.AlertType
import com.example.data.model.Batch
import com.example.data.model.SensorReading
import java.util.UUID

object AlertEngine {

    /**
     * Evaluates a real sensor reading against the assigned batch thresholds.
     * Returns any threshold violations as Alert entities.
     */
    fun evaluateReading(
        reading: SensorReading,
        batch: Batch?
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val now = reading.timestamp

        // Battery health check
        if (reading.battery < 15) {
            alerts.add(
                Alert(
                    alertId = "ALT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    deviceId = reading.deviceId,
                    batchId = reading.batchId,
                    type = AlertType.BATTERY_LOW,
                    severity = if (reading.battery < 5) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                    measurement = "Battery Level",
                    threshold = "< 15%",
                    actualValue = "${reading.battery}%",
                    timestamp = now
                )
            )
        }

        if (batch == null) return alerts

        // Temperature High Excursion
        if (reading.temperature > batch.tempMax) {
            val delta = reading.temperature - batch.tempMax
            val severity = if (delta > 3.0) AlertSeverity.CRITICAL else AlertSeverity.WARNING
            alerts.add(
                Alert(
                    alertId = "ALT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    deviceId = reading.deviceId,
                    batchId = batch.batchId,
                    type = AlertType.TEMPERATURE_HIGH,
                    severity = severity,
                    measurement = "Temperature",
                    threshold = "Max ${batch.tempMax}°C",
                    actualValue = "%.1f°C (+%.1f°C)".format(reading.temperature, delta),
                    timestamp = now
                )
            )
        }

        // Temperature Low Excursion
        if (reading.temperature < batch.tempMin) {
            val delta = batch.tempMin - reading.temperature
            val severity = if (delta > 2.0) AlertSeverity.CRITICAL else AlertSeverity.WARNING
            alerts.add(
                Alert(
                    alertId = "ALT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    deviceId = reading.deviceId,
                    batchId = batch.batchId,
                    type = AlertType.TEMPERATURE_LOW,
                    severity = severity,
                    measurement = "Temperature",
                    threshold = "Min ${batch.tempMin}°C",
                    actualValue = "%.1f°C (-%.1f°C)".format(reading.temperature, delta),
                    timestamp = now
                )
            )
        }

        // Humidity High
        if (reading.humidity > batch.humidityMax) {
            alerts.add(
                Alert(
                    alertId = "ALT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    deviceId = reading.deviceId,
                    batchId = batch.batchId,
                    type = AlertType.HUMIDITY_HIGH,
                    severity = AlertSeverity.WARNING,
                    measurement = "Humidity",
                    threshold = "Max ${batch.humidityMax}% RH",
                    actualValue = "%.1f%% RH".format(reading.humidity),
                    timestamp = now
                )
            )
        }

        // Humidity Low
        if (reading.humidity < batch.humidityMin) {
            alerts.add(
                Alert(
                    alertId = "ALT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    deviceId = reading.deviceId,
                    batchId = batch.batchId,
                    type = AlertType.HUMIDITY_LOW,
                    severity = AlertSeverity.WARNING,
                    measurement = "Humidity",
                    threshold = "Min ${batch.humidityMin}% RH",
                    actualValue = "%.1f%% RH".format(reading.humidity),
                    timestamp = now
                )
            )
        }

        // Ethylene Gas Surge (triggers premature ripening / decay)
        if (reading.ethylene > batch.maxEthylenePpm) {
            val delta = reading.ethylene - batch.maxEthylenePpm
            val severity = if (delta > 0.5) AlertSeverity.CRITICAL else AlertSeverity.WARNING
            alerts.add(
                Alert(
                    alertId = "ALT-${UUID.randomUUID().toString().take(8).uppercase()}",
                    deviceId = reading.deviceId,
                    batchId = batch.batchId,
                    type = AlertType.ETHYLENE_HIGH,
                    severity = severity,
                    measurement = "Ethylene Gas",
                    threshold = "Max ${batch.maxEthylenePpm} ppm",
                    actualValue = "%.3f ppm".format(reading.ethylene),
                    timestamp = now
                )
            )
        }

        return alerts
    }
}
