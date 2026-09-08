package com.dualstream.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.dualstream.audio.AudioCaptureManager
import com.dualstream.audio.DualAudioPlayer
import com.dualstream.audio.JitterBuffer
import com.dualstream.model.AudioStats
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.network.StreamReceiver
import com.dualstream.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class ReceiverForegroundService : Service() {

    @Inject
    lateinit var nearbyConnectionManager: NearbyConnectionManager

    @Inject
    lateinit var dualAudioPlayer: DualAudioPlayer

    @Inject
    lateinit var jitterBuffer: JitterBuffer

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var streamReceiver: StreamReceiver

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var mixJob: Job? = null
    private var captureJob: Job? = null
    private var pingJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var audioCaptureManager: AudioCaptureManager? = null
    private val localPcmChannel = Channel<ByteArray>(Channel.CONFLATED)

    private var projectionIntent: Intent? = null
    private var projectionResultCode: Int = -1
    private var useFallbackGainMakeup = false
    private var hasSentHandshake = false
    private var bytesReceivedWindow = 0L
    private var windowStartTime = 0L

    companion object {
        private val _audioStats = MutableStateFlow(AudioStats())
        val audioStats: StateFlow<AudioStats> = _audioStats.asStateFlow()

        private val _isRemoteAudioFlowing = MutableStateFlow(false)
        val isRemoteAudioFlowing: StateFlow<Boolean> = _isRemoteAudioFlowing.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("DualStream", "ReceiverForegroundService created")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DualStream::ReceiverWakeLock").apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DualStream", "ReceiverForegroundService started")

        val notification = notificationHelper.buildReceiverNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(102, notification)
        }

        intent?.getParcelableExtra<Intent>("PROJECTION_INTENT")?.let { data ->
            projectionIntent = data
            projectionResultCode = intent.getIntExtra("PROJECTION_RESULT_CODE", -1)
            useFallbackGainMakeup = intent.getBooleanExtra("USE_FALLBACK_GAIN_MAKEUP", false)
            
            if (projectionResultCode != -1) {
                try {
                    val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mediaProjectionManager.getMediaProjection(projectionResultCode, data)
                    Log.d("DualStream", "▶▶ MediaProjection initialized in Receiver")
                } catch (e: Exception) {
                    Log.e("DualStream", "▶▶ Failed to init MediaProjection in Receiver", e)
                }
            }
        }

        nearbyConnectionManager.startAdvertising()
        dualAudioPlayer.initialize()
        startPipelineLoops()

        return START_NOT_STICKY
    }

    private fun startPipelineLoops() {
        if (mixJob != null) return // Already running
        
        Log.d("DualStream", "▶ Starting Receiver pipeline loops")
        jitterBuffer.clear()
        hasSentHandshake = false
        bytesReceivedWindow = 0L
        windowStartTime = System.currentTimeMillis()

        // Initialize AudioCaptureManager
        if (mediaProjection != null) {
            audioCaptureManager = AudioCaptureManager(this, mediaProjection!!, useFallbackGainMakeup)
            
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (useFallbackGainMakeup) {
                try {
                    val minVol = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxOf(minVol, 1), 0)
                    Log.d("DualStream", "Strategy B: Set STREAM_MUSIC volume to ${maxOf(minVol, 1)}")
                } catch (e: Exception) {
                    Log.e("DualStream", "Failed to lower STREAM_MUSIC volume", e)
                }
            } else {
                Log.d("DualStream", "Strategy A: Alternate output active, leaving STREAM_MUSIC unchanged")
            }
            
            captureJob = serviceScope.launch {
                audioCaptureManager!!.captureFlow.collect { frame ->
                    localPcmChannel.trySend(frame)
                }
            }
        }

        streamReceiver.onAudioFrameReceived = { frameBytes ->
            jitterBuffer.write(frameBytes)
            
            if (!hasSentHandshake) {
                hasSentHandshake = true
                _isRemoteAudioFlowing.value = true
                val connectedEndpointId = nearbyConnectionManager.connectedEndpointId
                if (connectedEndpointId != null) {
                    nearbyConnectionManager.sendControlMessage(
                        connectedEndpointId,
                        """{"type":"AUDIO_FLOW_CONFIRMED"}"""
                    )
                }
            }

            bytesReceivedWindow += frameBytes.size
            val now = System.currentTimeMillis()
            if (now - windowStartTime >= 1000) {
                val realKbps = (bytesReceivedWindow * 8) / (now - windowStartTime)
                _audioStats.value = _audioStats.value.copy(bitrateKbps = realKbps.toInt())
                bytesReceivedWindow = 0
                windowStartTime = now
            }

            if (jitterBuffer.packetsReceived % 100 == 1L) {
                Log.d("DualStream", "StreamReader: frame written (bufHealth=${jitterBuffer.bufferHealthPercent}%)")
            }
        }

        mixJob = serviceScope.launch(Dispatchers.IO) {
            Log.d("DualStream", "Mixing loop STARTED")
            var idleFrames = 0L
            var activeFrames = 0L
            while (isActive) {
                val networkMono = jitterBuffer.poll()
                val localMono = localPcmChannel.tryReceive().getOrNull()

                if (networkMono == null && localMono == null) {
                    idleFrames++
                    if (idleFrames % 500 == 1L) {
                        Log.d("DualStream", "MixLoop idle — no audio (idle=$idleFrames, jbuf=${jitterBuffer.bufferHealthPercent}%)")
                    }
                    delay(10)
                    yield()
                    continue
                }

                activeFrames++
                if (activeFrames % 100 == 1L) {
                    Log.d("DualStream", "MixLoop active frame #$activeFrames — net=${networkMono != null}, loc=${localMono != null}")
                }

                dualAudioPlayer.writeMixed(networkMono, localMono)

                val leftLevel = networkMono?.let { dualAudioPlayer.calculateLevel(it) } ?: 0f
                val rightLevel = localMono?.let { dualAudioPlayer.calculateLevel(it) } ?: 0f
                updateLeftChannelLevel(leftLevel)
                updateRightChannelLevel(rightLevel)

                _audioStats.value = _audioStats.value.copy(
                    bufferHealth = jitterBuffer.bufferHealthPercent,
                    packetsReceived = jitterBuffer.packetsReceived,
                    packetsDropped = jitterBuffer.packetsDropped
                )
                yield()
            }
        }

        pingJob = serviceScope.launch {
            while (isActive) {
                delay(5000)
                if (nearbyConnectionManager.connectedEndpointId != null) {
                    val pingJson = JSONObject().apply {
                        put("type", "PING")
                        put("timestamp", System.currentTimeMillis())
                    }
                    nearbyConnectionManager.sendControlMessage(pingJson)
                }
            }
        }

        serviceScope.launch {
            nearbyConnectionManager.controlMessages.collect { json ->
                if (json.optString("type") == "PONG") {
                    val pingTimestamp = json.optLong("timestamp", 0L)
                    if (pingTimestamp > 0) {
                        val rtt = System.currentTimeMillis() - pingTimestamp
                        val halfLatency = rtt / 2
                        _audioStats.value = _audioStats.value.copy(latencyMs = halfLatency)
                    }
                }
            }
        }
    }

    private fun updateLeftChannelLevel(level: Float) {
        val intent = Intent("com.dualstream.AUDIO_LEVEL").apply {
            putExtra("channel", "left")
            putExtra("level", level)
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateRightChannelLevel(level: Float) {
        val intent = Intent("com.dualstream.AUDIO_LEVEL").apply {
            putExtra("channel", "right")
            putExtra("level", level)
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun stopPipelineLoops() {
        Log.d("DualStream", "Stopping Receiver pipeline loops")
        
        // Restore volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val restoreVol = maxOf(maxVol / 2, 1) // Restore to ~50%
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
            Log.d("DualStream", "Restored STREAM_MUSIC volume to $restoreVol")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to restore STREAM_MUSIC volume", e)
        }
        
        mixJob?.cancel()
        mixJob = null
        captureJob?.cancel()
        captureJob = null
        pingJob?.cancel()
        pingJob = null
        
        try { mediaProjection?.stop() } catch (e: Exception) {}
        mediaProjection = null
        audioCaptureManager = null
        _isRemoteAudioFlowing.value = false
        
        dualAudioPlayer.release()
        jitterBuffer.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DualStream", "ReceiverForegroundService destroyed")
        stopPipelineLoops()
        serviceScope.cancel()
        nearbyConnectionManager.disconnect()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        stopForeground(true)
    }
}
