package com.dualstream.network

import android.util.Log
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamSender @Inject constructor(
    private val connectionsClient: ConnectionsClient
) {
    private var sendJob: Job? = null

    fun startSending(
        endpointId: String,
        monoFrameFlow: Flow<ByteArray>,
        scope: CoroutineScope
    ) {
        Log.d("DualStream", "▶ StreamSender.startSending() [BYTES Mode] — endpoint=$endpointId")
        
        sendJob = scope.launch(Dispatchers.IO) {
            var framesSent = 0L
            try {
                monoFrameFlow.collect { frame ->
                    // Send each 1920-byte frame as a BYTES payload instead of STREAM.
                    // This bypasses all InputStream/Pipe blocking issues!
                    val payload = Payload.fromBytes(frame)
                    connectionsClient.sendPayload(endpointId, payload)
                    
                    framesSent++
                    if (framesSent % 100 == 1L) {
                        Log.d("DualStream", "StreamSender: frame #$framesSent sent as BYTES (${frame.size} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.e("DualStream", "StreamSender error after $framesSent frames", e)
            }
            Log.d("DualStream", "StreamSender send loop ended — total frames: $framesSent")
        }
    }

    fun stopSending() {
        Log.d("DualStream", "StreamSender.stopSending()")
        sendJob?.cancel()
        sendJob = null
    }
}


