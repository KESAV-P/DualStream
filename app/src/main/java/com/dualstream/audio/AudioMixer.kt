package com.dualstream.audio

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioMixer @Inject constructor() {

    /**
     * Mix two audio sources into a stereo output frame with strict channel isolation:
     *  - Left  channel output = Phone A's received audio (stereo downmixed to mono)
     *  - Right channel output = Phone B's local audio (mono or stereo downmixed to mono)
     *
     * Input PCM format: 16-bit little-endian samples.
     *  - [localPcm]     Phone B's local audio (ExoPlayer/WebView)
     *  - [receivedPcm]  Phone A's received audio from nearby network
     *
     * Output: stereo interleaved PCM [BYTES_PER_FRAME bytes] — L0,L1,R0,R1,...
     */
    fun mix(localPcm: ByteArray, receivedPcm: ByteArray): ByteArray {
        val output = ByteArray(BYTES_PER_FRAME)
        val totalStereoSamples = BYTES_PER_FRAME / 4  // each stereo sample = 4 bytes (L+R)

        val localIsMono = localPcm.size <= (BYTES_PER_FRAME / 2 + 16)
        val receivedIsMono = receivedPcm.size <= (BYTES_PER_FRAME / 2 + 16)

        for (s in 0 until totalStereoSamples) {
            val outByteIdx = s * 4  // position in output stereo interleaved buffer

            // ── LEFT OUTPUT: Phone A (received) downmixed to mono ──────────────────
            val leftSample = if (receivedIsMono) {
                val idx = s * 2
                if (idx + 1 < receivedPcm.size) {
                    readSample(receivedPcm, idx)
                } else {
                    0.toShort()
                }
            } else {
                val idx = s * 4
                if (idx + 3 < receivedPcm.size) {
                    val lSample = readSample(receivedPcm, idx)
                    val rSample = readSample(receivedPcm, idx + 2)
                    ((lSample.toLong() + rSample.toLong()) / 2L).toShort()
                } else {
                    0.toShort()
                }
            }
            writeSample(output, outByteIdx, leftSample)      // L bytes of output

            // ── RIGHT OUTPUT: Phone B (local) downmixed to mono ─────────────
            val rightSample = if (localIsMono) {
                val idx = s * 2
                if (idx + 1 < localPcm.size) {
                    readSample(localPcm, idx)
                } else {
                    0.toShort()
                }
            } else {
                val idx = s * 4
                if (idx + 3 < localPcm.size) {
                    val lSample = readSample(localPcm, idx)
                    val rSample = readSample(localPcm, idx + 2)
                    ((lSample.toLong() + rSample.toLong()) / 2L).toShort()
                } else {
                    0.toShort()
                }
            }
            writeSample(output, outByteIdx + 2, rightSample) // R bytes of output
        }

        return output
    }

    /** Read a 16-bit little-endian sample from [buf] at [byteOffset]. */
    private fun readSample(buf: ByteArray, byteOffset: Int): Short {
        val lo = buf[byteOffset].toInt() and 0xFF
        val hi = buf[byteOffset + 1].toInt() shl 8
        return (hi or lo).toShort()
    }

    /** Write a 16-bit little-endian sample [value] into [buf] at [byteOffset]. */
    private fun writeSample(buf: ByteArray, byteOffset: Int, value: Short) {
        val v = value.toInt()
        buf[byteOffset]     = (v and 0xFF).toByte()
        buf[byteOffset + 1] = (v shr 8 and 0xFF).toByte()
    }

    // ── Level Meters ──────────────────────────────────────────────────────────

    fun calculateLevel(pcm: ByteArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return 0f
        for (i in 0 until sampleCount) {
            val idx = i * 2
            if (idx + 1 >= pcm.size) break
            val sample = readSample(pcm, idx)
            sum += sample.toDouble() * sample.toDouble()
        }
        val rms = kotlin.math.sqrt(sum / sampleCount)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    /** Level of left channel in a stereo interleaved buffer. */
    fun calculateLeftLevel(pcm: ByteArray): Float {
        if (pcm.size < 4) return 0f
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = readSample(pcm, i)
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 4 // stride over right channel sample
        }
        if (count == 0) return 0f
        val rms = kotlin.math.sqrt(sum / count)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    /** Level of right channel in a stereo interleaved buffer. */
    fun calculateRightLevel(pcm: ByteArray): Float {
        if (pcm.size < 4) return 0f
        var sum = 0.0
        var count = 0
        var i = 2
        while (i + 1 < pcm.size) {
            val sample = readSample(pcm, i)
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 4 // stride over next left channel sample
        }
        if (count == 0) return 0f
        val rms = kotlin.math.sqrt(sum / count)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
