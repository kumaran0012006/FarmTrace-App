package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.ChainVerificationResult
import com.example.data.local.FarmTraceDatabase
import com.example.data.model.AiAnalysis
import com.example.data.model.Alert
import com.example.data.model.AuditLog
import com.example.data.model.Batch
import com.example.data.model.Device
import com.example.data.model.DeviceType
import com.example.data.model.MqttTelemetryPayload
import com.example.data.model.SensorReading
import com.example.data.model.SupplyChainStage
import com.example.data.model.TraceabilityEvent
import com.example.data.repository.CloudSyncStatus
import com.example.data.repository.FarmTraceRepository
import com.example.data.repository.RoomToFirestoreSyncRepository
import com.example.network.BrokerConfig
import com.example.network.BrokerConnectionState
import com.example.network.MqttWebSocketClient
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    DASHBOARD,
    BATCHES,
    BATCH_DETAIL,
    DEVICES,
    DEVICE_DETAIL,
    TRACEABILITY,
    ALERTS,
    AI_INSIGHTS,
    PUBLIC_VERIFY,
    QR_SCANNER,
    SETTINGS
}

data class FarmTraceUiState(
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    val selectedBatchId: String? = null,
    val selectedDeviceId: String? = null,
    val brokerConfig: BrokerConfig = BrokerConfig(),
    val connectionState: BrokerConnectionState = BrokerConnectionState.DISCONNECTED,
    val connectionStatusMessage: String? = null,
    val lastReceivedMessage: String? = null,
    val verificationResult: ChainVerificationResult? = null,
    val isVerifyingIntegrity: Boolean = false,
    val scannedNodeReport: com.example.crypto.NodeVerificationReport? = null,
    val lastScannedRawQr: String? = null,
    val aiAnalysisResult: AiAnalysis? = null,
    val isRunningAi: Boolean = false,
    val aiErrorMessage: String? = null,
    val snackbarMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FarmTraceDatabase.getInstance(application)
    val repository = FarmTraceRepository(db)

    private val _uiState = MutableStateFlow(FarmTraceUiState())
    val uiState: StateFlow<FarmTraceUiState> = _uiState.asStateFlow()

    val batches: StateFlow<List<Batch>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devices: StateFlow<List<Device>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readings: StateFlow<List<SensorReading>> = repository.allReadings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<TraceabilityEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<Alert>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unacknowledgedAlerts: StateFlow<List<Alert>> = repository.unacknowledgedAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unacknowledgedAlertCount: StateFlow<Int> = repository.unacknowledgedAlertCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncRepository: RoomToFirestoreSyncRepository = repository.syncRepository
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = syncRepository.syncStatus

    private var heartbeatCheckJob: Job? = null

    val mqttClient = MqttWebSocketClient(
        onPayloadReceived = { payload ->
            repository.ingestTelemetry(payload)
        },
        onStatusChanged = { state, reason ->
            _uiState.update {
                it.copy(
                    connectionState = state,
                    connectionStatusMessage = reason
                )
            }
        }
    )

    init {
        // Observe last raw message
        viewModelScope.launch {
            mqttClient.lastMessageReceived.collect { msg ->
                _uiState.update { it.copy(lastReceivedMessage = msg) }
            }
        }

        // Periodic check for stale devices (mark offline if heartbeat missing > 2 minutes)
        heartbeatCheckJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                repository.checkStaleDevices()
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun selectBatch(batchId: String, navigateToDetail: Boolean = true) {
        _uiState.update {
            it.copy(
                selectedBatchId = batchId,
                verificationResult = null,
                aiAnalysisResult = null,
                currentScreen = if (navigateToDetail) AppScreen.BATCH_DETAIL else it.currentScreen
            )
        }
    }

    fun selectDevice(deviceId: String) {
        _uiState.update {
            it.copy(
                selectedDeviceId = deviceId,
                currentScreen = AppScreen.DEVICE_DETAIL
            )
        }
    }

    fun connectBroker(config: BrokerConfig? = null) {
        val conf = config ?: _uiState.value.brokerConfig
        _uiState.update { it.copy(brokerConfig = conf) }
        mqttClient.connect(conf)
    }

    fun disconnectBroker() {
        mqttClient.disconnect()
    }

    fun ingestRawPayload(rawJson: String) {
        mqttClient.parseAndDispatch(rawJson)
        _uiState.update { it.copy(snackbarMessage = "Telemetry packet submitted for validation") }
    }

    fun emitSimulatedReading(
        targetDeviceId: String? = null,
        tempOverride: Double? = null,
        humidityOverride: Double? = null,
        ethyleneOverride: Double? = null,
        isExcursion: Boolean = false
    ) {
        viewModelScope.launch {
            val deviceList = devices.value
            val device = if (targetDeviceId != null) {
                deviceList.find { it.deviceId == targetDeviceId }
            } else {
                deviceList.firstOrNull()
            }
            val devId = device?.deviceId ?: "ESP32-NODE-01"
            val batId = device?.assignedBatchId ?: batches.value.firstOrNull()?.batchId ?: "LOT-2026-0901"

            val lastReading = readings.value.firstOrNull { it.deviceId == devId }
            val baseTemp = if (isExcursion) {
                14.2
            } else {
                tempOverride ?: ((lastReading?.temperature ?: 4.2) + (Math.random() * 0.4 - 0.2))
            }
            val baseHumidity = if (isExcursion) {
                68.0
            } else {
                humidityOverride ?: ((lastReading?.humidity ?: 89.5) + (Math.random() * 0.6 - 0.3))
            }
            val baseEthylene = if (isExcursion) {
                0.125
            } else {
                (ethyleneOverride ?: ((lastReading?.ethylene ?: 0.025) + (Math.random() * 0.004 - 0.002))).coerceAtLeast(0.005)
            }

            val payload = MqttTelemetryPayload(
                eventId = "EVT-STREAM-" + UUID.randomUUID().toString().take(8).uppercase(),
                deviceId = devId,
                batchId = batId,
                timestamp = System.currentTimeMillis(),
                temperature = Math.round(baseTemp * 10.0) / 10.0,
                humidity = Math.round(baseHumidity * 10.0) / 10.0,
                ethylene = Math.round(baseEthylene * 1000.0) / 1000.0,
                battery = ((device?.batteryPercent ?: 95) - (if (Math.random() > 0.8) 1 else 0)).coerceIn(1, 100),
                solarVoltage = 4.85 + (Math.random() * 0.3),
                solarCurrent = 115.0 + (Math.random() * 20.0),
                solarEnergy = 340.0,
                powerConsumption = 48.0,
                latitude = 36.7783 + (Math.random() * 0.01),
                longitude = -119.4179 + (Math.random() * 0.01),
                signalStrength = -65 + (Math.random() * 6).toInt(),
                firmwareVersion = "v1.4.2",
                signature = "SIG_ED25519_" + UUID.randomUUID().toString().take(12)
            )
            repository.ingestTelemetry(payload)
            _uiState.update {
                it.copy(
                    snackbarMessage = if (isExcursion) {
                        "⚠️ Excursion packet injected for $devId ($baseTemp°C / $baseEthylene ppm)"
                    } else {
                        "📡 Live telemetry packet received from $devId"
                    }
                )
            }
        }
    }

    fun verifyIntegrity(batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingIntegrity = true) }
            val result = repository.verifyBatchIntegrity(batchId)
            _uiState.update {
                it.copy(
                    verificationResult = result,
                    isVerifyingIntegrity = false
                )
            }
        }
    }

    fun runAiAnalysis(batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningAi = true, aiErrorMessage = null) }
            val result = repository.runAiQualityAnalysis(batchId)
            result.onSuccess { analysis ->
                _uiState.update {
                    it.copy(
                        aiAnalysisResult = analysis,
                        isRunningAi = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRunningAi = false,
                        aiErrorMessage = err.localizedMessage ?: err.message
                    )
                }
            }
        }
    }

    fun createBatch(
        batchId: String,
        productName: String,
        variety: String,
        farmOrigin: String,
        harvestDate: String,
        quantity: Double,
        unit: String,
        destination: String,
        tempMin: Double,
        tempMax: Double,
        humidityMin: Double,
        humidityMax: Double,
        maxEthylene: Double,
        deviceId: String?
    ) {
        viewModelScope.launch {
            repository.createBatch(
                batchId = batchId,
                productName = productName,
                variety = variety,
                farmOrigin = farmOrigin,
                harvestDate = harvestDate,
                quantity = quantity,
                unit = unit,
                destination = destination,
                tempMin = tempMin,
                tempMax = tempMax,
                humidityMin = humidityMin,
                humidityMax = humidityMax,
                maxEthylene = maxEthylene,
                deviceId = deviceId
            )
            _uiState.update {
                it.copy(
                    selectedBatchId = batchId,
                    snackbarMessage = "Batch $batchId registered on ledger"
                )
            }
        }
    }

    fun advanceBatchStage(
        batchId: String,
        newStage: SupplyChainStage,
        location: String,
        operator: String
    ) {
        viewModelScope.launch {
            repository.advanceBatchStage(batchId, newStage, location, operator)
            _uiState.update {
                it.copy(snackbarMessage = "Batch advanced to ${newStage.displayName}")
            }
        }
    }

    fun registerDevice(deviceId: String, name: String, type: DeviceType, batchId: String?) {
        viewModelScope.launch {
            repository.registerDevice(deviceId, name, type, batchId)
            _uiState.update {
                it.copy(snackbarMessage = "Device $deviceId registered")
            }
        }
    }

    fun acknowledgeAlert(alertId: String, operator: String = "Operator") {
        viewModelScope.launch {
            repository.acknowledgeAlert(alertId, operator)
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val result = syncRepository.syncAll()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (result.success) {
                        result.message
                    } else {
                        "Cloud sync: ${result.message}"
                    }
                )
            }
        }
    }

    fun syncBatchToCloud(batchId: String) {
        viewModelScope.launch {
            val readingResult = syncRepository.syncEnvironmentalReadings(batchId)
            val eventResult = syncRepository.syncTraceabilityEvents(batchId)
            _uiState.update {
                it.copy(
                    snackbarMessage = "Synced lot $batchId: ${readingResult.syncedReadings} telemetry, ${eventResult.syncedEvents} proofs to cloud"
                )
            }
        }
    }

    fun verifyNodeQrPayload(rawQr: String) {
        val payload = com.example.crypto.CryptoService.parseNodeQrPayload(rawQr)
        if (payload == null) {
            _uiState.update {
                it.copy(snackbarMessage = "Unrecognized QR format. Ensure code contains FarmTrace node payload.")
            }
            return
        }

        val report = com.example.crypto.CryptoService.verifySupplyChainNode(payload)
        _uiState.update {
            it.copy(
                scannedNodeReport = report,
                lastScannedRawQr = rawQr
            )
        }
    }

    fun clearScannedNodeReport() {
        _uiState.update {
            it.copy(scannedNodeReport = null, lastScannedRawQr = null)
        }
    }

    fun importVerifiedNodeToLedger(report: com.example.crypto.NodeVerificationReport) {
        val payload = report.payload
        viewModelScope.launch {
            val stageEnum = try {
                SupplyChainStage.valueOf(payload.stage.uppercase())
            } catch (e: Exception) {
                SupplyChainStage.HARVEST
            }

            val event = TraceabilityEvent(
                eventId = payload.eventId,
                batchId = payload.batchId,
                eventType = com.example.data.model.TraceabilityEventType.CUSTODY_TRANSFER,
                stage = stageEnum,
                location = payload.location,
                operator = payload.operator,
                timestamp = payload.timestamp,
                deviceId = payload.deviceId,
                environmentalConditions = payload.environmentalConditions,
                blockchainTxId = com.example.crypto.CryptoService.computeBlockchainTxId(payload.currentHash, payload.timestamp),
                previousHash = payload.previousHash,
                currentHash = payload.currentHash,
                status = com.example.data.model.BlockchainSyncStatus.CONFIRMED
            )

            repository.recordTraceabilityEvent(event)
            _uiState.update {
                it.copy(
                    snackbarMessage = "Node ${payload.eventId} verified & logged to tamper-evident ledger!",
                    scannedNodeReport = null
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatCheckJob?.cancel()
        syncRepository.stopRealtimeListeners()
        mqttClient.disconnect()
    }
}
