package com.example.data.repository

import com.example.data.model.Alert
import com.example.data.model.Batch
import com.example.data.model.SensorReading
import com.example.data.model.TraceabilityEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Result metrics returned upon executing Room-to-Firestore synchronization.
 */
data class SyncResult(
    val success: Boolean,
    val syncedReadings: Int = 0,
    val syncedBatches: Int = 0,
    val syncedEvents: Int = 0,
    val syncedAlerts: Int = 0,
    val message: String = "",
    val error: Throwable? = null
)

/**
 * Real-time cloud sync status observed by UI dashboards and background workers.
 */
data class CloudSyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val pendingReadingsCount: Int = 0,
    val totalSyncedReadings: Int = 0,
    val isCloudConnected: Boolean = true,
    val lastError: String? = null,
    val lastSyncSummary: String? = null
)

/**
 * Repository interface for synchronizing local Room environmental telemetry,
 * agricultural batches, and cryptographic traceability proof events with
 * Firebase Cloud Firestore for end-to-end farm-to-fork tracking.
 */
interface RoomToFirestoreSyncRepository {

    /**
     * Observable state of the Room-to-Firestore cloud synchronization engine.
     */
    val syncStatus: StateFlow<CloudSyncStatus>

    /**
     * Executes a complete bidirectional synchronization between Room and Firestore:
     * 1. Pushes pending local environmental readings to cloud Firestore.
     * 2. Synchronizes agricultural batch metadata and supply-chain stages.
     * 3. Anchors cryptographic traceability chain events to the cloud ledger.
     * 4. Synchronizes environmental excursion alerts.
     */
    suspend fun syncAll(): SyncResult

    /**
     * Pushes pending or filtered local environmental sensor readings to Firestore.
     * Updates local Room records to [SyncStatus.SYNCED] upon cloud confirmation.
     *
     * @param batchId optional filter to sync only readings assigned to a specific batch.
     */
    suspend fun syncEnvironmentalReadings(batchId: String? = null): SyncResult

    /**
     * Synchronizes agricultural batches (farm origins, harvest dates, quantities, stages, thresholds)
     * between Room and Firestore under the `farm_batches` collection.
     */
    suspend fun syncBatches(): SyncResult

    /**
     * Synchronizes immutable cryptographic traceability events with previous/current hash linkage
     * and blockchain transaction IDs to Firestore under `traceability_chain`.
     *
     * @param batchId optional filter to sync events for a specific lot.
     */
    suspend fun syncTraceabilityEvents(batchId: String? = null): SyncResult

    /**
     * Synchronizes environmental threshold alerts to Firestore under `environmental_alerts`.
     */
    suspend fun syncAlerts(): SyncResult

    /**
     * Uploads an individual newly captured environmental reading from Room to Firestore.
     *
     * @return Result containing the Firestore document ID or error.
     */
    suspend fun uploadEnvironmentalReading(reading: SensorReading): Result<String>

    /**
     * Uploads or updates an agricultural batch entity in Firestore.
     */
    suspend fun uploadBatch(batch: Batch): Result<String>

    /**
     * Uploads a stage transition or periodic checkpoint traceability event to Firestore.
     */
    suspend fun uploadTraceabilityEvent(event: TraceabilityEvent): Result<String>

    /**
     * Pulls remote environmental telemetry for a batch from Firestore and persists into Room.
     */
    suspend fun pullRemoteEnvironmentalReadings(batchId: String, limit: Int = 100): Result<List<SensorReading>>

    /**
     * Pulls remote agricultural batches from Firestore and upserts into local Room database.
     */
    suspend fun pullRemoteBatches(): Result<List<Batch>>

    /**
     * Pulls remote traceability chain events from Firestore and persists into Room.
     */
    suspend fun pullRemoteTraceabilityEvents(batchId: String): Result<List<TraceabilityEvent>>

    /**
     * Attaches a real-time Firestore snapshot listener for the specified batch to maintain
     * real-time cloud-to-Room reactivity across cold storage, freight, and retail stakeholders.
     */
    fun startRealtimeBatchListener(batchId: String)

    /**
     * Detaches active Firestore snapshot listeners to conserve device power and data.
     */
    fun stopRealtimeListeners()

    /**
     * Observes the count of local readings pending cloud synchronization.
     */
    fun observePendingSyncCount(): Flow<Int>
}
