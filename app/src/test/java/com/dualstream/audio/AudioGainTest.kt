package com.dualstream.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class AudioGainTest {

    private lateinit var classUnderTest: AudioGainCalculator

    @Before
    fun setup() {
        classUnderTest = AudioGainCalculator(targetPeakRatio = 0.9f, attack = 0.3f, release = 0.05f)
    }

    @Test
    fun testApplyGainCompensation_NearSilentFrame_RaisesAmplitude() {
        // Create a quiet frame (max amplitude 1000 out of 32767)
        val frame = ByteArray(1920)
        for (i in 0 until 960) {
            val sample = 1000.toShort()
            frame[i * 2] = (sample.toInt() and 0xFF).toByte()
            frame[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }

        classUnderTest.applyGainCompensation(frame)

        // Find new peak
        var peak = 0
        for (i in 0 until 960) {
            val low = frame[i * 2].toInt() and 0xFF
            val high = frame[i * 2 + 1].toInt() shl 8
            val sample = (low or high).toShort()
            peak = maxOf(peak, abs(sample.toInt()))
        }

        // Expected target gain is 15.0f (cap) because 1000/32768 is ~0.03, and 0.9/0.03 is 30x.
        // Current gain starts at 1.0. Next gain = 1.0 + (15.0 - 1.0) * 0.3 = 5.2x
        // New peak should be 1000 * 5.2 = 5200
        assertTrue("Amplitude should be raised. Peak was $peak", peak > 5000 && peak < 6000)
    }

    @Test
    fun testGainSmoothing_AvoidsInstantJumps() {
        val frame = ByteArray(1920) { 0 }
        // Frame with amplitude 1000
        for (i in 0 until 960) {
            val sample = 1000.toShort()
            frame[i * 2] = (sample.toInt() and 0xFF).toByte()
            frame[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }

        val initialGain = classUnderTest.currentGain
        classUnderTest.applyGainCompensation(frame)
        val secondGain = classUnderTest.currentGain
        
        // It shouldn't jump instantly to 15.0f
        assertTrue("Gain smoothed upward: $secondGain", secondGain > initialGain && secondGain < 6.0f)
        
        classUnderTest.applyGainCompensation(frame)
        val thirdGain = classUnderTest.currentGain
        assertTrue("Gain smoothed upward again: $thirdGain", thirdGain > secondGain)
    }

    @Test
    fun testGainCompensation_NeverClips() {
        // Create full scale frame
        val frame = ByteArray(1920)
        for (i in 0 until 960) {
            val sample = Short.MAX_VALUE
            frame[i * 2] = (sample.toInt() and 0xFF).toByte()
            frame[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }
        
        // Force current gain artificially high (simulating a sudden loud noise after silence)
        val field = AudioGainCalculator::class.java.getDeclaredField("currentGain")
        field.isAccessible = true
        field.set(classUnderTest, 10.0f)
        
        classUnderTest.applyGainCompensation(frame)
        
        var hasClippingDistortion = false
        for (i in 0 until 960) {
            val low = frame[i * 2].toInt() and 0xFF
            val high = frame[i * 2 + 1].toInt() shl 8
            val sample = (low or high).toShort()
            
            // If it rolled over due to integer overflow, it would become negative or smaller.
            // Short.MAX_VALUE clamped should remain Short.MAX_VALUE.
            if (sample != Short.MAX_VALUE) {
                hasClippingDistortion = true
            }
        }
        
        assertTrue("Should not have integer overflow clipping distortion", !hasClippingDistortion)
    }

    @Test
    fun testGainCompensation_FullScale_AppliesNoUnnecessaryGain() {
        // Feed 10 full scale frames
        for (f in 0 until 10) {
            val frame = ByteArray(1920)
            for (i in 0 until 960) {
                val sample = 30000.toShort() // near target Peak Ratio
                frame[i * 2] = (sample.toInt() and 0xFF).toByte()
                frame[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
            }
            classUnderTest.applyGainCompensation(frame)
        }
        
        // Gain should settle near 1.0 (actually around 32768*0.9 / 30000 = 0.98, capped to 1.0f or similar)
        // Let's see: targetPeakRatio = 0.9 (29491). Peak = 30000 (0.915).
        // TargetGain = 0.9 / 0.915 = 0.98.
        // It should drop to near 0.98.
        val finalGain = classUnderTest.currentGain
        assertTrue("Gain should drop back to near 1.0 when audio is loud (was $finalGain)", finalGain < 1.1f)
    }
}
