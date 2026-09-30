package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BlockchainSyncStatus
import com.example.data.model.TraceabilityEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceabilityEventDao {
    @Query("SELECT * FROM traceability_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<TraceabilityEvent>>

    @Query("SELECT * FROM traceability_events WHERE batchId = :batchId ORDER BY timestamp ASC")
    fun getEventsForBatch(batchId: String): Flow<List<TraceabilityEvent>>

    @Query("SELECT * FROM traceability_events WHERE batchId = :batchId ORDER BY timestamp ASC")
    suspend fun getEventsListForBatch(batchId: String): List<TraceabilityEvent>

    @Query("SELECT * FROM traceability_events WHERE batchId = :batchId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEventForBatch(batchId: String): TraceabilityEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TraceabilityEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<TraceabilityEvent>)

    @Query("UPDATE traceability_events SET status = :status, blockchainTxId = :txId WHERE eventId = :eventId")
    suspend fun updateBlockchainStatus(eventId: String, status: BlockchainSyncStatus, txId: String)

    @Query("SELECT COUNT(*) FROM traceability_events")
    fun getTotalEventsCount(): Flow<Int>
}
