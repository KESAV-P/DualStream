package com.dualstream.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class JitterBufferTest {

    private lateinit var classUnderTest: JitterBuffer

    @Before
    fun setup() {
        classUnderTest = JitterBuffer()
    }

    @Test
    fun testFifoOrder() {
        val frame1 = ByteArray(AudioConstants.MONO_FRAME_BYTES) { 1 }
        val frame2 = ByteArray(AudioConstants.MONO_FRAME_BYTES) { 2 }
        
        classUnderTest.write(frame1)
        classUnderTest.write(frame2)
        
        val read1 = classUnderTest.poll()
        val read2 = classUnderTest.poll()
        
        assertArrayEquals("First polled frame should equal first written", frame1, read1)
        assertArrayEquals("Second polled frame should equal second written", frame2, read2)
    }

    @Test
    fun testPollOnEmptyBuffer() {
        val read = classUnderTest.poll()
        assertNull("Polling empty buffer should return null", read)
    }

    @Test
    fun testDropOldestOnOverflow() {
        // Capacity is 15 frames
        for (i in 0 until 16) {
            classUnderTest.write(ByteArray(AudioConstants.MONO_FRAME_BYTES) { i.toByte() })
        }
        
        // Since we wrote 16 frames, frame 0 should be dropped. The first poll should return frame 1.
        val firstPoll = classUnderTest.poll()
        assertEquals("First polled frame should be frame 1 after frame 0 dropped", 1.toByte(), firstPoll?.get(0))
        assertEquals("packetsDropped should be 1", 1L, classUnderTest.packetsDropped)
    }

    @Test
    fun testBufferHealthPercent() {
        assertEquals("Health should be 0 when empty", 0, classUnderTest.bufferHealthPercent)
        
        for (i in 0 until 15) {
            classUnderTest.write(ByteArray(AudioConstants.MONO_FRAME_BYTES))
        }
        assertEquals("Health should be 100 when full", 100, classUnderTest.bufferHealthPercent)
        
        classUnderTest.poll()
        classUnderTest.poll()
        // 13 out of 15 is 86%
        assertEquals("Health should reflect partial fullness", (13 * 100) / 15, classUnderTest.bufferHealthPercent)
    }

    @Test
    fun testClear() {
        classUnderTest.write(ByteArray(AudioConstants.MONO_FRAME_BYTES))
        classUnderTest.poll() // increments received
        // write 16 to cause 1 drop
        for (i in 0 until 16) {
            classUnderTest.write(ByteArray(AudioConstants.MONO_FRAME_BYTES))
        }
        
        classUnderTest.clear()
        
        assertNull("Buffer should be empty", classUnderTest.poll())
        assertEquals("packetsReceived should be 0", 0L, classUnderTest.packetsReceived)
        assertEquals("packetsDropped should be 0", 0L, classUnderTest.packetsDropped)
        assertEquals("bufferHealthPercent should be 0", 0, classUnderTest.bufferHealthPercent)
    }

    @Test
    fun testConcurrentWriteAndPoll() {
        val iterations = 10000
        val latch = CountDownLatch(2)
        var polledCount = 0
        var writeException: Exception? = null
        var pollException: Exception? = null
        
        thread {
            try {
                for (i in 0 until iterations) {
                    classUnderTest.write(ByteArray(AudioConstants.MONO_FRAME_BYTES) { i.toByte() })
                }
            } catch (e: Exception) {
                writeException = e
            } finally {
                latch.countDown()
            }
        }
        
        thread {
            try {
                var nullCount = 0
                // Need to keep polling until we process or miss (it's concurrent, so it might return null if empty)
                // We'll just poll iterations times. It's fine if we miss some due to dropping or being empty.
                for (i in 0 until iterations * 2) {
                    val frame = classUnderTest.poll()
                    if (frame != null) polledCount++
                    else nullCount++
                }
            } catch (e: Exception) {
                pollException = e
            } finally {
                latch.countDown()
            }
        }
        
        latch.await()
        
        writeException?.printStackTrace()
        pollException?.printStackTrace()
        
        assertNull("No exception during concurrent write", writeException)
        assertNull("No exception during concurrent poll", pollException)
    }
}
