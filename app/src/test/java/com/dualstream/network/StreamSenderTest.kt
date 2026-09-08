package com.dualstream.network

import android.os.ParcelFileDescriptor
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class StreamSenderTest {

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var streamSender: StreamSender
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        connectionsClient = mockk(relaxed = true)
        every { connectionsClient.sendPayload(any<String>(), any<Payload>()) } returns mockk(relaxed = true)
        
        streamSender = io.mockk.spyk(StreamSender(connectionsClient))
        testScope = TestScope(StandardTestDispatcher())

        mockkStatic(ParcelFileDescriptor::class)
        val dummyFd1 = mockk<ParcelFileDescriptor>(relaxed = true)
        val dummyFd2 = mockk<ParcelFileDescriptor>(relaxed = true)
        every { ParcelFileDescriptor.createPipe() } returns arrayOf(dummyFd1, dummyFd2)

        mockkStatic(Payload::class)
        every { Payload.fromStream(any<ParcelFileDescriptor>()) } returns mockk(relaxed = true)
    }

    @After
    fun teardown() {
        streamSender.stopSending()
        testScope.cancel()
    }

    @Test
    fun testStartSending_SendsNFramesToPipe() = testScope.runTest {
        // We will capture the bytes written to the OutputStream
        val capturedBytes = ByteArrayOutputStream()
        val mockStream = mockk<java.io.OutputStream>(relaxed = true)
        every { mockStream.write(any<ByteArray>()) } answers {
            val arr = it.invocation.args[0] as ByteArray
            capturedBytes.write(arr)
        }
        
        every { streamSender.createOutputStream(any()) } returns mockStream

        val nFrames = 5
        val frameBytes = 1920
        val fakeFlow = flow {
            for (i in 0 until nFrames) {
                val frame = ByteArray(frameBytes) { i.toByte() }
                emit(frame)
            }
        }

        streamSender.startSending("endpointA", fakeFlow, CoroutineScope(testScope.coroutineContext + Dispatchers.Unconfined))
        
        // Let Dispatchers.IO execute
        Thread.sleep(200)
        testScheduler.advanceUntilIdle()

        // Verify that 5 frames of 1920 bytes each were written to the pipe
        val writtenData = capturedBytes.toByteArray()
        assertEquals("Should write exactly N * frameSize bytes", nFrames * frameBytes, writtenData.size)
        
        // Verify the 4th frame is correctly populated with 3s
        val startOf4th = 3 * frameBytes
        assertEquals(3.toByte(), writtenData[startOf4th])
    }

    @Test
    fun testStartSending_Backpressure_DropsOldestFrames() = testScope.runTest {
        val mockStream = mockk<java.io.OutputStream>(relaxed = true)
        
        var writesAttempted = 0
        every { mockStream.write(any<ByteArray>()) } answers {
            // Simulate a blocking write that prevents the consumer from keeping up
            writesAttempted++
            Thread.sleep(50) // Artificial delay
        }
        
        every { streamSender.createOutputStream(any()) } returns mockStream

        // The channel capacity is 25 in StreamSender.
        // We'll emit 50 frames very fast. The flow should NOT block.
        val nFrames = 50
        val frameBytes = 1920
        val fakeFlow = flow {
            for (i in 0 until nFrames) {
                emit(ByteArray(frameBytes) { i.toByte() })
            }
        }

        streamSender.startSending("endpointA", fakeFlow, CoroutineScope(testScope.coroutineContext + Dispatchers.Unconfined))
        
        // Let it run. It should complete emission almost instantly because of DROP_OLDEST
        testScheduler.advanceUntilIdle()
        
        // If it blocked, it would hang or take a long time. 
        // Because of Drop Oldest and our slow reader, writesAttempted will be much less than nFrames.
        assertTrue("Producer was not blocked, but consumer dropped frames. Attempted: $writesAttempted", writesAttempted < nFrames)
    }
}
