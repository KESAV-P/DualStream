package com.dualstream.network

import java.io.InputStream

class StreamReceiver(private val inputStream: InputStream) {
    
    fun readFrame(): ByteArray? {
        try {
            // Read 2-byte big-endian size prefix
            val header = ByteArray(2)
            var bytesRead = 0
            while (bytesRead < 2) {
                val result = inputStream.read(header, bytesRead, 2 - bytesRead)
                if (result == -1) return null // EOF reached
                bytesRead += result
            }
            
            val size = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
            if (size <= 0) return null
            
            val frame = ByteArray(size)
            var frameBytesRead = 0
            while (frameBytesRead < size) {
                val result = inputStream.read(frame, frameBytesRead, size - frameBytesRead)
                if (result == -1) return null // EOF reached during frame read
                frameBytesRead += result
            }
            return frame
        } catch (e: Exception) {
            return null
        }
    }
}
