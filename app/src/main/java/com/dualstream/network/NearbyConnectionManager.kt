package com.dualstream.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.dualstream.model.AppMode
import com.dualstream.model.ConnectionState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamReceiver: StreamReceiver
) {
    private val SERVICE_ID = "com.dualstream.nearby"
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    private val localDeviceName = Build.MODEL

    private val connectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _controlMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 16)
    val controlMessages: SharedFlow<JSONObject> = _controlMessages.asSharedFlow()

    var connectedEndpointId: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var lastConnectedEndpointId: String? = null
    private var currentMode: AppMode? = null

    init {
        streamReceiver.onControlMessageReceived = { jsonString ->
            try {
                val json = JSONObject(jsonString)
                Log.d("DualStream", "Received control message: $jsonString")
                scope.launch {
                    _controlMessages.emit(json)
                }
            } catch (e: Exception) {
                Log.e("DualStream", "Error parsing control message", e)
            }
        }
    }

    // Check if Google Play Services are available
    fun isPlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("DualStream", "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            _connectionState.value = ConnectionState.Connecting(connectionInfo.endpointName)
            acceptConnection(endpointId)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("DualStream", "Connection successful to $endpointId")
                connectedEndpointId = endpointId
                reconnectAttempts = 0
                lastConnectedEndpointId = endpointId
                _connectionState.value = ConnectionState.Connected(endpointId)
                
                // Stop advertising and discovery to release Bluetooth/Wi-Fi scanning resource
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                Log.d("DualStream", "Stopped advertising and discovery on connection success")

                // Send READY message containing device name
                sendControlMessage(JSONObject().apply {
                    put("type", "READY")
                    put("deviceName", localDeviceName)
                })
            } else {
                Log.w("DualStream", "Connection failed with code: ${result.status.statusCode}")
                _connectionState.value = ConnectionState.Error("Connection failed")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.w("DualStream", "Disconnected from $endpointId")
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                _connectionState.value = ConnectionState.Disconnected
            }

            // Attempt reconnection if we were previously connected
            if (lastConnectedEndpointId != null && reconnectAttempts < maxReconnectAttempts) {
                scope.launch {
                    val delayMs = (1000L * (reconnectAttempts + 1))
                        .coerceAtMost(10000L) // Max 10 second wait
                    Log.d(
                        "DualStream",
                        "Reconnect attempt ${reconnectAttempts + 1} in ${delayMs}ms"
                    )
                    delay(delayMs)
                    reconnectAttempts++
                    // Restart discovery or advertising based on mode
                    when (currentMode) {
                        AppMode.SENDER   -> startDiscovery()
                        AppMode.RECEIVER -> startAdvertising()
                        null -> {}
                    }
                }
            }
        }
    }

    fun startAdvertising() {
        if (!isPlayServicesAvailable()) {
            _connectionState.value = ConnectionState.Error("Google Play Services unavailable")
            return
        }
        currentMode = AppMode.RECEIVER
        _connectionState.value = ConnectionState.Discovering
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        
        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d("DualStream", "Advertising started successfully")
        }.addOnFailureListener { e ->
            Log.e("DualStream", "Advertising failed to start", e)
            _connectionState.value = ConnectionState.Error("Advertising failed: ${e.message}")
        }
    }

    fun startDiscovery() {
        if (!isPlayServicesAvailable()) {
            _connectionState.value = ConnectionState.Error("Google Play Services unavailable")
            return
        }
        currentMode = AppMode.SENDER
        _connectionState.value = ConnectionState.Discovering
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        
        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("DualStream", "Endpoint found: $endpointId (${info.endpointName})")
                    _connectionState.value = ConnectionState.Connecting(info.endpointName)
                    
                    // Automatically request connection
                    connectionsClient.requestConnection(
                        localDeviceName,
                        endpointId,
                        connectionLifecycleCallback
                    ).addOnFailureListener { e ->
                        Log.e("DualStream", "Failed to request connection to $endpointId", e)
                        _connectionState.value = ConnectionState.Error("Request failed: ${e.message}")
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("DualStream", "Endpoint lost: $endpointId")
                }
            },
            options
        ).addOnSuccessListener {
            Log.d("DualStream", "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e("DualStream", "Discovery failed to start", e)
            _connectionState.value = ConnectionState.Error("Discovery failed: ${e.message}")
        }
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, streamReceiver)
            .addOnSuccessListener {
                Log.d("DualStream", "Accepted connection from $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e("DualStream", "Failed to accept connection from $endpointId", e)
            }
    }

    fun sendControlMessage(endpointId: String, jsonMessage: String) {
        val bytes = jsonMessage.toByteArray(Charsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        connectionsClient.sendPayload(endpointId, payload)
            .addOnFailureListener { e ->
                Log.e("DualStream", "Failed to send control message to $endpointId", e)
            }
    }

    fun sendControlMessage(json: JSONObject) {
        val endpointId = connectedEndpointId ?: return
        sendControlMessage(endpointId, json.toString())
    }

    fun disconnect() {
        Log.d("DualStream", "Disconnecting NearbyConnectionManager")
        lastConnectedEndpointId = null
        reconnectAttempts = 0
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        _connectionState.value = ConnectionState.Idle
        currentMode = null
    }
}

