package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AiAnalysis
import kotlinx.coroutines.flow.Flow

@Dao
interface AiAnalysisDao {
    @Query("SELECT * FROM ai_analyses WHERE batchId = :batchId ORDER BY analysisTimestamp DESC LIMIT 1")
    fun getLatestAnalysisForBatch(batchId: String): Flow<AiAnalysis?>

    @Query("SELECT * FROM ai_analyses ORDER BY analysisTimestamp DESC")
    fun getAllAnalyses(): Flow<List<AiAnalysis>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: AiAnalysis)
}
