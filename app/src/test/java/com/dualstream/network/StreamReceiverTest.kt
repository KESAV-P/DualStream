package com.dualstream.network

import com.google.android.gms.nearby.connection.Payload
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class StreamReceiverTest {

    private lateinit var classUnderTest: StreamReceiver

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        classUnderTest = StreamReceiver()
    }

    @Test
    fun testOnPayloadReceived_ControlMessage_IsNeverMisroutedAsAudio() {
        // Construct a BYTES payload that happens to be exactly 1920 bytes long
        val fakeJsonBytes = ByteArray(1920) { 'a'.code.toByte() }
        val payload = mockk<Payload>(relaxed = true)
        every { payload.type } returns Payload.Type.BYTES
        every { payload.asBytes() } returns fakeJsonBytes

        var controlMessage: String? = null
        var audioFrame: ByteArray? = null

        classUnderTest.onControlMessageReceived = { controlMessage = it }
        classUnderTest.onAudioFrameReceived = { audioFrame = it }

        classUnderTest.onPayloadReceived("endpointA", payload)

        // It should ONLY trigger control message, not audio frame
        assertTrue("Control message should not be null", controlMessage != null)
        assertNull("Audio frame should be null, even if the payload is 1920 bytes", audioFrame)
        assertEquals(String(fakeJsonBytes, Charsets.UTF_8), controlMessage)
    }

    @Test
    fun testOnPayloadReceived_StreamPayload_EmitsAudioFrames() = runBlocking {
        val payload = mockk<Payload>(relaxed = true)
        every { payload.type } returns Payload.Type.STREAM

        // Create a dummy stream with 2 frames (1920 * 2)
        val frameBytes = 1920
        val dummyData = ByteArray(frameBytes * 2)
        for (i in dummyData.indices) {
            dummyData[i] = (i % 255).toByte()
        }
        val inputStream = ByteArrayInputStream(dummyData)

        val streamObj = mockk<Payload.Stream>(relaxed = true)
        every { streamObj.asInputStream() } returns inputStream
        every { payload.asStream() } returns streamObj

        val receivedFrames = mutableListOf<ByteArray>()
        classUnderTest.onAudioFrameReceived = { receivedFrames.add(it) }

        classUnderTest.onPayloadReceived("endpointA", payload)

        // It launches a coroutine to read the stream, so we delay briefly to allow it to finish
        delay(100)

        assertEquals("Should receive exactly 2 frames", 2, receivedFrames.size)
        assertEquals("First frame size", frameBytes, receivedFrames[0].size)
        assertEquals("Second frame size", frameBytes, receivedFrames[1].size)

        // Verify content of the second frame
        assertEquals((frameBytes % 255).toByte(), receivedFrames[1][0])
    }
}
