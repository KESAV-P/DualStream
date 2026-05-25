package com.dualstream.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.dualstream.audio.BYTES_PER_FRAME
import com.dualstream.model.ConnectionState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val SERVICE_ID = "com.dualstream.nearby"
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    private val localDeviceName = Build.MODEL

    private val connectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<Payload>(extraBufferCapacity = 64)
    val incomingPayloads: SharedFlow<Payload> = _incomingPayloads.asSharedFlow()

    private val _controlMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 16)
    val controlMessages: SharedFlow<JSONObject> = _controlMessages.asSharedFlow()

    var connectedEndpointId: String? = null
        private set

    private var activeMode: String? = null // "SENDER" or "RECEIVER"

    private var pipedOutputStream: PipedOutputStream? = null

    // Check if Google Play Services are available
    fun isPlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(context)
        return result == ConnectionResult.SUCCESS
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                try {
                    val messageBytes = payload.asBytes() ?: return
                    val messageString = String(messageBytes, Charsets.UTF_8)
                    val json = JSONObject(messageString)
                    Log.d("DualStream", "Received control message: $messageString")
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        _controlMessages.emit(json)
                    }
                } catch (e: Exception) {
                    Log.e("DualStream", "Error parsing control message", e)
                }
            } else if (payload.type == Payload.Type.STREAM) {
                Log.d("DualStream", "Received STREAM payload from $endpointId")
                CoroutineScope(Dispatchers.IO).launch {
                    _incomingPayloads.emit(payload)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Track transfer updates if needed
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("DualStream", "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            _connectionState.value = ConnectionState.Connecting(connectionInfo.endpointName)
            acceptConnection(endpointId)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d("DualStream", "Connection successful to $endpointId")
                    connectedEndpointId = endpointId
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
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w("DualStream", "Connection rejected by $endpointId")
                    _connectionState.value = ConnectionState.Error("Connection rejected")
                }
                else -> {
                    Log.e("DualStream", "Connection failed with code: ${result.status.statusCode}")
                    _connectionState.value = ConnectionState.Error("Connection failed: ${result.status.statusMessage}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("DualStream", "Disconnected from $endpointId")
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                _connectionState.value = ConnectionState.Disconnected
                
                // Attempt automatic reconnection once
                if (activeMode == "SENDER") {
                    Log.d("DualStream", "Sender restarting discovery for reconnection")
                    startDiscovery()
                } else if (activeMode == "RECEIVER") {
                    Log.d("DualStream", "Receiver restarting advertising for reconnection")
                    startAdvertising()
                }
            }
        }
    }

    fun startAdvertising() {
        if (!isPlayServicesAvailable()) {
            _connectionState.value = ConnectionState.Error("Google Play Services unavailable")
            return
        }
        activeMode = "RECEIVER"
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
        activeMode = "SENDER"
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
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d("DualStream", "Accepted connection from $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e("DualStream", "Failed to accept connection from $endpointId", e)
            }
    }

    fun sendControlMessage(json: JSONObject) {
        val endpointId = connectedEndpointId ?: return
        val message = json.toString()
        val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
            .addOnFailureListener { e ->
                Log.e("DualStream", "Failed to send control message to $endpointId", e)
            }
    }

    fun startAudioStream(): OutputStream? {
        val endpointId = connectedEndpointId ?: return null
        try {
            val output = PipedOutputStream()
            val input = PipedInputStream(output, BYTES_PER_FRAME * 8)
            pipedOutputStream = output
            
            val payload = Payload.fromStream(input)
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d("DualStream", "Audio stream payload sent to $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e("DualStream", "Failed to send audio stream payload", e)
                }
            return output
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to set up piped streams", e)
            return null
        }
    }

    fun sendAudioChunk(chunk: ByteArray) {
        // Obsolete if we write directly to the PipedOutputStream, but kept for compatibility.
        // If we write to pipedOutputStream, the background StreamSender handles writing.
    }

    fun disconnect() {
        Log.d("DualStream", "Disconnecting NearbyConnectionManager")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        _connectionState.value = ConnectionState.Idle
        activeMode = null
        
        try {
            pipedOutputStream?.close()
        } catch (e: Exception) {
            // Ignore
        }
        pipedOutputStream = null
    }
}
