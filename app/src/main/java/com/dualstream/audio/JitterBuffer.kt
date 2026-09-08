package com.dualstream.audio

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JitterBuffer @Inject constructor() {

    private val CAPACITY = 15  // 300ms at 20ms/frame

    private val queue = LinkedBlockingDeque<ByteArray>(CAPACITY)

    private val _packetsReceived = AtomicLong(0L)
    private val _packetsDropped = AtomicLong(0L)

    val packetsReceived: Long
        get() = _packetsReceived.get()

    val packetsDropped: Long
        get() = _packetsDropped.get()

    // WRITE side — called by StreamReceiver on network thread
    fun write(monoFrame: ByteArray) {
        if (!queue.offerLast(monoFrame)) {
            // Buffer full — drop OLDEST frame to make room for newest
            queue.pollFirst()
            _packetsDropped.incrementAndGet()
            queue.offerLast(monoFrame)
        }
        _packetsReceived.incrementAndGet()
    }

    // READ side — called by mixing thread (non-blocking)
    fun poll(): ByteArray? = queue.pollFirst()

    // Buffer health as percentage (for UI display)
    val bufferHealthPercent: Int
        get() = ((queue.size.toFloat() / CAPACITY) * 100).toInt()

    // Estimated latency in ms (queue depth × frame duration)
    val estimatedLatencyMs: Long
        get() = (queue.size * 20L)

    fun clear() {
        queue.clear()
        _packetsReceived.set(0L)
        _packetsDropped.set(0L)
    }
}

