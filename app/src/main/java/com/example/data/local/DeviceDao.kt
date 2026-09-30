package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Device
import com.example.data.model.DeviceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeenTimestamp DESC")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): Device?

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId LIMIT 1")
    fun observeDeviceById(deviceId: String): Flow<Device?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<Device>)

    @Update
    suspend fun updateDevice(device: Device)

    @Query("UPDATE devices SET status = :status, lastSeenTimestamp = :timestamp, batteryPercent = :battery, signalDbm = :signal WHERE deviceId = :deviceId")
    suspend fun updateHeartbeat(deviceId: String, status: DeviceStatus, timestamp: Long, battery: Int, signal: Int)

    @Query("UPDATE devices SET status = :status WHERE lastSeenTimestamp < :threshold AND status = 'ONLINE'")
    suspend fun markStaleDevicesOffline(threshold: Long, status: DeviceStatus = DeviceStatus.OFFLINE)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)
}
