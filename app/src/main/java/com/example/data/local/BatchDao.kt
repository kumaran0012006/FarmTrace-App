package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Batch
import com.example.data.model.SupplyChainStage
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches ORDER BY createdAt DESC")
    fun getAllBatches(): Flow<List<Batch>>

    @Query("SELECT * FROM batches WHERE batchId = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: String): Batch?

    @Query("SELECT * FROM batches WHERE batchId = :batchId LIMIT 1")
    fun observeBatchById(batchId: String): Flow<Batch?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: Batch)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(batches: List<Batch>)

    @Update
    suspend fun updateBatch(batch: Batch)

    @Query("UPDATE batches SET currentStage = :stage WHERE batchId = :batchId")
    suspend fun updateStage(batchId: String, stage: SupplyChainStage)

    @Query("UPDATE batches SET assignedDeviceId = :deviceId WHERE batchId = :batchId")
    suspend fun assignDevice(batchId: String, deviceId: String)

    @Query("SELECT * FROM batches ORDER BY createdAt DESC")
    suspend fun getAllBatchesList(): List<Batch>
}
