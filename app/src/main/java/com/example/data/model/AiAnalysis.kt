package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_analyses")
data class AiAnalysis(
    @PrimaryKey val analysisId: String,
    val batchId: String,
    val analysisTimestamp: Long = System.currentTimeMillis(),
    val inputDataSummary: String,
    val qualityTrend: String,
    val riskFactors: String,
    val observedAnomalies: String,
    val recommendedInspectionAction: String,
    val model: String = "gemini-3.5-flash",
    val advisoryNotice: String = "Advisory note: Analytical assistance only. Not certified food-safety approval."
)
