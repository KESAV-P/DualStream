package com.dualstream.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue

class LocalAudioProcessor : AudioProcessor {
    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    private var isActive = false
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Queue of PCM byte arrays for the receiver's local loop
    val pcmQueue = LinkedBlockingQueue<ByteArray>(200)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        inputFormat = inputAudioFormat
        // We only activate if the input encoding is 16-bit PCM (standard)
        isActive = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        outputFormat = inputAudioFormat
        return outputFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bytes = ByteArray(remaining)
        val position = inputBuffer.position()
        inputBuffer.get(bytes)
        inputBuffer.position(position) // Restore position for pass-through

        if (pcmQueue.size >= 200) {
            pcmQueue.poll() // Drop oldest to prevent memory leakage/overflow
        }
        pcmQueue.offer(bytes)

        // Set up output buffer
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer == AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        isActive = false
        pcmQueue.clear()
    }
}
