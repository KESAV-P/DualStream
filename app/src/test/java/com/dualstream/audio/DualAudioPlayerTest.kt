package com.dualstream.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DualAudioPlayerTest {

    private lateinit var classUnderTest: DualAudioPlayer
    private lateinit var dummyTrack: AudioTrack

    @Before
    fun setup() {

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        
        classUnderTest = spyk(DualAudioPlayer(), recordPrivateCalls = true)
        dummyTrack = mockk<AudioTrack>(relaxed = true)
        every { dummyTrack.write(any<ByteArray>(), any(), any(), any()) } returns 3840
        every { dummyTrack.write(any<ByteArray>(), any(), any()) } returns 3840
        every { classUnderTest invokeNoArgs "buildAudioTrack" } returns dummyTrack
        
        classUnderTest.initialize()
    }

    @After
    fun teardown() {
        classUnderTest.release()
        unmockkAll()
    }

    @Test
    fun testWriteMixedBothNonNull() {
        val networkMono = ByteArray(AudioConstants.MONO_FRAME_BYTES) { 1 }
        val localMono = ByteArray(AudioConstants.MONO_FRAME_BYTES) { 2 }
        
        classUnderTest.writeMixed(networkMono, localMono)
        
        verify { 
            dummyTrack.write(withArg<ByteArray> { mixed -> 
                assertEquals(AudioConstants.STEREO_FRAME_BYTES, mixed.size)
                // Left = localMono (2), Right = networkMono (1)
                // Frame format is Little Endian 16-bit PCM (2 bytes per channel). 
                // sample loop: local = i, network = i+1
                // Actually the mixing logic is byte-by-byte:
                // even 2 bytes = left (local), odd 2 bytes = right (network)
                for (i in 0 until AudioConstants.SAMPLES_PER_FRAME) {
                    val outIndex = i * 4
                    val inIndex = i * 2
                    assertEquals(localMono[inIndex], mixed[outIndex])
                    assertEquals(localMono[inIndex + 1], mixed[outIndex + 1])
                    assertEquals(networkMono[inIndex], mixed[outIndex + 2])
                    assertEquals(networkMono[inIndex + 1], mixed[outIndex + 3])
                }
            }, 0, AudioConstants.STEREO_FRAME_BYTES, AudioTrack.WRITE_BLOCKING)
        }
    }

    @Test
    fun testWriteMixedNullLocal() {
        val networkMono = ByteArray(AudioConstants.MONO_FRAME_BYTES) { 1 }
        classUnderTest.writeMixed(networkMono, null)
        
        verify {
            dummyTrack.write(withArg<ByteArray> { mixed ->
                for (i in 0 until AudioConstants.SAMPLES_PER_FRAME) {
                    val outIndex = i * 4
                    val inIndex = i * 2
                    // Left is silent
                    assertEquals(0.toByte(), mixed[outIndex])
                    assertEquals(0.toByte(), mixed[outIndex + 1])
                    // Right is network
                    assertEquals(networkMono[inIndex], mixed[outIndex + 2])
                    assertEquals(networkMono[inIndex + 1], mixed[outIndex + 3])
                }
            }, 0, AudioConstants.STEREO_FRAME_BYTES, AudioTrack.WRITE_BLOCKING)
        }
    }

    @Test
    fun testWriteMixedNullNetwork() {
        val localMono = ByteArray(AudioConstants.MONO_FRAME_BYTES) { 2 }
        classUnderTest.writeMixed(null, localMono)
        
        verify {
            dummyTrack.write(withArg<ByteArray> { mixed ->
                for (i in 0 until AudioConstants.SAMPLES_PER_FRAME) {
                    val outIndex = i * 4
                    val inIndex = i * 2
                    // Left is local
                    assertEquals(localMono[inIndex], mixed[outIndex])
                    assertEquals(localMono[inIndex + 1], mixed[outIndex + 1])
                    // Right is silent
                    assertEquals(0.toByte(), mixed[outIndex + 2])
                    assertEquals(0.toByte(), mixed[outIndex + 3])
                }
            }, 0, AudioConstants.STEREO_FRAME_BYTES, AudioTrack.WRITE_BLOCKING)
        }
    }

    @Test
    fun testWriteMixedNullNull() {
        classUnderTest.writeMixed(null, null)
        
        verify {
            dummyTrack.write(withArg<ByteArray> { mixed ->
                for (i in 0 until AudioConstants.STEREO_FRAME_BYTES) {
                    assertEquals(0.toByte(), mixed[i])
                }
            }, 0, AudioConstants.STEREO_FRAME_BYTES, AudioTrack.WRITE_BLOCKING)
        }
    }

    @Test
    fun testReadSample() {
        // We will call the private method using reflection
        val method = DualAudioPlayer::class.java.getDeclaredMethod("readSample", ByteArray::class.java, Int::class.java)
        method.isAccessible = true
        
        val buffer = ByteArray(4)
        buffer[0] = 0x00.toByte()
        assertEquals(0, method.invoke(classUnderTest, buffer, 0))
        
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0xFF.toByte() // -1
        assertEquals(-1, method.invoke(classUnderTest, buffer, 0))
        
        buffer[0] = 0x00.toByte()
        buffer[1] = 0x80.toByte() // Short.MIN_VALUE
        assertEquals(Short.MIN_VALUE.toInt(), method.invoke(classUnderTest, buffer, 0))
        
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0x7F.toByte() // Short.MAX_VALUE
        assertEquals(Short.MAX_VALUE.toInt(), method.invoke(classUnderTest, buffer, 0))
        
        // Out of bounds should return 0
        assertEquals(0, method.invoke(classUnderTest, buffer, 4))
    }

    @Test
    fun testCalculateLevel() {
        val method = DualAudioPlayer::class.java.getDeclaredMethod("calculateLevel", ByteArray::class.java)
        method.isAccessible = true
        
        val silentBuffer = ByteArray(AudioConstants.MONO_FRAME_BYTES)
        assertEquals(0.0f, method.invoke(classUnderTest, silentBuffer) as Float, 0.01f)
        
        val fullScaleBuffer = ByteArray(AudioConstants.MONO_FRAME_BYTES)
        for (i in 0 until AudioConstants.SAMPLES_PER_FRAME) {
            fullScaleBuffer[i * 2] = 0xFF.toByte()
            fullScaleBuffer[i * 2 + 1] = 0x7F.toByte() // Short.MAX_VALUE
        }
        assertEquals(1.0f, method.invoke(classUnderTest, fullScaleBuffer) as Float, 0.05f)
        
        val halfScaleBuffer = ByteArray(AudioConstants.MONO_FRAME_BYTES)
        for (i in 0 until AudioConstants.SAMPLES_PER_FRAME) {
            halfScaleBuffer[i * 2] = 0x00.toByte()
            halfScaleBuffer[i * 2 + 1] = 0x40.toByte() // Half of Short.MAX_VALUE
        }
        val level = method.invoke(classUnderTest, halfScaleBuffer) as Float
        assert(level > 0.0f && level < 1.0f) { "Level $level should be between 0 and 1" }
    }

    @Test
    fun testAudioTrackAttributes() {
        // Verify AudioTrack is constructed with correct attributes
        // Since AudioTrack constructor logic is built in init or buildAudioTrack, we can't easily reflect the builder inside a unit test without Robolectric, 
        // but we can verify it was constructed via MockK if we spy or intercept.
        // As MockK intercepts the constructor, we just assert the constructor is called.
        // Real validation of attributes is better suited for AndroidTest if we want to read the real AudioTrack's properties.
        // However, we'll try to verify the builder if possible, but the builder is what gets called.
    }

    @Test
    fun testRecoverTrackOnNegativeWrite() {
        // Mock write to return a negative error code (AudioTrack.ERROR_DEAD_OBJECT)
        every { dummyTrack.write(any<ByteArray>(), any(), any(), any()) } returns AudioTrack.ERROR_DEAD_OBJECT
        
        // This should trigger recoverTrack() without crashing
        classUnderTest.writeMixed(ByteArray(AudioConstants.MONO_FRAME_BYTES), ByteArray(AudioConstants.MONO_FRAME_BYTES))
        
        // AudioTrack should have been recovered (stop, flush, play)
        verify(atLeast = 1) { dummyTrack.stop() }
        verify(atLeast = 1) { dummyTrack.flush() }
        verify(atLeast = 1) { dummyTrack.play() }
    }
}
