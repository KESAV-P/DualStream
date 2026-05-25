package com.dualstream.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.dualstream.audio.AudioCaptureManager
import com.dualstream.audio.AudioMixer
import com.dualstream.audio.OpusEncoder
import com.dualstream.model.ConnectionState
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.network.StreamSender
import com.dualstream.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class SenderForegroundService : Service() {

    @Inject
    lateinit var nearbyConnectionManager: NearbyConnectionManager

    @Inject
    lateinit var opusEncoder: OpusEncoder

    @Inject
    lateinit var audioMixer: AudioMixer

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    
    private var mediaProjection: MediaProjection? = null
    private var audioCaptureManager: AudioCaptureManager? = null

    companion object {
        private val _audioLevel = MutableStateFlow(0f)
        val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
        
        private val _isStreaming = MutableStateFlow(false)
        val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("DualStream", "SenderForegroundService created")
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DualStream::SenderWakeLock").apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DualStream", "SenderForegroundService started")
        
        val notification = notificationHelper.buildSenderNotification("Searching...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(101, notification)
        }

        nearbyConnectionManager.startDiscovery()

        // Connection State Observer
        serviceScope.launch {
            nearbyConnectionManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        val deviceName = state.deviceName
                        val connectedNotification = notificationHelper.buildSenderNotification(deviceName)
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.notify(101, connectedNotification)
                    }
                    is ConnectionState.Disconnected -> {
                        stopStreamingPipeline()
                    }
                    is ConnectionState.Idle -> {
                        stopStreamingPipeline()
                    }
                    else -> {}
                }
            }
        }

        // Control Command Observer
        serviceScope.launch {
            nearbyConnectionManager.controlMessages.collect { json ->
                when (json.optString("type")) {
                    "START_STREAM" -> {
                        val tokenIntent = intent?.getParcelableExtra<Intent>("PROJECTION_INTENT")
                        val resultCode = intent?.getIntExtra("PROJECTION_RESULT_CODE", -1) ?: -1
                        if (tokenIntent != null) {
                            startStreamingPipeline(resultCode, tokenIntent)
                        } else {
                            Log.e("DualStream", "MediaProjection token Intent is missing")
                        }
                    }
                    "STOP_STREAM" -> {
                        stopStreamingPipeline()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startStreamingPipeline(resultCode: Int, data: Intent) {
        if (captureJob != null) return // Already streaming
        
        Log.d("DualStream", "Starting Sender Audio pipeline")
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        val proj = mediaProjection ?: return
        
        audioCaptureManager = AudioCaptureManager(proj)
        
        val outputStream = nearbyConnectionManager.startAudioStream()
        if (outputStream == null) {
            Log.e("DualStream", "Nearby audio stream output stream is null")
            return
        }
        val streamSender = StreamSender(outputStream)
        _isStreaming.value = true

        captureJob = serviceScope.launch {
            try {
                audioCaptureManager!!.captureFlow
                    .map { pcmBytes ->
                        val level = audioMixer.calculateLevel(pcmBytes)
                        _audioLevel.value = level
                        opusEncoder.encode(pcmBytes)
                    }
                    .filterNotNull()
                    .collect { encodedFrame ->
                        streamSender.sendFrame(encodedFrame)
                    }
            } catch (e: CancellationException) {
                Log.d("DualStream", "Streaming capture job cancelled normally")
            } catch (e: Exception) {
                Log.e("DualStream", "Error in sender capture pipeline", e)
            } finally {
                _isStreaming.value = false
                _audioLevel.value = 0f
                try {
                    outputStream.close()
                } catch (e: Exception) {}
            }
        }
    }

    private fun stopStreamingPipeline() {
        Log.d("DualStream", "Stopping Sender Audio pipeline")
        captureJob?.cancel()
        captureJob = null
        _isStreaming.value = false
        _audioLevel.value = 0f
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null
        audioCaptureManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DualStream", "SenderForegroundService destroyed")
        stopStreamingPipeline()
        serviceScope.cancel()
        
        nearbyConnectionManager.disconnect()
        opusEncoder.release()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        stopForeground(true)
    }
}
