package com.dualstream.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DualAudioPlayer @Inject constructor() {

    companion object {
        private const val TAG = "DualStream"
        private const val SAMPLE_RATE = 48000
        private const val MONO_FRAME_BYTES = 1920    // 960 samples × 2 bytes
        private const val STEREO_FRAME_BYTES = 3840  // 960 samples × 2ch × 2 bytes
        private const val SAMPLES_PER_FRAME = 960

    }

    private var audioTrack: AudioTrack? = null
    private var debugFrameCount = 0

    fun initialize() {
        audioTrack = buildAudioTrack()
        audioTrack?.play()
        Log.d(TAG, "DualAudioPlayer initialized with single stereo AudioTrack")
    }

    /**
     * Takes two nullable MONO PCM frames (1920 bytes each).
     * Constructs a STEREO output (3840 bytes) with weighted cross-mix.
     * Writes to AudioTrack with WRITE_BLOCKING (this is the clock).
     */
    fun writeMixed(networkMono: ByteArray?, localMono: ByteArray?) {
        val track = audioTrack ?: return
        val output = ByteArray(STEREO_FRAME_BYTES)

        // DEBUG: Log every 50th frame to verify non-zero signals
        if (debugFrameCount % 50 == 0) {
            Log.d(TAG, "writeMixed frame $debugFrameCount — " +
                    "net size: ${networkMono?.size ?: 0} (null: ${networkMono == null}) — " +
                    "loc size: ${localMono?.size ?: 0} (null: ${localMono == null})")
        }
        debugFrameCount++

        for (s in 0 until SAMPLES_PER_FRAME) {
            val net = readSample(networkMono, s)
            val loc = readSample(localMono, s)

            val left  = loc
            val right = net

            val outIdx = s * 4
            // LEFT sample (little-endian)
            output[outIdx] = (left and 0xFF).toByte()
            output[outIdx + 1] = ((left ushr 8) and 0xFF).toByte()
            // RIGHT sample (little-endian)
            output[outIdx + 2] = (right and 0xFF).toByte()
            output[outIdx + 3] = ((right ushr 8) and 0xFF).toByte()
        }

        val result = track.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
        if (result < 0) {
            Log.e(TAG, "AudioTrack write error: $result")
            recoverTrack()
        }
    }

    private fun readSample(mono: ByteArray?, index: Int): Int {
        if (mono == null || index * 2 + 1 >= mono.size) return 0
        val base = index * 2
        return ((mono[base].toInt() and 0xFF) or (mono[base + 1].toInt() shl 8)).toShort().toInt()
    }

    private fun recoverTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.flush()
            audioTrack?.play()
            Log.d(TAG, "AudioTrack recovered")
        } catch (e: Exception) {
            Log.e(TAG, "Track recovery failed: ${e.message}")
        }
    }

    private fun buildAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, STEREO_FRAME_BYTES * 4)

        Log.d(TAG, "Building mixed track — buffer: $bufferSize bytes")

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            .let { builder ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                    builder.setSpatializationBehavior(AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER)
                }
                builder
            }
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun calculateLevel(pcmBytes: ByteArray): Float {
        if (pcmBytes.isEmpty()) return 0f
        var sumSquares = 0.0
        var i = 0
        while (i < pcmBytes.size - 1) {
            val sample = (pcmBytes[i].toInt() and 0xFF) or
                         (pcmBytes[i + 1].toInt() shl 8)
            val normalized = sample.toShort().toDouble() / 32768.0
            sumSquares += normalized * normalized
            i += 2
        }
        return kotlin.math.sqrt(sumSquares / (pcmBytes.size / 2))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        } finally {
            audioTrack = null
        }
        Log.d(TAG, "DualAudioPlayer released")
    }
}
