package com.dualstream.model

data class AudioStats(
    val leftChannelLevel: Float = 0f,    // 0.0 to 1.0
    val rightChannelLevel: Float = 0f,   // 0.0 to 1.0
    val latencyMs: Long = 0L,
    val bufferHealth: Int = 0,           // jitter buffer fill percentage
    val bitrateKbps: Int = 0,
    val packetsReceived: Long = 0L,
    val packetsDropped: Long = 0L
)
