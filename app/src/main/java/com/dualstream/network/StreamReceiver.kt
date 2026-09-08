package com.dualstream.network

import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamReceiver @Inject constructor() : PayloadCallback() {

    var onControlMessageReceived: ((String) -> Unit)? = null
    var onAudioFrameReceived: ((ByteArray) -> Unit)? = null

    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        when (payload.type) {
            Payload.Type.BYTES -> {
                val bytes = payload.asBytes() ?: return
                if (bytes.size == 1920) {
                    onAudioFrameReceived?.invoke(bytes)
                } else {
                    val json = String(bytes, Charsets.UTF_8)
                    Log.d("DualStream", "  → BYTES payload: $json")
                    onControlMessageReceived?.invoke(json)
                }
            }
            Payload.Type.STREAM -> {
                Log.e("DualStream", "  → STREAM payload received but we are expecting BYTES for audio!")
            }
            else -> {
                Log.d("DualStream", "  → Unknown payload type: ${payload.type}")
            }
        }
    }

    override fun onPayloadTransferUpdate(
        endpointId: String,
        update: PayloadTransferUpdate
    ) {
        // Log transfer progress for stream payloads
        if (update.status == PayloadTransferUpdate.Status.FAILURE) {
            Log.e("DualStream", "Payload transfer FAILED for endpoint=$endpointId, payloadId=${update.payloadId}")
        }
    }
}

