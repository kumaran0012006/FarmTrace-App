package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE acknowledged = 0 ORDER BY timestamp DESC")
    fun getUnacknowledgedAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE batchId = :batchId ORDER BY timestamp DESC")
    fun getAlertsForBatch(batchId: String): Flow<List<Alert>>

    @Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
    fun getUnacknowledgedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<Alert>)

    @Query("UPDATE alerts SET acknowledged = 1, acknowledgedBy = :operator, acknowledgedAt = :timestamp WHERE alertId = :alertId")
    suspend fun acknowledgeAlert(alertId: String, operator: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT EXISTS(SELECT 1 FROM alerts WHERE batchId = :batchId AND type = :alertType AND acknowledged = 0 AND timestamp > :recentThreshold)")
    suspend fun hasActiveRecentAlert(batchId: String, alertType: String, recentThreshold: Long): Boolean

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    suspend fun getAllAlertsList(): List<Alert>
}
