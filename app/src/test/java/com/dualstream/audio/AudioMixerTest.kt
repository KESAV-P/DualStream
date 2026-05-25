package com.dualstream.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMixerTest {

    private val mixer = AudioMixer()

    @Test
    fun testMix_channelIsolationAndStereoDownmix() {
        // Create sample data for local PCM (Phone B):
        // Each sample is 16-bit little-endian.
        // We'll fill left and right samples with specific values.
        val localPcm = ByteArray(BYTES_PER_FRAME)
        // Set local (Phone B) samples: Left channel = 1000, Right channel = 2000
        // Downmix should be: (1000 + 2000) / 2 = 1500
        val localLeftVal = 1000.toShort()
        val localRightVal = 2000.toShort()
        val expectedLocalMixedVal = 1500.toShort()

        // Create sample data for received PCM (Phone A):
        // Set received (Phone A) samples: Left channel = 4000, Right channel = 6000
        // Downmix should be: (4000 + 6000) / 2 = 5000
        val receivedPcm = ByteArray(BYTES_PER_FRAME)
        val receivedLeftVal = 4000.toShort()
        val receivedRightVal = 6000.toShort()
        val expectedReceivedMixedVal = 5000.toShort()

        val totalSamples = BYTES_PER_FRAME / 4
        for (s in 0 until totalSamples) {
            // Write local (Phone B) stereo sample
            val localIdx = s * 4
            localPcm[localIdx] = (localLeftVal.toInt() and 0xFF).toByte()
            localPcm[localIdx + 1] = (localLeftVal.toInt() shr 8 and 0xFF).toByte()
            localPcm[localIdx + 2] = (localRightVal.toInt() and 0xFF).toByte()
            localPcm[localIdx + 3] = (localRightVal.toInt() shr 8 and 0xFF).toByte()

            // Write received (Phone A) stereo sample
            val recIdx = s * 4
            receivedPcm[recIdx] = (receivedLeftVal.toInt() and 0xFF).toByte()
            receivedPcm[recIdx + 1] = (receivedLeftVal.toInt() shr 8 and 0xFF).toByte()
            receivedPcm[recIdx + 2] = (receivedRightVal.toInt() and 0xFF).toByte()
            receivedPcm[recIdx + 3] = (receivedRightVal.toInt() shr 8 and 0xFF).toByte()
        }

        // Mix the two streams
        val output = mixer.mix(localPcm = localPcm, receivedPcm = receivedPcm)

        // Verify output:
        // - Left channel output (bytes 0, 1) = receivedPcm (Phone A) downmixed to mono
        // - Right channel output (bytes 2, 3) = localPcm (Phone B) downmixed to mono
        for (s in 0 until totalSamples) {
            val outIdx = s * 4

            // Read output Left channel value
            val outLeftLo = output[outIdx].toInt() and 0xFF
            val outLeftHi = output[outIdx + 1].toInt() shl 8
            val outLeftVal = (outLeftHi or outLeftLo).toShort()

            // Read output Right channel value
            val outRightLo = output[outIdx + 2].toInt() and 0xFF
            val outRightHi = output[outIdx + 3].toInt() shl 8
            val outRightVal = (outRightHi or outRightLo).toShort()

            assertEquals("Sample $s Left channel should match Phone A mixed mono", expectedReceivedMixedVal, outLeftVal)
            assertEquals("Sample $s Right channel should match Phone B mixed mono", expectedLocalMixedVal, outRightVal)
        }
    }

    @Test
    fun testMix_withMonoInputs() {
        val mixer = AudioMixer()
        // Mono input size is BYTES_PER_FRAME / 2
        val monoSize = BYTES_PER_FRAME / 2
        val localPcm = ByteArray(monoSize)
        val receivedPcm = ByteArray(monoSize)

        val localVal = 3000.toShort()
        val receivedVal = 7000.toShort()

        val monoSampleCount = monoSize / 2
        for (i in 0 until monoSampleCount) {
            val idx = i * 2
            localPcm[idx] = (localVal.toInt() and 0xFF).toByte()
            localPcm[idx + 1] = (localVal.toInt() shr 8 and 0xFF).toByte()

            receivedPcm[idx] = (receivedVal.toInt() and 0xFF).toByte()
            receivedPcm[idx + 1] = (receivedVal.toInt() shr 8 and 0xFF).toByte()
        }

        val output = mixer.mix(localPcm = localPcm, receivedPcm = receivedPcm)

        val totalSamples = BYTES_PER_FRAME / 4
        for (s in 0 until totalSamples) {
            val outIdx = s * 4

            // Read output Left channel value (Phone A)
            val outLeftLo = output[outIdx].toInt() and 0xFF
            val outLeftHi = output[outIdx + 1].toInt() shl 8
            val outLeftVal = (outLeftHi or outLeftLo).toShort()

            // Read output Right channel value (Phone B)
            val outRightLo = output[outIdx + 2].toInt() and 0xFF
            val outRightHi = output[outIdx + 3].toInt() shl 8
            val outRightVal = (outRightHi or outRightLo).toShort()

            assertEquals("Sample $s Left channel should match Phone A mono value", receivedVal, outLeftVal)
            assertEquals("Sample $s Right channel should match Phone B mono value", localVal, outRightVal)
        }
    }
}
