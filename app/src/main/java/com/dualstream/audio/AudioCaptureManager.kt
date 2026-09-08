package com.dualstream.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class AudioCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {

    private val _isSilenceDetected = MutableStateFlow(false)
    val isSilenceDetected: StateFlow<Boolean> = _isSilenceDetected.asStateFlow()

    private var isPaused = false

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    @SuppressLint("MissingPermission")
    val captureFlow: Flow<ByteArray> = flow {
        Log.d("DualStream", "Initializing AudioPlaybackCapture — MONO 48kHz 16-bit")

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(context.applicationInfo.uid) // Prevent feedback loop
            .build()

        // *** Use MONO directly — eliminates stereo→mono downmix variable-size bug ***
        // AudioPlaybackCaptureConfiguration supports CHANNEL_IN_MONO
        val minBuf = AudioRecord.getMinBufferSize(
            com.dualstream.audio.AudioConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, com.dualstream.audio.AudioConstants.MONO_FRAME_BYTES * 4)

        val audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(com.dualstream.audio.AudioConstants.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .build()

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("DualStream", "AudioRecord failed to initialize — state=${audioRecord.state}")
            throw IllegalStateException("AudioRecord initialization failed")
        }

        Log.d("DualStream", "AudioRecord initialized — bufSize=$bufSize")

        var consecutiveSilenceFrames = 0
        val silenceThresholdFrames = 3000 / com.dualstream.audio.AudioConstants.FRAME_SIZE_MS
        val MONO_BYTES = com.dualstream.audio.AudioConstants.MONO_FRAME_BYTES // 1920

        try {
            audioRecord.startRecording()
            Log.d("DualStream", "AudioRecord started recording (MONO)")

            val monoBuffer = ByteArray(MONO_BYTES)
            var totalFramesEmitted = 0L

            while (currentCoroutineContext().isActive) {
                if (isPaused) {
                    delay(com.dualstream.audio.AudioConstants.FRAME_SIZE_MS.toLong())
                    continue
                }

                // READ_BLOCKING guarantees we always get exactly MONO_BYTES
                // This ensures the stream is always framed correctly on the receiver
                var totalRead = 0
                while (totalRead < MONO_BYTES && currentCoroutineContext().isActive) {
                    val n = audioRecord.read(
                        monoBuffer, totalRead, MONO_BYTES - totalRead,
                        AudioRecord.READ_BLOCKING
                    )
                    if (n < 0) {
                        Log.e("DualStream", "AudioRecord.read error code: $n")
                        return@flow
                    }
                    totalRead += n
                }

                if (totalRead < MONO_BYTES) break // coroutine cancelled mid-read

                // Silence detection
                var isSilence = true
                for (b in monoBuffer) {
                    if (b != 0.toByte()) { isSilence = false; break }
                }

                if (isSilence) {
                    consecutiveSilenceFrames++
                    if (consecutiveSilenceFrames >= silenceThresholdFrames && !_isSilenceDetected.value) {
                        Log.w("DualStream", "Audio silence for >3s — possible DRM block")
                        _isSilenceDetected.value = true
                    }
                } else {
                    consecutiveSilenceFrames = 0
                    if (_isSilenceDetected.value) _isSilenceDetected.value = false
                }

                totalFramesEmitted++
                if (totalFramesEmitted % 100 == 1L) {
                    Log.d("DualStream", "Capture frame #$totalFramesEmitted emitted — silence=$isSilence")
                }

                emit(monoBuffer.copyOf())
            }
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (e: Exception) {
                Log.e("DualStream", "Error stopping AudioRecord", e)
            }
            audioRecord.release()
            Log.d("DualStream", "AudioRecord released")
        }
    }
}
