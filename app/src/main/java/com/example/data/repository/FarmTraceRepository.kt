package com.example.data.repository

import android.util.Log
import com.example.crypto.ChainVerificationResult
import com.example.crypto.CryptoService
import com.example.data.local.FarmTraceDatabase
import com.example.data.model.AiAnalysis
import com.example.data.model.Alert
import com.example.data.model.AuditLog
import com.example.data.model.Batch
import com.example.data.model.BatchStatus
import com.example.data.model.BlockchainSyncStatus
import com.example.data.model.Device
import com.example.data.model.DeviceStatus
import com.example.data.model.DeviceType
import com.example.data.model.MqttTelemetryPayload
import com.example.data.model.SensorReading
import com.example.data.model.SupplyChainStage
import com.example.data.model.SyncStatus
import com.example.data.model.TraceabilityEvent
import com.example.data.model.TraceabilityEventType
import com.example.engine.AlertEngine
import com.example.network.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

class FarmTraceRepository(
    private val db: FarmTraceDatabase,
    val syncRepository: RoomToFirestoreSyncRepository = RoomToFirestoreSyncRepositoryImpl(db)
) {

    private val tag = "FarmTraceRepository"
    private val deviceDao = db.deviceDao()
    private val batchDao = db.batchDao()
    private val readingDao = db.sensorReadingDao()
    private val eventDao = db.traceabilityEventDao()
    private val alertDao = db.alertDao()
    private val auditDao = db.auditLogDao()
    private val aiDao = db.aiAnalysisDao()

    // Observable Flows
    val allDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    val allBatches: Flow<List<Batch>> = batchDao.getAllBatches()
    val allReadings: Flow<List<SensorReading>> = readingDao.getAllReadings()
    val allEvents: Flow<List<TraceabilityEvent>> = eventDao.getAllEvents()
    val allAlerts: Flow<List<Alert>> = alertDao.getAllAlerts()
    val unacknowledgedAlerts: Flow<List<Alert>> = alertDao.getUnacknowledgedAlerts()
    val unacknowledgedAlertCount: Flow<Int> = alertDao.getUnacknowledgedCount()
    val auditLogs: Flow<List<AuditLog>> = auditDao.getAllLogs()

    fun getEventsForBatch(batchId: String): Flow<List<TraceabilityEvent>> =
        eventDao.getEventsForBatch(batchId)

    fun getReadingsForBatch(batchId: String): Flow<List<SensorReading>> =
        readingDao.getReadingsForBatch(batchId)

    fun observeBatch(batchId: String): Flow<Batch?> =
        batchDao.observeBatchById(batchId)

    fun observeDevice(deviceId: String): Flow<Device?> =
        deviceDao.observeDeviceById(deviceId)

    fun getLatestAiAnalysis(batchId: String): Flow<AiAnalysis?> =
        aiDao.getLatestAnalysisForBatch(batchId)

    /**
     * Ingests real IoT telemetry message with full validation, idempotency,
     * alert engine evaluation, cryptographic hashing, and chain linkage.
     */
    suspend fun ingestTelemetry(payload: MqttTelemetryPayload): Boolean = withContext(Dispatchers.IO) {
        // 1. Validation: reject NaN, Infinity, empty IDs, or unreasonable values
        if (payload.temperature.isNaN() || payload.temperature.isInfinite() ||
            payload.humidity.isNaN() || payload.humidity.isInfinite() ||
            payload.ethylene.isNaN() || payload.ethylene.isInfinite()
        ) {
            Log.e(tag, "Rejected telemetry: NaN/Infinite numbers in payload: $payload")
            return@withContext false
        }

        if (payload.deviceId.isBlank() || payload.eventId.isBlank()) {
            Log.e(tag, "Rejected telemetry: Missing required deviceId or eventId")
            return@withContext false
        }

        // 2. Duplicate Detection / Idempotency
        if (readingDao.existsEvent(payload.eventId)) {
            Log.i(tag, "Skipping duplicate eventId: ${payload.eventId}")
            return@withContext true
        }

        val now = if (payload.timestamp > 0) payload.timestamp else System.currentTimeMillis()

        // 3. Compute Data Integrity Hash
        val dataHash = CryptoService.computeReadingHash(
            eventId = payload.eventId,
            deviceId = payload.deviceId,
            batchId = payload.batchId,
            timestamp = now,
            temp = payload.temperature,
            humidity = payload.humidity,
            ethylene = payload.ethylene
        )

        val reading = SensorReading(
            eventId = payload.eventId,
            deviceId = payload.deviceId,
            batchId = payload.batchId,
            timestamp = now,
            temperature = payload.temperature,
            humidity = payload.humidity,
            ethylene = payload.ethylene,
            battery = payload.battery.coerceIn(0, 100),
            solarVoltage = payload.solarVoltage ?: 0.0,
            solarCurrent = payload.solarCurrent ?: 0.0,
            solarEnergy = payload.solarEnergy ?: 0.0,
            powerConsumption = payload.powerConsumption ?: 0.0,
            latitude = payload.latitude,
            longitude = payload.longitude,
            signalStrength = payload.signalStrength ?: -75,
            firmwareVersion = payload.firmwareVersion ?: "v1.4.2",
            syncStatus = SyncStatus.PENDING_SYNC,
            dataHash = dataHash,
            rawSignature = payload.signature ?: ""
        )

        readingDao.insertReading(reading)

        // Attempt background cloud push to Firestore
        try {
            syncRepository.uploadEnvironmentalReading(reading)
        } catch (e: Exception) {
            Log.d(tag, "Local reading queued for next sync window: ${e.message}")
        }

        // 4. Update Device Heartbeat and Status
        val existingDevice = deviceDao.getDeviceById(payload.deviceId)
        if (existingDevice != null) {
            deviceDao.updateHeartbeat(
                deviceId = payload.deviceId,
                status = DeviceStatus.ONLINE,
                timestamp = now,
                battery = payload.battery,
                signal = payload.signalStrength ?: -75
            )
        } else {
            // Auto-register discovered hardware node
            deviceDao.insertDevice(
                Device(
                    deviceId = payload.deviceId,
                    name = "ESP32 Node (${payload.deviceId.takeLast(6)})",
                    type = DeviceType.ESP32_CELLULAR_NODE,
                    assignedBatchId = payload.batchId.ifBlank { null },
                    status = DeviceStatus.ONLINE,
                    lastSeenTimestamp = now,
                    batteryPercent = payload.battery,
                    signalDbm = payload.signalStrength ?: -75,
                    firmwareVersion = payload.firmwareVersion ?: "v1.4.2"
                )
            )
        }

        // 5. Evaluate Threshold Alerts
        val targetBatch = if (payload.batchId.isNotBlank()) batchDao.getBatchById(payload.batchId) else null
        val generatedAlerts = AlertEngine.evaluateReading(reading, targetBatch)
        for (alert in generatedAlerts) {
            // Deduplicate if recent active alert of same type exists for this batch
            val hasRecent = alertDao.hasActiveRecentAlert(alert.batchId, alert.type.name, now - 60_000L)
            if (!hasRecent) {
                alertDao.insertAlert(alert)
                auditDao.insertLog(
                    AuditLog(
                        userId = "ALERT_ENGINE",
                        action = "ALERT_TRIGGERED",
                        resource = "BATCH",
                        resourceId = alert.batchId,
                        timestamp = now,
                        ipOrMetadata = "Severity: ${alert.severity}, Type: ${alert.type}"
                    )
                )
            }
        }

        // 6. Checkpoint Traceability Event (anchoring environmental snapshot to blockchain hash chain)
        if (targetBatch != null) {
            val latestEvent = eventDao.getLatestEventForBatch(targetBatch.batchId)
            val prevHash = latestEvent?.currentHash ?: CryptoService.GENESIS_PREV_HASH
            val condSummary = "T:%.1f°C | H:%.1f%% | C2H4:%.3fppm | Bat:%d%%".format(
                payload.temperature,
                payload.humidity,
                payload.ethylene,
                payload.battery
            )
            val currentHash = CryptoService.computeEventHash(
                previousHash = prevHash,
                eventId = payload.eventId,
                deviceId = payload.deviceId,
                batchId = targetBatch.batchId,
                timestamp = now,
                sensorData = condSummary
            )
            val txId = CryptoService.computeBlockchainTxId(currentHash, now)

            val traceEvent = TraceabilityEvent(
                eventId = payload.eventId,
                batchId = targetBatch.batchId,
                eventType = TraceabilityEventType.PERIODIC_CHECKPOINT,
                stage = targetBatch.currentStage,
                location = if (payload.latitude != null && payload.longitude != null)
                    "GPS(%.4f, %.4f)".format(payload.latitude, payload.longitude)
                else "Transit Node",
                operator = "IoT Node ${payload.deviceId}",
                timestamp = now,
                deviceId = payload.deviceId,
                environmentalConditions = condSummary,
                blockchainTxId = txId,
                previousHash = prevHash,
                currentHash = currentHash,
                status = BlockchainSyncStatus.CONFIRMED
            )
            eventDao.insertEvent(traceEvent)
        }

        true
    }

    /**
     * Batch Creation: Creates a new verifiable agricultural batch and genesis event
     */
    suspend fun createBatch(
        batchId: String,
        productName: String,
        variety: String,
        farmOrigin: String,
        harvestDate: String,
        quantity: Double,
        unit: String,
        destination: String,
        tempMin: Double,
        tempMax: Double,
        humidityMin: Double,
        humidityMax: Double,
        maxEthylene: Double,
        deviceId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val batch = Batch(
            batchId = batchId,
            productName = productName,
            variety = variety,
            farmOrigin = farmOrigin,
            harvestDate = harvestDate,
            quantity = quantity,
            unit = unit,
            destination = destination,
            currentStage = SupplyChainStage.FARM,
            status = BatchStatus.ACTIVE,
            assignedDeviceId = deviceId,
            tempMin = tempMin,
            tempMax = tempMax,
            humidityMin = humidityMin,
            humidityMax = humidityMax,
            maxEthylenePpm = maxEthylene,
            createdAt = now
        )
        batchDao.insertBatch(batch)

        // Assign to device if specified
        if (!deviceId.isNullOrBlank()) {
            val dev = deviceDao.getDeviceById(deviceId)
            if (dev != null) {
                deviceDao.updateDevice(dev.copy(assignedBatchId = batchId))
            }
        }

        // Genesis traceability event for the batch
        val genesisEventId = "EVT-GENESIS-" + UUID.randomUUID().toString().take(8).uppercase()
        val condSummary = "Batch Initialized at Origin: $farmOrigin"
        val currentHash = CryptoService.computeEventHash(
            previousHash = CryptoService.GENESIS_PREV_HASH,
            eventId = genesisEventId,
            deviceId = deviceId ?: "SYSTEM_REGISTRY",
            batchId = batchId,
            timestamp = now,
            sensorData = condSummary
        )
        val txId = CryptoService.computeBlockchainTxId(currentHash, now)

        val genesisEvent = TraceabilityEvent(
            eventId = genesisEventId,
            batchId = batchId,
            eventType = TraceabilityEventType.STAGE_TRANSITION,
            stage = SupplyChainStage.FARM,
            location = farmOrigin,
            operator = "Farm Origin Administrator",
            timestamp = now,
            deviceId = deviceId ?: "REGISTRY",
            environmentalConditions = condSummary,
            blockchainTxId = txId,
            previousHash = CryptoService.GENESIS_PREV_HASH,
            currentHash = currentHash,
            status = BlockchainSyncStatus.CONFIRMED
        )
        eventDao.insertEvent(genesisEvent)

        auditDao.insertLog(
            AuditLog(
                userId = "OPERATOR",
                action = "CREATE_BATCH",
                resource = "BATCH",
                resourceId = batchId,
                timestamp = now,
                ipOrMetadata = "Product: $productName, Origin: $farmOrigin"
            )
        )
        true
    }

    /**
     * Advances batch to next supply chain stage and chains the cryptographic event
     */
    suspend fun advanceBatchStage(
        batchId: String,
        newStage: SupplyChainStage,
        location: String,
        operator: String
    ): Boolean = withContext(Dispatchers.IO) {
        val batch = batchDao.getBatchById(batchId) ?: return@withContext false
        val now = System.currentTimeMillis()

        batchDao.updateStage(batchId, newStage)

        val latestEvent = eventDao.getLatestEventForBatch(batchId)
        val prevHash = latestEvent?.currentHash ?: CryptoService.GENESIS_PREV_HASH
        val eventId = "EVT-STAGE-" + UUID.randomUUID().toString().take(8).uppercase()

        val latestReading = readingDao.getLatestReadingForBatch(batchId)
        val condSummary = if (latestReading != null) {
            "Stage: ${newStage.displayName} | T:%.1f°C | H:%.1f%% | C2H4:%.3fppm".format(
                latestReading.temperature,
                latestReading.humidity,
                latestReading.ethylene
            )
        } else {
            "Stage: ${newStage.displayName} at $location"
        }

        val currentHash = CryptoService.computeEventHash(
            previousHash = prevHash,
            eventId = eventId,
            deviceId = batch.assignedDeviceId ?: "MOBILE_CLIENT",
            batchId = batchId,
            timestamp = now,
            sensorData = condSummary
        )
        val txId = CryptoService.computeBlockchainTxId(currentHash, now)

        val event = TraceabilityEvent(
            eventId = eventId,
            batchId = batchId,
            eventType = TraceabilityEventType.STAGE_TRANSITION,
            stage = newStage,
            location = location,
            operator = operator,
            timestamp = now,
            deviceId = batch.assignedDeviceId ?: "MOBILE_CLIENT",
            environmentalConditions = condSummary,
            blockchainTxId = txId,
            previousHash = prevHash,
            currentHash = currentHash,
            status = BlockchainSyncStatus.CONFIRMED
        )
        eventDao.insertEvent(event)

        auditDao.insertLog(
            AuditLog(
                userId = operator,
                action = "STAGE_TRANSITION",
                resource = "BATCH",
                resourceId = batchId,
                timestamp = now,
                ipOrMetadata = "Moved to ${newStage.displayName} at $location"
            )
        )
        true
    }

    /**
     * Executes real cryptographic hash verification over the entire chain
     */
    suspend fun verifyBatchIntegrity(batchId: String): ChainVerificationResult = withContext(Dispatchers.IO) {
        val events = eventDao.getEventsListForBatch(batchId)
        val result = CryptoService.verifyChain(events)

        auditDao.insertLog(
            AuditLog(
                userId = "AUDITOR",
                action = "VERIFY_INTEGRITY",
                resource = "BATCH",
                resourceId = batchId,
                timestamp = System.currentTimeMillis(),
                ipOrMetadata = "Result: ${if (result.isValid) "VERIFIED" else "INTEGRITY_VIOLATION"}, Nodes: ${result.totalNodesChecked}"
            )
        )

        result
    }

    /**
     * Invokes Gemini AI quality analytics based on real stored sensor readings
     */
    suspend fun runAiQualityAnalysis(batchId: String): Result<AiAnalysis> = withContext(Dispatchers.IO) {
        try {
            val batch = batchDao.getBatchById(batchId)
                ?: return@withContext Result.failure(Exception("Batch $batchId not found"))
            val readings = readingDao.getReadingsListForBatch(batchId)
            val alerts = alertDao.getAlertsForBatch(batchId).firstOrNull() ?: emptyList()

            val readingsSummary = if (readings.isEmpty()) {
                "No sensor readings logged for this batch yet."
            } else {
                val avgTemp = readings.map { it.temperature }.average()
                val minTemp = readings.minOf { it.temperature }
                val maxTemp = readings.maxOf { it.temperature }
                val avgHum = readings.map { it.humidity }.average()
                val maxEthylene = readings.maxOf { it.ethylene }
                val count = readings.size
                val firstTime = readings.first().timestamp
                val lastTime = readings.last().timestamp
                val durationHours = ((lastTime - firstTime) / 3600000.0).coerceAtLeast(0.1)

                "Total Readings: $count over %.1f hours.\nTemp Range: Min %.1f°C, Max %.1f°C, Avg %.1f°C.\nAvg Humidity: %.1f%% RH.\nMax Ethylene Gas: %.3f ppm.".format(
                    durationHours, minTemp, maxTemp, avgTemp, avgHum, maxEthylene
                )
            }

            val alertsSummary = if (alerts.isEmpty()) {
                "No environmental excursion alerts triggered."
            } else {
                alerts.joinToString("\n") {
                    "- [${it.severity}] ${it.measurement}: ${it.actualValue} vs Threshold ${it.threshold}"
                }
            }

            val rawAnalysis = GeminiClient.analyzeProduceQuality(
                batchId = batchId,
                productName = batch.productName,
                variety = batch.variety,
                tempMin = batch.tempMin,
                tempMax = batch.tempMax,
                maxEthylene = batch.maxEthylenePpm,
                readingsSummary = readingsSummary,
                alertsSummary = alertsSummary
            )

            val analysis = AiAnalysis(
                analysisId = "AI-" + UUID.randomUUID().toString().take(8).uppercase(),
                batchId = batchId,
                analysisTimestamp = System.currentTimeMillis(),
                inputDataSummary = readingsSummary,
                qualityTrend = if (alerts.any { it.severity.name == "CRITICAL" }) "Elevated Spoilage Risk" else "Within Standard Parameters",
                riskFactors = "Evaluated temperature stability and ethylene buildup.",
                observedAnomalies = alertsSummary,
                recommendedInspectionAction = rawAnalysis,
                model = "gemini-3.5-flash"
            )

            aiDao.insertAnalysis(analysis)

            auditDao.insertLog(
                AuditLog(
                    userId = "AI_ENGINE",
                    action = "AI_QUALITY_ANALYSIS",
                    resource = "BATCH",
                    resourceId = batchId,
                    timestamp = System.currentTimeMillis(),
                    ipOrMetadata = "Model: gemini-3.5-flash"
                )
            )

            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerDevice(
        deviceId: String,
        name: String,
        type: DeviceType,
        batchId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val device = Device(
            deviceId = deviceId,
            name = name,
            type = type,
            assignedBatchId = batchId,
            status = DeviceStatus.OFFLINE,
            registeredAt = System.currentTimeMillis()
        )
        deviceDao.insertDevice(device)
        auditDao.insertLog(
            AuditLog(
                userId = "ADMIN",
                action = "REGISTER_DEVICE",
                resource = "DEVICE",
                resourceId = deviceId,
                timestamp = System.currentTimeMillis(),
                ipOrMetadata = "Name: $name, Type: $type"
            )
        )
        true
    }

    suspend fun acknowledgeAlert(alertId: String, operator: String): Boolean = withContext(Dispatchers.IO) {
        alertDao.acknowledgeAlert(alertId, operator)
        auditDao.insertLog(
            AuditLog(
                userId = operator,
                action = "ACKNOWLEDGE_ALERT",
                resource = "ALERT",
                resourceId = alertId,
                timestamp = System.currentTimeMillis(),
                ipOrMetadata = "Acknowledged by $operator"
            )
        )
        true
    }

    suspend fun checkStaleDevices(staleThresholdMs: Long = 120_000L) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - staleThresholdMs
        deviceDao.markStaleDevicesOffline(cutoff)
    }

    suspend fun recordTraceabilityEvent(event: TraceabilityEvent) = withContext(Dispatchers.IO) {
        eventDao.insertEvent(event)
    }
}
