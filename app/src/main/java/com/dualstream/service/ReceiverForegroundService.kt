package com.dualstream.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.dualstream.audio.*
import com.dualstream.model.AudioStats
import com.dualstream.model.ConnectionState
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.network.StreamReceiver
import com.dualstream.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject

@AndroidEntryPoint
class ReceiverForegroundService : Service() {

    @Inject
    lateinit var nearbyConnectionManager: NearbyConnectionManager

    @Inject
    lateinit var opusDecoder: OpusDecoder

    @Inject
    lateinit var audioMixer: AudioMixer

    @Inject
    lateinit var jitterBuffer: JitterBuffer

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var audioTrack: AudioTrack? = null
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private val localAudioProcessor = LocalAudioProcessor()
    
    private val leftChannelBufferQueue = LinkedBlockingQueue<ByteArray>(30)
    private val silenceBuffer = ByteArray(BYTES_PER_FRAME)

    private var networkReceiveJob: Job? = null
    private var localCaptureJob: Job? = null
    private var mixingJob: Job? = null
    private var pingJob: Job? = null

    companion object {
        private val _audioStats = MutableStateFlow(AudioStats())
        val audioStats: StateFlow<AudioStats> = _audioStats.asStateFlow()
        
        const val ACTION_PLAY = "com.dualstream.ACTION_PLAY"
        const val ACTION_STOP_MEDIA = "com.dualstream.ACTION_STOP_MEDIA"
        const val EXTRA_AUDIO_URI = "com.dualstream.EXTRA_AUDIO_URI"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("DualStream", "ReceiverForegroundService created")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DualStream::ReceiverWakeLock").apply {
            acquire()
        }

