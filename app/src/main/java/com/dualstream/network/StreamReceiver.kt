package com.dualstream.network

import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamReceiver @Inject constructor() : PayloadCallback() {

    var onControlMessageReceived: ((String) -> Unit)? = null
    var onAudioFrameReceived: ((ByteArray) -> Unit)? = null

    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        when (payload.type) {
            Payload.Type.BYTES -> {
                val bytes = payload.asBytes() ?: return
                val json = String(bytes, Charsets.UTF_8)
                Log.d("DualStream", "  → BYTES payload: $json")
                onControlMessageReceived?.invoke(json)
            }
            Payload.Type.STREAM -> {
                Log.d("DualStream", "  → STREAM payload received! Starting reading thread.")
                val inputStream = payload.asStream()?.asInputStream() ?: return
                startReadingStream(inputStream)
            }
            else -> {
                Log.d("DualStream", "  → Unknown payload type: ${payload.type}")
            }
        }
    }

    private fun startReadingStream(inputStream: InputStream) {
        streamJob?.cancel()
        streamJob = scope.launch {
            val buffer = ByteArray(1920) // MONO_FRAME_BYTES
            var framesReceived = 0L
            
            try {
                while (true) {
                    var bytesRead = 0
                    while (bytesRead < 1920) {
                        val read = inputStream.read(buffer, bytesRead, 1920 - bytesRead)
                        if (read == -1) {
                            Log.w("DualStream", "Stream read returned -1. Stream ended.")
                            return@launch
                        }
                        bytesRead += read
                    }
                    framesReceived++
                    onAudioFrameReceived?.invoke(buffer.copyOf())
                    
                    if (framesReceived % 100 == 1L) {
                        Log.d("DualStream", "StreamReceiver: frame #$framesReceived received via STREAM")
                    }
                }
            } catch (e: Exception) {
                Log.e("DualStream", "Stream reading error", e)
            } finally {
                try {
                    inputStream.close()
                } catch (e: Exception) {}
            }
        }
    }

    override fun onPayloadTransferUpdate(
        endpointId: String,
        update: PayloadTransferUpdate
    ) {
        if (update.status == PayloadTransferUpdate.Status.FAILURE) {
            Log.e("DualStream", "Payload transfer FAILED for endpoint=$endpointId, payloadId=${update.payloadId}")
        }
    }
}
