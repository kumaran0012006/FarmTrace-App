package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoService
import com.example.data.model.BlockchainSyncStatus
import com.example.data.model.SupplyChainStage
import com.example.data.model.TraceabilityEvent
import com.example.data.model.TraceabilityEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FarmTrace", appName)
    }

    @Test
    fun `cryptographic hash chain verification works correctly`() {
        val prevHash = CryptoService.GENESIS_PREV_HASH
        val eventId = "EVT-TEST-001"
        val devId = "ESP32-NODE-01"
        val batchId = "FT-BATCH-2026-0891"
        val time = 1727500000000L
        val conditions = "T:4.5°C | H:88.0% | C2H4:0.020ppm"

        val currentHash = CryptoService.computeEventHash(
            previousHash = prevHash,
            eventId = eventId,
            deviceId = devId,
            batchId = batchId,
            timestamp = time,
            sensorData = conditions
        )

        val event1 = TraceabilityEvent(
            eventId = eventId,
            batchId = batchId,
            eventType = TraceabilityEventType.STAGE_TRANSITION,
            stage = SupplyChainStage.FARM,
            location = "Origin Farm",
            operator = "Inspector 1",
            timestamp = time,
            deviceId = devId,
            environmentalConditions = conditions,
            blockchainTxId = "0x123",
            previousHash = prevHash,
            currentHash = currentHash,
            status = BlockchainSyncStatus.CONFIRMED
        )

        // Verify valid chain
        val result = CryptoService.verifyChain(listOf(event1))
        assertTrue("Expected chain to be valid", result.isValid)
        assertEquals(1, result.verifiedNodesCount)

        // Verify tampering is caught
        val tamperedEvent = event1.copy(environmentalConditions = "T:25.0°C | H:30.0% | TAMPERED")
        val tamperedResult = CryptoService.verifyChain(listOf(tamperedEvent))
        assertFalse("Expected tampering to be caught", tamperedResult.isValid)
    }

    @Test
    fun `room to firestore repository handles uninitialized cloud safely`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.local.FarmTraceDatabase.getInstance(context)
        val syncRepo = com.example.data.repository.RoomToFirestoreSyncRepositoryImpl(
            db = db,
            firestoreProvider = { null } // Simulate offline / unconfigured Firestore
        )

        val syncResult = syncRepo.syncAll()
        assertFalse(syncResult.success)
        assertTrue(syncResult.message.contains("not initialized", ignoreCase = true))

        val status = syncRepo.syncStatus.value
        assertFalse(status.isSyncing)
    }
}
