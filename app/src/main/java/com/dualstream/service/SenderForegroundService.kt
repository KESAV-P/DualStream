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
import com.dualstream.model.ConnectionState
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.network.StreamSender
import com.dualstream.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

@AndroidEntryPoint
class SenderForegroundService : Service() {

    @Inject
    lateinit var nearbyConnectionManager: NearbyConnectionManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var streamSender: StreamSender

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var mediaProjection: MediaProjection? = null
    private var audioCaptureManager: AudioCaptureManager? = null

    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var projectionIntent: Intent? = null
    private var projectionResultCode: Int = -1

    companion object {
        private val _audioLevel = MutableStateFlow(0f)
        val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
        
        private val _isStreaming = MutableStateFlow(false)
        val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

        private val _isSilenceDetected = MutableStateFlow(false)
        val isSilenceDetected: StateFlow<Boolean> = _isSilenceDetected.asStateFlow()
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

        // Store intent and result code and initialize MediaProjection immediately
        intent?.getParcelableExtra<Intent>("PROJECTION_INTENT")?.let { data ->
            projectionIntent = data
            val resultCode = intent.getIntExtra("PROJECTION_RESULT_CODE", -1)
            projectionResultCode = resultCode
            
            if (resultCode != -1) {
                try {
                    val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                    Log.d("DualStream", "▶▶ MediaProjection successfully initialized in onStartCommand")
                } catch (e: Exception) {
                    Log.e("DualStream", "▶▶ Failed to initialize MediaProjection in onStartCommand", e)
                }
            }
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
                        
                        // Auto-start streaming!
                        val curIntent = projectionIntent
                        val curCode = projectionResultCode
                        if (mediaProjection != null) {
                            startStreamingPipeline(-1, Intent()) // Dummy intent since mediaProjection is already active
                        } else if (curIntent != null && curCode != -1) {
                            startStreamingPipeline(curCode, curIntent)
                        } else {
                            Log.w("DualStream", "Cannot auto-start stream: projection parameters missing")
                        }
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
                    "STOP_STREAM" -> {
                        stopStreamingPipeline()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d("DualStream", "Audio focus change: $focusChange")
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> Log.d("DualStream", "Audio focus change: $focusChange") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }

    private fun calculatePcmLevel(pcmBytes: ByteArray): Float {
        var sum = 0f
        val samples = pcmBytes.size / 2
        for (i in 0 until samples) {
            val sample = ((pcmBytes[i * 2].toInt() and 0xFF) or (pcmBytes[i * 2 + 1].toInt() shl 8)).toShort()
            sum += Math.abs(sample.toFloat()) / 32768f
        }
        return if (samples > 0) sum / samples else 0f
    }

    private fun startStreamingPipeline(resultCode: Int, data: Intent) {
        val endpointId = nearbyConnectionManager.connectedEndpointId ?: return
        if (_isStreaming.value) return // Already streaming
        
        Log.d("DualStream", "▶ Starting Sender Audio pipeline to endpoint=$endpointId")
        
        requestAudioFocus()
        
        var proj = mediaProjection
        if (proj == null) {
            Log.d("DualStream", "MediaProjection is null in startStreamingPipeline — attempting to initialize")
            try {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                proj = mediaProjection
            } catch (e: Exception) {
                Log.e("DualStream", "Failed to initialize MediaProjection in startStreamingPipeline", e)
            }
        }
        
        if (proj == null) {
            Log.e("DualStream", "MediaProjection is null — cannot start capture")
            return
        }
        
        audioCaptureManager = AudioCaptureManager(this, proj)
        
        // *** CRITICAL FIX: DO NOT MUTE STREAM_MUSIC ***
        // adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE) silences the audio source,
        // which causes AudioPlaybackCaptureConfiguration to receive all-zero frames.
        // Instead, set volume to minimum (1) so the capture stream stays alive
        // while being nearly inaudible from the phone speaker.
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val minVol = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            // Set to minimum non-zero volume (keeps capture alive)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxOf(minVol, 1), 0)
            Log.d("DualStream", "Set STREAM_MUSIC volume to ${maxOf(minVol, 1)} (min=$minVol)")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to lower STREAM_MUSIC volume", e)
        }
        
        _isStreaming.value = true

        // Collect isSilenceDetected state from audioCaptureManager
        serviceScope.launch {
            audioCaptureManager?.isSilenceDetected?.collect { silence ->
                _isSilenceDetected.value = silence
                val connectedEndpointId = nearbyConnectionManager.connectedEndpointId
                if (connectedEndpointId != null) {
                    nearbyConnectionManager.sendControlMessage(
                        connectedEndpointId,
                        """{"type":"SILENCE_DETECTED","status":$silence}"""
                    )
                }
            }
        }

        val monoFrameFlow = audioCaptureManager!!.captureFlow
            .map { pcmBytes ->
                _audioLevel.value = calculatePcmLevel(pcmBytes)
                pcmBytes
            }
            .onCompletion {
                _isStreaming.value = false
                _audioLevel.value = 0f
            }
            
        Log.d("DualStream", "▶ Calling streamSender.startSending()")
        streamSender.startSending(endpointId, monoFrameFlow, serviceScope)
    }

    private fun stopStreamingPipeline(cancelSender: Boolean = true) {
        Log.d("DualStream", "Stopping Sender Audio pipeline")
        
        // Restore Phone A volume to a reasonable level
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val restoreVol = maxOf(maxVol / 2, 1) // Restore to ~50%
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
            Log.d("DualStream", "Restored STREAM_MUSIC volume to $restoreVol")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to restore STREAM_MUSIC volume", e)
        }
        
        abandonAudioFocus()

        if (cancelSender) {
            streamSender.stopSending()
        }
        _isStreaming.value = false
        _audioLevel.value = 0f
        _isSilenceDetected.value = false
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null
        audioCaptureManager = null
    }

    private fun updateNotification(message: String) {
        val notification = notificationHelper.buildSenderNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DualStream", "SenderForegroundService destroyed")
        
        stopStreamingPipeline()
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

