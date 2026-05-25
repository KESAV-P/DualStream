package com.dualstream.audio

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioMixer @Inject constructor() {

    fun mix(leftChannelPcm: ByteArray, rightChannelPcm: ByteArray): ByteArray {
        val outputBuffer = ByteArray(BYTES_PER_FRAME)
        var i = 0
        while (i < BYTES_PER_FRAME - 3) {
            // LEFT CHANNEL OUTPUT: take LEFT sample from leftChannelPcm
            if (i + 1 < leftChannelPcm.size) {
                outputBuffer[i] = leftChannelPcm[i]       // L_byte0
                outputBuffer[i + 1] = leftChannelPcm[i + 1]   // L_byte1
            } else {
                outputBuffer[i] = 0
                outputBuffer[i + 1] = 0
            }

            // RIGHT CHANNEL OUTPUT: take RIGHT sample from rightChannelPcm
            if (i + 3 < rightChannelPcm.size) {
                outputBuffer[i + 2] = rightChannelPcm[i + 2]  // R_byte0
                outputBuffer[i + 3] = rightChannelPcm[i + 3]  // R_byte1
            } else {
                outputBuffer[i + 2] = 0
                outputBuffer[i + 3] = 0
            }

            i += 4
        }
        return outputBuffer
    }

    fun calculateLevel(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until sampleCount) {
            val idx = i * 2
            if (idx + 1 >= pcm.size) break
            val sample = ((pcm[idx + 1].toInt() shl 8) or (pcm[idx].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
        }

        val rms = kotlin.math.sqrt(sum / sampleCount)
        val level = (rms / 32768.0).toFloat()
        return level.coerceIn(0f, 1f)
    }

    fun calculateLeftLevel(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < pcm.size - 1) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 4 // Skip right channel sample
        }
        if (count == 0) return 0f
        val rms = kotlin.math.sqrt(sum / count)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun calculateRightLevel(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        var count = 0
        var i = 2
        while (i < pcm.size - 1) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 4 // Skip left channel sample
        }
        if (count == 0) return 0f
        val rms = kotlin.math.sqrt(sum / count)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
