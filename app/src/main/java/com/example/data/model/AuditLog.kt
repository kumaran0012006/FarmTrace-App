package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val action: String,
    val resource: String,
    val resourceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ipOrMetadata: String = "ClientApp/v1.0"
)
