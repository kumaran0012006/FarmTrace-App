package com.example.data.repository

import android.util.Log
import com.example.data.local.FarmTraceDatabase
import com.example.data.model.Alert
import com.example.data.model.AlertSeverity
import com.example.data.model.AlertType
import com.example.data.model.Batch
import com.example.data.model.BatchStatus
import com.example.data.model.BlockchainSyncStatus
import com.example.data.model.SensorReading
import com.example.data.model.SupplyChainStage
import com.example.data.model.SyncStatus
import com.example.data.model.TraceabilityEvent
import com.example.data.model.TraceabilityEventType
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RoomToFirestoreSyncRepositoryImpl(
    private val db: FarmTraceDatabase,
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w("RoomToFirestore", "FirebaseFirestore instance unavailable: ${e.message}")
            null
        }
    }
) : RoomToFirestoreSyncRepository {

    companion object {
        private const val TAG = "RoomToFirestoreSync"
        const val COLLECTION_BATCHES = "farm_batches"
        const val COLLECTION_TELEMETRY = "environmental_telemetry"
        const val COLLECTION_EVENTS = "traceability_chain"
        const val COLLECTION_ALERTS = "environmental_alerts"
    }

    private val readingDao = db.sensorReadingDao()
    private val batchDao = db.batchDao()
    private val eventDao = db.traceabilityEventDao()
    private val alertDao = db.alertDao()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus())
    override val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Monitor pending count in Room
        repositoryScope.launch {
            try {
                readingDao.observePendingSyncCount(SyncStatus.PENDING_SYNC).collect { count ->
                    _syncStatus.update { it.copy(pendingReadingsCount = count) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed observing pending count: ${e.message}")
            }
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        return firestoreProvider()
    }

    override suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
        if (firestore == null) {
            val msg = "Cloud Firestore is not initialized. Ensure Google Services are configured."
            _syncStatus.update { it.copy(isCloudConnected = false, lastError = msg) }
            return@withContext SyncResult(success = false, message = msg)
        }

        _syncStatus.update { it.copy(isSyncing = true, lastError = null) }

        var totalReadings = 0
        var totalBatches = 0
        var totalEvents = 0
        var totalAlerts = 0

        try {
            // 1. Sync Batches (Farm-to-Fork Lots)
            val batchResult = syncBatches()
            totalBatches += batchResult.syncedBatches

            // 2. Sync Local Environmental Telemetry
            val telemetryResult = syncEnvironmentalReadings()
            totalReadings += telemetryResult.syncedReadings

            // 3. Sync Blockchain Traceability Events
            val eventResult = syncTraceabilityEvents()
            totalEvents += eventResult.syncedEvents

            // 4. Sync Environmental Alerts
            val alertResult = syncAlerts()
            totalAlerts += alertResult.syncedAlerts

            val now = System.currentTimeMillis()
            val summary = "Synced $totalReadings readings, $totalBatches lots, $totalEvents proof events to Firestore"

            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    lastSyncTimestamp = now,
                    totalSyncedReadings = it.totalSyncedReadings + totalReadings,
                    isCloudConnected = true,
                    lastSyncSummary = summary,
                    lastError = null
                )
            }

            SyncResult(
                success = true,
                syncedReadings = totalReadings,
                syncedBatches = totalBatches,
                syncedEvents = totalEvents,
                syncedAlerts = totalAlerts,
                message = summary
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing full sync to Firestore", e)
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    lastError = e.localizedMessage ?: "Unknown Firestore sync error"
                )
            }
            SyncResult(
                success = false,
                syncedReadings = totalReadings,
                syncedBatches = totalBatches,
                syncedEvents = totalEvents,
                syncedAlerts = totalAlerts,
                message = "Sync failed: ${e.message}",
                error = e
            )
        }
    }

    override suspend fun syncEnvironmentalReadings(batchId: String?): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext SyncResult(
            success = false,
            message = "Firestore not initialized"
        )

        try {
            val pendingReadings = if (batchId.isNullOrBlank()) {
                readingDao.getReadingsBySyncStatus(SyncStatus.PENDING_SYNC, limit = 150)
            } else {
                readingDao.getReadingsBySyncStatusForBatch(SyncStatus.PENDING_SYNC, batchId, limit = 150)
            }

            if (pendingReadings.isEmpty()) {
                return@withContext SyncResult(
                    success = true,
                    syncedReadings = 0,
                    message = "No pending environmental readings to sync"
                )
            }

            val telemetryCollection = firestore.collection(COLLECTION_TELEMETRY)
            val successfulEventIds = mutableListOf<String>()

            for (reading in pendingReadings) {
                try {
                    val map = readingToMap(reading)
                    telemetryCollection.document(reading.eventId)
                        .set(map, SetOptions.merge())
                        .awaitTask()
                    successfulEventIds.add(reading.eventId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing reading ${reading.eventId}: ${e.message}")
                    readingDao.updateSyncStatus(reading.eventId, SyncStatus.FAILED)
                }
            }

            if (successfulEventIds.isNotEmpty()) {
                readingDao.updateBatchSyncStatus(successfulEventIds, SyncStatus.SYNCED)
            }

            SyncResult(
                success = true,
                syncedReadings = successfulEventIds.size,
                message = "Uploaded ${successfulEventIds.size} environmental readings to cloud Firestore"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed synchronizing environmental readings", e)
            SyncResult(
                success = false,
                message = "Failed syncing readings: ${e.message}",
                error = e
            )
        }
    }

    override suspend fun syncBatches(): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext SyncResult(
            success = false,
            message = "Firestore not initialized"
        )

        try {
            val batches = batchDao.getAllBatchesList()
            val batchCollection = firestore.collection(COLLECTION_BATCHES)
            var count = 0

            for (batch in batches) {
                val data = batchToMap(batch)
                batchCollection.document(batch.batchId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                count++
            }

            SyncResult(
                success = true,
                syncedBatches = count,
                message = "Synchronized $count farm-to-fork batches with Firestore"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing batches to Firestore", e)
            SyncResult(
                success = false,
                message = "Failed syncing batches: ${e.message}",
                error = e
            )
        }
    }

    override suspend fun syncTraceabilityEvents(batchId: String?): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext SyncResult(
            success = false,
            message = "Firestore not initialized"
        )

        try {
            val events = if (batchId.isNullOrBlank()) {
                eventDao.getEventsListForBatch(batchId = "") // fallback or all
            } else {
                eventDao.getEventsListForBatch(batchId)
            }

            // If empty batch filter or need all, query all events
            val targetEvents = if (events.isEmpty() && batchId.isNullOrBlank()) {
                // Fetch first batch of events
                val allEventsList = mutableListOf<TraceabilityEvent>()
                // Querying batches to get their events
                val allBatches = batchDao.getAllBatchesList()
                for (b in allBatches) {
                    allEventsList.addAll(eventDao.getEventsListForBatch(b.batchId))
                }
                allEventsList
            } else {
                events
            }

            val eventsCollection = firestore.collection(COLLECTION_EVENTS)
            var count = 0

            for (event in targetEvents) {
                val data = eventToMap(event)
                eventsCollection.document(event.eventId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                count++
            }

            SyncResult(
                success = true,
                syncedEvents = count,
                message = "Synchronized $count cryptographic proof events to cloud ledger"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing traceability events to Firestore", e)
            SyncResult(
                success = false,
                message = "Failed syncing events: ${e.message}",
                error = e
            )
        }
    }

    override suspend fun syncAlerts(): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext SyncResult(
            success = false,
            message = "Firestore not initialized"
        )

        try {
            val alerts = alertDao.getAllAlertsList()
            val alertCollection = firestore.collection(COLLECTION_ALERTS)
            var count = 0

            for (alert in alerts) {
                val data = alertToMap(alert)
                alertCollection.document(alert.alertId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                count++
            }

            SyncResult(
                success = true,
                syncedAlerts = count,
                message = "Synchronized $count excursion alerts to Firestore"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing alerts to Firestore", e)
            SyncResult(
                success = false,
                message = "Failed syncing alerts: ${e.message}",
                error = e
            )
        }
    }

    override suspend fun uploadEnvironmentalReading(reading: SensorReading): Result<String> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(
            IllegalStateException("Firestore client is uninitialized")
        )

        try {
            val map = readingToMap(reading)
            firestore.collection(COLLECTION_TELEMETRY)
                .document(reading.eventId)
                .set(map, SetOptions.merge())
                .awaitTask()

            // Update local Room status
            readingDao.updateSyncStatus(reading.eventId, SyncStatus.SYNCED)
            _syncStatus.update { it.copy(totalSyncedReadings = it.totalSyncedReadings + 1) }

            Result.success(reading.eventId)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading reading ${reading.eventId}", e)
            readingDao.updateSyncStatus(reading.eventId, SyncStatus.FAILED)
            Result.failure(e)
        }
    }

    override suspend fun uploadBatch(batch: Batch): Result<String> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(
            IllegalStateException("Firestore client is uninitialized")
        )

        try {
            val map = batchToMap(batch)
            firestore.collection(COLLECTION_BATCHES)
                .document(batch.batchId)
                .set(map, SetOptions.merge())
                .awaitTask()

            Result.success(batch.batchId)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading batch ${batch.batchId}", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadTraceabilityEvent(event: TraceabilityEvent): Result<String> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(
            IllegalStateException("Firestore client is uninitialized")
        )

        try {
            val map = eventToMap(event)
            firestore.collection(COLLECTION_EVENTS)
                .document(event.eventId)
                .set(map, SetOptions.merge())
                .awaitTask()

            Result.success(event.eventId)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading traceability event ${event.eventId}", e)
            Result.failure(e)
        }
    }

    override suspend fun pullRemoteEnvironmentalReadings(batchId: String, limit: Int): Result<List<SensorReading>> =
        withContext(Dispatchers.IO) {
            val firestore = getFirestore() ?: return@withContext Result.failure(
                IllegalStateException("Firestore client is uninitialized")
            )

            try {
                val snapshot = firestore.collection(COLLECTION_TELEMETRY)
                    .whereEqualTo("batchId", batchId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .awaitTask()

                val readings = snapshot.documents.mapNotNull { docToReading(it) }
                if (readings.isNotEmpty()) {
                    readingDao.upsertAll(readings)
                }

                Result.success(readings)
            } catch (e: Exception) {
                Log.e(TAG, "Error pulling remote readings for $batchId", e)
                Result.failure(e)
            }
        }

    override suspend fun pullRemoteBatches(): Result<List<Batch>> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(
            IllegalStateException("Firestore client is uninitialized")
        )

        try {
            val snapshot = firestore.collection(COLLECTION_BATCHES)
                .get()
                .awaitTask()

            val batches = snapshot.documents.mapNotNull { docToBatch(it) }
            if (batches.isNotEmpty()) {
                batchDao.insertAll(batches)
            }

            Result.success(batches)
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling remote batches", e)
            Result.failure(e)
        }
    }

    override suspend fun pullRemoteTraceabilityEvents(batchId: String): Result<List<TraceabilityEvent>> =
        withContext(Dispatchers.IO) {
            val firestore = getFirestore() ?: return@withContext Result.failure(
                IllegalStateException("Firestore client is uninitialized")
            )

            try {
                val snapshot = firestore.collection(COLLECTION_EVENTS)
                    .whereEqualTo("batchId", batchId)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .get()
                    .awaitTask()

                val events = snapshot.documents.mapNotNull { docToEvent(it) }
                if (events.isNotEmpty()) {
                    eventDao.insertAll(events)
                }

                Result.success(events)
            } catch (e: Exception) {
                Log.e(TAG, "Error pulling remote traceability events for $batchId", e)
                Result.failure(e)
            }
        }

    override fun startRealtimeBatchListener(batchId: String) {
        val firestore = getFirestore() ?: return
        try {
            val registration = firestore.collection(COLLECTION_TELEMETRY)
                .whereEqualTo("batchId", batchId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Realtime listener error for batch $batchId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        repositoryScope.launch {
                            val newReadings = snapshot.documents.mapNotNull { docToReading(it) }
                            if (newReadings.isNotEmpty()) {
                                readingDao.upsertAll(newReadings)
                            }
                        }
                    }
                }
            activeListeners.add(registration)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start realtime listener: ${e.message}")
        }
    }

    override fun stopRealtimeListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    override fun observePendingSyncCount(): Flow<Int> {
        return readingDao.observePendingSyncCount(SyncStatus.PENDING_SYNC)
    }

    // --- Entity <-> Firestore Document Mappers ---

    private fun readingToMap(reading: SensorReading): Map<String, Any?> {
        return mapOf(
            "eventId" to reading.eventId,
            "deviceId" to reading.deviceId,
            "batchId" to reading.batchId,
            "timestamp" to reading.timestamp,
            "temperature" to reading.temperature,
            "humidity" to reading.humidity,
            "ethylene" to reading.ethylene,
            "battery" to reading.battery,
            "solarVoltage" to reading.solarVoltage,
            "solarCurrent" to reading.solarCurrent,
            "solarEnergy" to reading.solarEnergy,
            "powerConsumption" to reading.powerConsumption,
            "latitude" to reading.latitude,
            "longitude" to reading.longitude,
            "signalStrength" to reading.signalStrength,
            "firmwareVersion" to reading.firmwareVersion,
            "dataHash" to reading.dataHash,
            "rawSignature" to reading.rawSignature,
            "syncStatus" to "SYNCED",
            "lastSyncedAt" to System.currentTimeMillis()
        )
    }

    private fun docToReading(doc: DocumentSnapshot): SensorReading? {
        val eventId = doc.getString("eventId") ?: doc.id
        val deviceId = doc.getString("deviceId") ?: return null
        val batchId = doc.getString("batchId") ?: ""
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val temp = doc.getDouble("temperature") ?: 0.0
        val hum = doc.getDouble("humidity") ?: 0.0
        val ethylene = doc.getDouble("ethylene") ?: 0.0
        val battery = doc.getLong("battery")?.toInt() ?: 100

        return SensorReading(
            eventId = eventId,
            deviceId = deviceId,
            batchId = batchId,
            timestamp = timestamp,
            temperature = temp,
            humidity = hum,
            ethylene = ethylene,
            battery = battery,
            solarVoltage = doc.getDouble("solarVoltage") ?: 0.0,
            solarCurrent = doc.getDouble("solarCurrent") ?: 0.0,
            solarEnergy = doc.getDouble("solarEnergy") ?: 0.0,
            powerConsumption = doc.getDouble("powerConsumption") ?: 0.0,
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            signalStrength = doc.getLong("signalStrength")?.toInt() ?: -75,
            firmwareVersion = doc.getString("firmwareVersion") ?: "v1.4.2",
            syncStatus = SyncStatus.SYNCED,
            dataHash = doc.getString("dataHash") ?: "",
            rawSignature = doc.getString("rawSignature") ?: ""
        )
    }

    private fun batchToMap(batch: Batch): Map<String, Any?> {
        return mapOf(
            "batchId" to batch.batchId,
            "productName" to batch.productName,
            "variety" to batch.variety,
            "farmOrigin" to batch.farmOrigin,
            "harvestDate" to batch.harvestDate,
            "quantity" to batch.quantity,
            "unit" to batch.unit,
            "destination" to batch.destination,
            "currentStage" to batch.currentStage.name,
            "status" to batch.status.name,
            "assignedDeviceId" to batch.assignedDeviceId,
            "tempMin" to batch.tempMin,
            "tempMax" to batch.tempMax,
            "humidityMin" to batch.humidityMin,
            "humidityMax" to batch.humidityMax,
            "maxEthylenePpm" to batch.maxEthylenePpm,
            "createdAt" to batch.createdAt,
            "lastSyncedAt" to System.currentTimeMillis()
        )
    }

    private fun docToBatch(doc: DocumentSnapshot): Batch? {
        val batchId = doc.getString("batchId") ?: doc.id
        val productName = doc.getString("productName") ?: return null
        val variety = doc.getString("variety") ?: ""
        val farmOrigin = doc.getString("farmOrigin") ?: ""
        val harvestDate = doc.getString("harvestDate") ?: ""
        val quantity = doc.getDouble("quantity") ?: 0.0
        val unit = doc.getString("unit") ?: "kg"
        val destination = doc.getString("destination") ?: ""

        val stageName = doc.getString("currentStage") ?: SupplyChainStage.FARM.name
        val stage = try {
            SupplyChainStage.valueOf(stageName)
        } catch (_: Exception) {
            SupplyChainStage.FARM
        }

        val statusName = doc.getString("status") ?: BatchStatus.ACTIVE.name
        val status = try {
            BatchStatus.valueOf(statusName)
        } catch (_: Exception) {
            BatchStatus.ACTIVE
        }

        return Batch(
            batchId = batchId,
            productName = productName,
            variety = variety,
            farmOrigin = farmOrigin,
            harvestDate = harvestDate,
            quantity = quantity,
            unit = unit,
            destination = destination,
            currentStage = stage,
            status = status,
            assignedDeviceId = doc.getString("assignedDeviceId"),
            tempMin = doc.getDouble("tempMin") ?: 2.0,
            tempMax = doc.getDouble("tempMax") ?: 8.0,
            humidityMin = doc.getDouble("humidityMin") ?: 85.0,
            humidityMax = doc.getDouble("humidityMax") ?: 95.0,
            maxEthylenePpm = doc.getDouble("maxEthylenePpm") ?: 0.5,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun eventToMap(event: TraceabilityEvent): Map<String, Any?> {
        return mapOf(
            "eventId" to event.eventId,
            "batchId" to event.batchId,
            "eventType" to event.eventType.name,
            "stage" to event.stage.name,
            "location" to event.location,
            "operator" to event.operator,
            "timestamp" to event.timestamp,
            "deviceId" to event.deviceId,
            "environmentalConditions" to event.environmentalConditions,
            "blockchainTxId" to event.blockchainTxId,
            "previousHash" to event.previousHash,
            "currentHash" to event.currentHash,
            "status" to event.status.name,
            "lastSyncedAt" to System.currentTimeMillis()
        )
    }

    private fun docToEvent(doc: DocumentSnapshot): TraceabilityEvent? {
        val eventId = doc.getString("eventId") ?: doc.id
        val batchId = doc.getString("batchId") ?: return null
        val eventTypeName = doc.getString("eventType") ?: TraceabilityEventType.PERIODIC_CHECKPOINT.name
        val stageName = doc.getString("stage") ?: SupplyChainStage.FARM.name
        val location = doc.getString("location") ?: "Unknown"
        val operator = doc.getString("operator") ?: "Automated IoT Node"
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val deviceId = doc.getString("deviceId") ?: ""
        val environmentalConditions = doc.getString("environmentalConditions") ?: ""
        val txId = doc.getString("blockchainTxId") ?: ""
        val prevHash = doc.getString("previousHash") ?: ""
        val currentHash = doc.getString("currentHash") ?: ""

        val eventType = try {
            TraceabilityEventType.valueOf(eventTypeName)
        } catch (_: Exception) {
            TraceabilityEventType.PERIODIC_CHECKPOINT
        }

        val stage = try {
            SupplyChainStage.valueOf(stageName)
        } catch (_: Exception) {
            SupplyChainStage.FARM
        }

        val statusName = doc.getString("status") ?: BlockchainSyncStatus.CONFIRMED.name
        val status = try {
            BlockchainSyncStatus.valueOf(statusName)
        } catch (_: Exception) {
            BlockchainSyncStatus.CONFIRMED
        }

        return TraceabilityEvent(
            eventId = eventId,
            batchId = batchId,
            eventType = eventType,
            stage = stage,
            location = location,
            operator = operator,
            timestamp = timestamp,
            deviceId = deviceId,
            environmentalConditions = environmentalConditions,
            blockchainTxId = txId,
            previousHash = prevHash,
            currentHash = currentHash,
            status = status
        )
    }

    private fun alertToMap(alert: Alert): Map<String, Any?> {
        return mapOf(
            "alertId" to alert.alertId,
            "batchId" to alert.batchId,
            "deviceId" to alert.deviceId,
            "timestamp" to alert.timestamp,
            "severity" to alert.severity.name,
            "type" to alert.type.name,
            "measurement" to alert.measurement,
            "actualValue" to alert.actualValue,
            "threshold" to alert.threshold,
            "acknowledged" to alert.acknowledged,
            "acknowledgedBy" to alert.acknowledgedBy,
            "acknowledgedAt" to alert.acknowledgedAt,
            "lastSyncedAt" to System.currentTimeMillis()
        )
    }

    /**
     * Await extension for Google Play Tasks using Coroutine cancellation.
     */
    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
}
