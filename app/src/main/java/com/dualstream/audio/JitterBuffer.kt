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
        return try {
            queue.poll(40, TimeUnit.MILLISECONDS) ?: silenceBuffer
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            silenceBuffer
        }
    }

    fun clear() {
        queue.clear()
        _packetsReceived.set(0)
        _packetsDropped.set(0)
    }
}
