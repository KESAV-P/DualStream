package com.dualstream.network

import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamSender @Inject constructor(
    private val connectionsClient: ConnectionsClient
) {
    private var sendJob: Job? = null
    private var outputStream: OutputStream? = null

    fun startSending(
        endpointId: String,
        monoFrameFlow: Flow<ByteArray>,
        scope: CoroutineScope
    ) {
        Log.d("DualStream", "▶ StreamSender.startSending() [STREAM Mode] — endpoint=$endpointId")
        
        val fds = ParcelFileDescriptor.createPipe()
        val readFd = fds[0]
        val writeFd = fds[1]
        
        outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writeFd)
        val payload = Payload.fromStream(readFd)
        connectionsClient.sendPayload(endpointId, payload)
        
        sendJob = scope.launch(Dispatchers.IO) {
            var framesSent = 0L
            try {
                monoFrameFlow.collect { frame ->
                    outputStream?.write(frame)
                    framesSent++
                    if (framesSent % 100 == 1L) {
                        Log.d("DualStream", "StreamSender: frame #$framesSent sent via STREAM (${frame.size} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.e("DualStream", "StreamSender error after $framesSent frames", e)
            } finally {
                Log.d("DualStream", "StreamSender send loop ended — total frames: $framesSent")
                stopSending()
            }
        }
    }

    fun stopSending() {
        Log.d("DualStream", "StreamSender.stopSending()")
        sendJob?.cancel()
        sendJob = null
        try {
            outputStream?.close()
        } catch (e: Exception) {}
        outputStream = null
    }
}

