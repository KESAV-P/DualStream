package com.dualstream.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class AudioCaptureManager(private val mediaProjection: MediaProjection) {

    private val _isSilenceDetected = MutableStateFlow(false)
    val isSilenceDetected: StateFlow<Boolean> = _isSilenceDetected.asStateFlow()

    @SuppressLint("MissingPermission")
    val captureFlow: Flow<ByteArray> = flow {
        Log.d("DualStream", "Initializing AudioPlaybackCapture Configuration")
        
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioRecord = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(BYTES_PER_FRAME * 4)
            .build()

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("DualStream", "AudioRecord failed to initialize")
            throw IllegalStateException("AudioRecord initialization failed")
        }

        var consecutiveSilenceFrames = 0
        val silenceThresholdFrames = 3000 / FRAME_SIZE_MS // 3 seconds / 20ms = 150 frames

        try {
            audioRecord.startRecording()
            Log.d("DualStream", "AudioRecord started recording")

            val buffer = ByteArray(BYTES_PER_FRAME)
            while (currentCoroutineContext().isActive) {
                val readResult = audioRecord.read(buffer, 0, BYTES_PER_FRAME)
                if (readResult > 0) {
                    // Check for silence (all-zero PCM)
                    var isSilence = true
                    for (i in 0 until readResult) {
                        if (buffer[i] != 0.toByte()) {
                            isSilence = false
                            break
                        }
                    }

                    if (isSilence) {
                        consecutiveSilenceFrames++
                        if (consecutiveSilenceFrames >= silenceThresholdFrames) {
                            if (!_isSilenceDetected.value) {
                                Log.w("DualStream", "Audio silence detected for over 3 seconds - potential DRM protection")
                                _isSilenceDetected.value = true
                            }
                        }
                    } else {
                        consecutiveSilenceFrames = 0
                        if (_isSilenceDetected.value) {
                            _isSilenceDetected.value = false
                        }
                    }

                    // Emit a copy of the buffer to avoid mutability issues down the pipeline
                    emit(buffer.copyOf(readResult))
                } else if (readResult < 0) {
                    Log.e("DualStream", "Error reading from AudioRecord: $readResult")
                    break
                }
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
