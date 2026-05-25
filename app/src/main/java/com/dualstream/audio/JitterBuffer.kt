package com.dualstream.audio

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JitterBuffer @Inject constructor() {
    private val capacity = 15
    private val queue = LinkedBlockingDeque<ByteArray>(capacity)
    private val silenceBuffer = ByteArray(BYTES_PER_FRAME)

    @Volatile
    private var isBuffering = true

    private val _packetsReceived = AtomicLong(0L)
    private val _packetsDropped = AtomicLong(0L)

    val packetsReceived: Long
        get() = _packetsReceived.get()

    val packetsDropped: Long
        get() = _packetsDropped.get()

    val bufferHealthPercent: Int
        get() = ((queue.size.toDouble() / capacity) * 100).toInt()

    val averageLatencyMs: Long
        get() = (queue.size * FRAME_SIZE_MS).toLong()

    fun offer(frame: ByteArray) {
        if (queue.size >= capacity) {
            queue.poll() // Drop oldest frame (head of queue)
            _packetsDropped.incrementAndGet()
        }
        queue.offer(frame)
        _packetsReceived.incrementAndGet()
    }

    fun poll(): ByteArray {
        if (isBuffering) {
            if (queue.size < 3) {
                return silenceBuffer
            } else {
                isBuffering = false
            }
        }

        val frame = queue.poll()
        if (frame == null) {
            isBuffering = true
            return silenceBuffer
        }
        return frame
    }

    fun clear() {
        queue.clear()
        isBuffering = true
        _packetsReceived.set(0)
        _packetsDropped.set(0)
    }
}
