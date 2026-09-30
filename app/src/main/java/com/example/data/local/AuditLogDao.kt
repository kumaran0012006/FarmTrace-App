package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert
    suspend fun insertLog(log: AuditLog)
}
