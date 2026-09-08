package com.dualstream.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConstantsTest {

    @Test
    fun testMonoFrameBytes() {
        val expectedBytes = AudioConstants.SAMPLES_PER_FRAME * AudioConstants.BYTES_PER_SAMPLE
        assertEquals("MONO_FRAME_BYTES should be SAMPLES_PER_FRAME * BYTES_PER_SAMPLE", expectedBytes, AudioConstants.MONO_FRAME_BYTES)
        assertEquals("MONO_FRAME_BYTES should be exactly 1920", 1920, AudioConstants.MONO_FRAME_BYTES)
    }

    @Test
    fun testStereoFrameBytes() {
        val expectedBytes = AudioConstants.SAMPLES_PER_FRAME * 2 * AudioConstants.BYTES_PER_SAMPLE
        assertEquals("STEREO_FRAME_BYTES should be SAMPLES_PER_FRAME * 2 * BYTES_PER_SAMPLE", expectedBytes, AudioConstants.STEREO_FRAME_BYTES)
        assertEquals("STEREO_FRAME_BYTES should be exactly 3840", 3840, AudioConstants.STEREO_FRAME_BYTES)
    }

    @Test
    fun testFrameTimingMath() {
        // FRAME_SIZE_MS(20) * SAMPLE_RATE(48000) / 1000 == SAMPLES_PER_FRAME(960)
        val calculatedSamples = (AudioConstants.FRAME_SIZE_MS * AudioConstants.SAMPLE_RATE) / 1000
        assertEquals("Frame timing math is internally inconsistent", calculatedSamples, AudioConstants.SAMPLES_PER_FRAME)
    }
}