        initAudioTrack()
        initExoPlayer()
    }

    private fun initAudioTrack() {
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(BYTES_PER_FRAME * 8)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            Log.d("DualStream", "AudioTrack initialized successfully")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to initialize AudioTrack", e)
        }
    }

    private fun initExoPlayer() {
        try {
            val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink? {
                    return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(localAudioProcessor))
                        .build()
                }
            }

            exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .build().apply {
                    volume = 0f // Mute local direct output (rendered via AudioTrack mixing loop)
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                }
            Log.d("DualStream", "Receiver ExoPlayer initialized")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to initialize ExoPlayer", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DualStream", "ReceiverForegroundService started")

        when (intent?.action) {
            ACTION_PLAY -> {
                val uriString = intent.getStringExtra(EXTRA_AUDIO_URI)
                if (uriString != null) {
                    playUri(Uri.parse(uriString))
                }
            }
            ACTION_STOP_MEDIA -> {
                exoPlayer?.stop()
            }
            else -> {
                val notification = notificationHelper.buildReceiverNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(102, notification)
                }

                nearbyConnectionManager.startAdvertising()

                startPipelineLoops()
            }
        }

        return START_NOT_STICKY
    }

    private fun playUri(uri: Uri) {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            player.prepare()
            player.play()
            Log.d("DualStream", "ExoPlayer playing local file URI: $uri")
        }
    }

    private fun startPipelineLoops() {
        if (networkReceiveJob != null) return // Already running
        
        Log.d("DualStream", "Starting Receiver pipeline loops")
        jitterBuffer.clear()
        leftChannelBufferQueue.clear()

        // Loop 1: Network Receive Loop
        networkReceiveJob = serviceScope.launch {
            nearbyConnectionManager.incomingPayloads.collect { payload ->
                val stream = payload.asStream()?.asInputStream() ?: return@collect
                val streamReceiver = StreamReceiver(stream)
                Log.d("DualStream", "Receiver pipeline reading stream payload")
                
                try {
                    while (isActive) {
                        val opusBytes = streamReceiver.readFrame() ?: break
                        val pcmBytes = opusDecoder.decode(opusBytes)
                        if (pcmBytes != null) {
                            jitterBuffer.offer(pcmBytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DualStream", "Error reading streaming payload", e)
                }
            }
        }

        // Loop 2: Local Audio Interception & Capture Loop
        localCaptureJob = serviceScope.launch {
            val chunkBuffer = ByteArray(BYTES_PER_FRAME)
            var bytesBuffered = 0
            while (isActive) {
                try {
                    val rawChunk = localAudioProcessor.pcmQueue.poll()
                    if (rawChunk != null) {
                        var sourceIdx = 0
                        while (sourceIdx < rawChunk.size && isActive) {
                            val space = BYTES_PER_FRAME - bytesBuffered
                            val toCopy = Math.min(space, rawChunk.size - sourceIdx)
                            System.arraycopy(rawChunk, sourceIdx, chunkBuffer, bytesBuffered, toCopy)
                            bytesBuffered += toCopy
                            sourceIdx += toCopy

                            if (bytesBuffered == BYTES_PER_FRAME) {
                                if (leftChannelBufferQueue.size >= 30) {
                                    leftChannelBufferQueue.poll() // Drop oldest
                                }
                                leftChannelBufferQueue.offer(chunkBuffer.copyOf())
                                bytesBuffered = 0
                            }
                        }
                    } else {
                        // Empty queue, sleep briefly to prevent high CPU usage
                        delay(5)
                    }
                } catch (e: Exception) {
                    Log.e("DualStream", "Error in local capture loop", e)
                }
            }
        }

        // Loop 3: Mix and Playback Loop
        mixingJob = serviceScope.launch {
            audioTrack?.play()
            Log.d("DualStream", "AudioTrack play() called")

            var nextTime = System.currentTimeMillis()
            while (isActive) {
                val leftPcm = leftChannelBufferQueue.poll() ?: silenceBuffer
                
                // Buffer overflow/underflow handling:
                // If health is 100% (queue is full), drain faster.
                val rightPcm = if (jitterBuffer.bufferHealthPercent >= 100) {
                    jitterBuffer.poll() // Discard one frame
                    jitterBuffer.poll() // Take the next frame
                } else {
                    jitterBuffer.poll()
                }

                val mixed = audioMixer.mix(leftPcm, rightPcm)
                
                writeAudioToTrack(mixed)

                // Update Stats
                val leftLevel = audioMixer.calculateLeftLevel(leftPcm)
                val rightLevel = audioMixer.calculateRightLevel(rightPcm)
                
                _audioStats.value = _audioStats.value.copy(
                    leftChannelLevel = leftLevel,
                    rightChannelLevel = rightLevel,
                    bufferHealth = jitterBuffer.bufferHealthPercent,
                    packetsReceived = jitterBuffer.packetsReceived,
                    packetsDropped = jitterBuffer.packetsDropped,
                    bitrateKbps = 64 // constant for Opus configured profile
                )

                // Maintain 20ms frame timing
                nextTime += FRAME_SIZE_MS
                val sleepTime = nextTime - System.currentTimeMillis()
                if (sleepTime > 0) {
                    delay(sleepTime)
                } else {
                    nextTime = System.currentTimeMillis()
                }
            }
        }

        // Latency Measurement Job (PING/PONG)
        pingJob = serviceScope.launch {
            // Wait for connection to be stable
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

    private fun writeAudioToTrack(mixed: ByteArray) {
        val track = audioTrack ?: return
        try {
            val result = track.write(mixed, 0, mixed.size)
            if (result < 0) {
                Log.e("DualStream", "AudioTrack.write error: $result. Reinitializing AudioTrack.")
                reinitAudioTrack()
            }
        } catch (e: Exception) {
            Log.e("DualStream", "Exception during AudioTrack.write", e)
            reinitAudioTrack()
        }
    }

    private fun reinitAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        initAudioTrack()
        audioTrack?.play()
    }

    private fun stopPipelineLoops() {
        Log.d("DualStream", "Stopping Receiver pipeline loops")
        networkReceiveJob?.cancel()
        networkReceiveJob = null
        localCaptureJob?.cancel()
        localCaptureJob = null
        mixingJob?.cancel()
        mixingJob = null
        pingJob?.cancel()
        pingJob = null
        
        try {
            audioTrack?.stop()
        } catch (e: Exception) {}
        
        leftChannelBufferQueue.clear()
        jitterBuffer.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DualStream", "ReceiverForegroundService destroyed")
        stopPipelineLoops()
        serviceScope.cancel()

        exoPlayer?.release()
        exoPlayer = null
        
        audioTrack?.release()
        audioTrack = null
        
        nearbyConnectionManager.disconnect()
        opusDecoder.release()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        stopForeground(true)
    }
}
