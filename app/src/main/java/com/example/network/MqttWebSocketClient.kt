package com.example.network

import android.util.Log
import com.example.data.model.MqttTelemetryPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class BrokerConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class BrokerConfig(
    val brokerHost: String = "broker.emqx.io",
    val port: Int = 8083, // Default MQTT WebSocket port (e.g. EMQX or Mosquitto WS)
    val path: String = "/mqtt",
    val useTls: Boolean = true,
    val topicFilter: String = "farmtrace/+/telemetry",
    val clientId: String = "FarmTrace_Android_Client"
) {
    val websocketUrl: String
        get() = "${if (useTls) "wss" else "ws"}://$brokerHost:$port$path"
}

class MqttWebSocketClient(
    private val onPayloadReceived: suspend (MqttTelemetryPayload) -> Unit,
    private val onStatusChanged: (BrokerConnectionState, String?) -> Unit
) {
    private val tag = "MqttWebSocketClient"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val payloadAdapter = moshi.adapter(MqttTelemetryPayload::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WebSocket
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var currentConfig = BrokerConfig()
    private var isIntentionalDisconnect = false
    private var reconnectAttempts = 0

    private val _connectionState = MutableStateFlow(BrokerConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BrokerConnectionState> = _connectionState.asStateFlow()

    private val _lastMessageReceived = MutableStateFlow<String?>(null)
    val lastMessageReceived: StateFlow<String?> = _lastMessageReceived.asStateFlow()

    fun connect(config: BrokerConfig = currentConfig) {
        currentConfig = config
        isIntentionalDisconnect = false
        _connectionState.value = BrokerConnectionState.CONNECTING
        onStatusChanged(BrokerConnectionState.CONNECTING, null)

        val request = Request.Builder()
            .url(config.websocketUrl)
            .addHeader("Sec-WebSocket-Protocol", "mqtt")
            .build()

        webSocket?.cancel()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(tag, "WebSocket connected to ${config.websocketUrl}")
                _connectionState.value = BrokerConnectionState.CONNECTED
                reconnectAttempts = 0
                onStatusChanged(BrokerConnectionState.CONNECTED, null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _lastMessageReceived.value = text
                parseAndDispatch(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(tag, "WebSocket closing: $code / $reason")
                _connectionState.value = BrokerConnectionState.DISCONNECTED
                onStatusChanged(BrokerConnectionState.DISCONNECTED, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = BrokerConnectionState.DISCONNECTED
                onStatusChanged(BrokerConnectionState.DISCONNECTED, reason)
                if (!isIntentionalDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}")
                _connectionState.value = BrokerConnectionState.ERROR
                onStatusChanged(BrokerConnectionState.ERROR, t.localizedMessage ?: "Connection error")
                if (!isIntentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = BrokerConnectionState.DISCONNECTED
        onStatusChanged(BrokerConnectionState.DISCONNECTED, "Disconnected by user")
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts > 5) {
            Log.w(tag, "Max reconnect attempts reached")
            return
        }
        reconnectAttempts++
        val backoffMillis = (2000L * reconnectAttempts).coerceAtMost(30000L)
        scope.launch {
            delay(backoffMillis)
            if (!isIntentionalDisconnect && _connectionState.value != BrokerConnectionState.CONNECTED) {
                Log.i(tag, "Attempting reconnect #$reconnectAttempts")
                connect(currentConfig)
            }
        }
    }

    /**
     * Ingests JSON string directly, whether arriving via WebSocket or manual node ingress portal
     */
    fun parseAndDispatch(jsonString: String) {
        scope.launch {
            try {
                val payload = payloadAdapter.fromJson(jsonString)
                if (payload != null) {
                    onPayloadReceived(payload)
                } else {
                    Log.w(tag, "Parsed null payload from: $jsonString")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing telemetry JSON: ${e.message}", e)
            }
        }
    }
}
