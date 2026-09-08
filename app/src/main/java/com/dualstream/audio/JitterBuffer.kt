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
    
    private var highBufferFrames = 0L

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
    fun poll(): ByteArray? {
        val frame = queue.pollFirst()
        if (frame == null) {
            // Log starvation distinctly from general debugs, but rate limit it
            if (_packetsReceived.get() > 0 && Math.random() < 0.05) {
                android.util.Log.w("DualStream", "JitterBuffer STARVATION: underrun detected")
            }
            return null
        }
        
        // Adaptive jitter buffer: if constantly full, drop a frame to reduce latency
        if (bufferHealthPercent > 80) {
            highBufferFrames++
            if (highBufferFrames > 100) { // >2 seconds of high buffer
                android.util.Log.w("DualStream", "JitterBuffer ADAPTIVE DROP: reducing latency")
                queue.pollFirst() // drop one extra frame
                _packetsDropped.incrementAndGet()
                highBufferFrames = 0L
            }
        } else {
            highBufferFrames = 0L
        }
        
        return frame
    }

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

