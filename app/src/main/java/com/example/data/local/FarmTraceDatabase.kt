package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AiAnalysis
import com.example.data.model.Alert
import com.example.data.model.AuditLog
import com.example.data.model.Batch
import com.example.data.model.Device
import com.example.data.model.SensorReading
import com.example.data.model.TraceabilityEvent

@Database(
    entities = [
        Device::class,
        Batch::class,
        SensorReading::class,
        TraceabilityEvent::class,
        Alert::class,
        AuditLog::class,
        AiAnalysis::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FarmTraceDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun batchDao(): BatchDao
    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun traceabilityEventDao(): TraceabilityEventDao
    abstract fun alertDao(): AlertDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun aiAnalysisDao(): AiAnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: FarmTraceDatabase? = null

        fun getInstance(context: Context): FarmTraceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FarmTraceDatabase::class.java,
                    "farmtrace.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
