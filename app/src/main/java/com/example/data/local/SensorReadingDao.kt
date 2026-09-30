package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SensorReading
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorReadingDao {
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT 200")
    fun getAllReadings(): Flow<List<SensorReading>>

    @Query("SELECT * FROM sensor_readings WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 100")
    fun getReadingsForDevice(deviceId: String): Flow<List<SensorReading>>

    @Query("SELECT * FROM sensor_readings WHERE batchId = :batchId ORDER BY timestamp ASC")
    fun getReadingsForBatch(batchId: String): Flow<List<SensorReading>>

    @Query("SELECT * FROM sensor_readings WHERE batchId = :batchId ORDER BY timestamp ASC")
    suspend fun getReadingsListForBatch(batchId: String): List<SensorReading>

    @Query("SELECT * FROM sensor_readings WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReadingForDevice(deviceId: String): SensorReading?

    @Query("SELECT * FROM sensor_readings WHERE batchId = :batchId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReadingForBatch(batchId: String): SensorReading?

    @Query("SELECT EXISTS(SELECT 1 FROM sensor_readings WHERE eventId = :eventId)")
    suspend fun existsEvent(eventId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReading(reading: SensorReading): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(readings: List<SensorReading>)

    @Query("SELECT COUNT(*) FROM sensor_readings")
    fun getTotalReadingsCount(): Flow<Int>

    @Query("SELECT * FROM sensor_readings WHERE syncStatus = :status ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getReadingsBySyncStatus(status: com.example.data.model.SyncStatus, limit: Int = 100): List<SensorReading>

    @Query("SELECT * FROM sensor_readings WHERE syncStatus = :status AND batchId = :batchId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getReadingsBySyncStatusForBatch(status: com.example.data.model.SyncStatus, batchId: String, limit: Int = 100): List<SensorReading>

    @Query("UPDATE sensor_readings SET syncStatus = :status WHERE eventId = :eventId")
    suspend fun updateSyncStatus(eventId: String, status: com.example.data.model.SyncStatus)

    @Query("UPDATE sensor_readings SET syncStatus = :status WHERE eventId IN (:eventIds)")
    suspend fun updateBatchSyncStatus(eventIds: List<String>, status: com.example.data.model.SyncStatus)

    @Query("SELECT COUNT(*) FROM sensor_readings WHERE syncStatus = :status")
    fun observePendingSyncCount(status: com.example.data.model.SyncStatus = com.example.data.model.SyncStatus.PENDING_SYNC): Flow<Int>

    @Query("SELECT COUNT(*) FROM sensor_readings WHERE syncStatus = :status")
    suspend fun getCountBySyncStatus(status: com.example.data.model.SyncStatus): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReading(reading: SensorReading)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(readings: List<SensorReading>)
}
