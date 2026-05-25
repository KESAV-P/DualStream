package com.dualstream.network

import java.io.OutputStream

class StreamSender(private val outputStream: OutputStream) {
    
    @Synchronized
    fun sendFrame(frame: ByteArray) {
        val size = frame.size
        if (size <= 0) return
        
        // 2-byte big-endian size prefix
        val header = ByteArray(2)
        header[0] = (size shr 8 and 0xFF).toByte()
        header[1] = (size and 0xFF).toByte()
        
        outputStream.write(header)
        outputStream.write(frame)
        outputStream.flush()
    }
}
