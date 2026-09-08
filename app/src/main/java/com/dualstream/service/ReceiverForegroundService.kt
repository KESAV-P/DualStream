package com.dualstream.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.dualstream.audio.DualAudioPlayer
import com.dualstream.audio.JitterBuffer
import com.dualstream.audio.LocalAudioCapture
import com.dualstream.model.AudioStats
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.network.StreamReceiver
import com.dualstream.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
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
    lateinit var localAudioCapture: LocalAudioCapture

    @Inject
    lateinit var streamReceiver: StreamReceiver

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var mixJob: Job? = null
    private var streamReaderJob: Job? = null
    private var pingJob: Job? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    companion object {
        private val _audioStats = MutableStateFlow(AudioStats())
        val audioStats: StateFlow<AudioStats> = _audioStats.asStateFlow()
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
            startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(102, notification)
        }

        nearbyConnectionManager.startAdvertising()

        dualAudioPlayer.initialize()

        startPipelineLoops()

        return START_NOT_STICKY
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d("DualStream", "Receiver audio focus change: $focusChange")
                }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> Log.d("DualStream", "Receiver audio focus change: $focusChange") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
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

    private fun startPipelineLoops() {
        if (mixJob != null) return // Already running
        
        Log.d("DualStream", "▶ Starting Receiver pipeline loops")
        jitterBuffer.clear()

        localAudioCapture.initialize()
        requestAudioFocus()

        // Stream Reader Job — reading from network stream and writing to jitter buffer
        streamReceiver.onAudioFrameReceived = { frameBytes ->
            jitterBuffer.write(frameBytes)
            // Log occasionally to avoid spam
            if (jitterBuffer.packetsReceived % 100 == 1L) {
                Log.d("DualStream", "StreamReader: frame written to jitter buffer (bufHealth=${jitterBuffer.bufferHealthPercent}%)")
            }
        }

        // Single mixing loop — clocked by AudioTrack.write(WRITE_BLOCKING) inside writeMixed
        // CRITICAL FIX: When both sources are null, we must NOT spin at full CPU speed.
        // We yield() every iteration and delay when idle, so the streamReaderJob can run.
        mixJob = serviceScope.launch(Dispatchers.IO) {
            Log.d("DualStream", "Mixing loop STARTED")
            var idleFrames = 0L
            var activeFrames = 0L
            while (isActive) {
                val networkMono = jitterBuffer.poll()
                val localMono = localAudioCapture.localPcmChannel
                    .tryReceive()
                    .getOrNull()

                if (networkMono == null && localMono == null) {
                    // *** CRITICAL: Don't spin writing silent frames at CPU speed ***
                    // This was starving the streamReaderJob coroutine, preventing audio from
                    // ever reaching the jitter buffer.
                    idleFrames++
                    if (idleFrames % 500 == 1L) {
                        Log.d("DualStream", "MixLoop idle — no audio from either source (idle=$idleFrames, jbuf=${jitterBuffer.bufferHealthPercent}%)")
                    }
                    delay(10) // ~10ms pause — gives streamReaderJob CPU time
                    yield()
                    continue
                }

                // We have at least one source — write the mixed frame
                activeFrames++
                if (activeFrames % 100 == 1L) {
                    Log.d("DualStream", "MixLoop active frame #$activeFrames — net=${networkMono != null}, loc=${localMono != null}")
                }

                dualAudioPlayer.writeMixed(networkMono, localMono)

                // Update levels for UI stats
                val leftLevel = networkMono?.let { dualAudioPlayer.calculateLevel(it) } ?: 0f
                val rightLevel = localMono?.let { dualAudioPlayer.calculateLevel(it) } ?: 0f
                updateLeftChannelLevel(leftLevel)
                updateRightChannelLevel(rightLevel)

                // Update stats
                _audioStats.value = _audioStats.value.copy(
                    bufferHealth = jitterBuffer.bufferHealthPercent,
                    packetsReceived = jitterBuffer.packetsReceived,
                    packetsDropped = jitterBuffer.packetsDropped,
                    bitrateKbps = 768
                )

                yield() // Cooperative multitasking
            }
        }

        // Latency Measurement Job (PING/PONG)
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

        // Handle PONG responses
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
        mixJob?.cancel()
        mixJob = null
        streamReaderJob?.cancel()
        streamReaderJob = null
        pingJob?.cancel()
        pingJob = null
        
        abandonAudioFocus()
        localAudioCapture.release()
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

