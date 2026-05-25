package com.dualstream.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

class OpusCodec {
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    fun initEncoder() {
        try {
            // Encode stereo at 128kbps for high quality.
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_COMPLEXITY, 5)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            Log.d("DualStream", "Opus Encoder initialized (stereo, 128kbps)")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to initialize Opus Encoder", e)
        }
    }

    fun initDecoder() {
        try {
            // Decode stereo — matches the stereo encoder on Sender.
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNEL_COUNT)
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(format, null, null, 0)
                start()
            }
            Log.d("DualStream", "Opus Decoder initialized (stereo)")
        } catch (e: Exception) {
            Log.e("DualStream", "Failed to initialize Opus Decoder", e)
        }
    }

    @Synchronized
    fun encode(pcmBytes: ByteArray): ByteArray? {
        val codec = encoder ?: return null
        try {
            val inputBufferIndex = codec.dequeueInputBuffer(1000) // 1ms — fast non-blocking response
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(pcmBytes)
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        pcmBytes.size,
                        System.nanoTime() / 1000,
                        0
                    )
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000) // 1ms timeout
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(outData)
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    return outData
                }
            }
        } catch (e: Exception) {
            Log.e("DualStream", "Error during Opus encoding", e)
        }
        return null
    }

    @Synchronized
    fun decode(opusBytes: ByteArray): ByteArray? {
        val codec = decoder ?: return null
        try {
            val inputBufferIndex = codec.dequeueInputBuffer(1000) // 1ms — fast non-blocking response
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(opusBytes)
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        opusBytes.size,
                        System.nanoTime() / 1000,
                        0
                    )
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000) // 1ms timeout
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null) {
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(outData)
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    return outData
                }
            }
        } catch (e: Exception) {
            Log.e("DualStream", "Error during Opus decoding", e)
        }
        return null
    }

    fun releaseEncoder() {
        try {
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            Log.e("DualStream", "Error releasing Opus encoder", e)
        } finally {
            encoder = null
        }
    }

    fun releaseDecoder() {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {
            Log.e("DualStream", "Error releasing Opus decoder", e)
        } finally {
            decoder = null
        }
    }
}

@Singleton
class OpusEncoder @Inject constructor() {
    private val codec = OpusCodec().apply { initEncoder() }
    
    fun encode(pcmBytes: ByteArray): ByteArray? {
        return codec.encode(pcmBytes)
    }

    fun release() {
        codec.releaseEncoder()
    }
}

@Singleton
class OpusDecoder @Inject constructor() {
    private val codec = OpusCodec().apply { initDecoder() }
    
    fun decode(opusBytes: ByteArray): ByteArray? {
        return codec.decode(opusBytes)
    }

    fun release() {
        codec.releaseDecoder()
    }
}
