package com.dualstream.audio

import android.media.AudioTrack
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

class CustomPcmAudioSink(
    private val onPcmFrameReady: (ByteArray) -> Unit
) : AudioSink {

    private var isInitialized = false
    private var playbackStarted = false
    private var listener: AudioSink.Listener? = null
    private var channelCount = 2

    private val accumulator = java.io.ByteArrayOutputStream()
    private val MONO_FRAME_SIZE = 1920 // 960 mono samples * 2 bytes

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean {
        return true
    }

    override fun getFormatSupport(format: Format): Int {
        return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        return AudioSink.CURRENT_POSITION_NOT_SET
    }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        isInitialized = true
        channelCount = inputFormat.channelCount
    }

    override fun play() {
        playbackStarted = true
    }

    private fun downmixToMono(bytes: ByteArray): ByteArray {
        if (channelCount == 1) return bytes
        val samples = bytes.size / 4 // 2 channels * 2 bytes/sample = 4 bytes per sample pair
        val mono = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val left = ((bytes[i * 4].toInt() and 0xFF) or (bytes[i * 4 + 1].toInt() shl 8)).toShort()
            val right = ((bytes[i * 4 + 2].toInt() and 0xFF) or (bytes[i * 4 + 3].toInt() shl 8)).toShort()
            val monoVal = ((left.toInt() + right.toInt()) / 2).toShort()
            mono[i * 2] = (monoVal.toInt() and 0xFF).toByte()
            mono[i * 2 + 1] = ((monoVal.toInt() ushr 8) and 0xFF).toByte()
        }
        return mono
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        if (!playbackStarted) return false

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val monoBytes = downmixToMono(bytes)
        accumulator.write(monoBytes)

        while (accumulator.size() >= MONO_FRAME_SIZE) {
            val frame = accumulator.toByteArray()
            onPcmFrameReady(frame.copyOfRange(0, MONO_FRAME_SIZE))
            
            val remainder = frame.copyOfRange(MONO_FRAME_SIZE, frame.size)
            accumulator.reset()
            accumulator.write(remainder)
        }

        return true
    }

    override fun handleDiscontinuity() {}

    override fun playToEndOfStream() {}
    override fun isEnded(): Boolean = false
    override fun hasPendingData(): Boolean = false
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {}
    override fun getSkipSilenceEnabled(): Boolean = false
    override fun setAudioAttributes(audioAttributes: AudioAttributes) {}
    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun setAudioSessionId(audioSessionId: Int) {}
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}
    override fun enableTunnelingV21() {}
    override fun disableTunneling() {}
    override fun setVolume(volume: Float) {}
    override fun pause() { playbackStarted = false }
    override fun flush() { accumulator.reset() }
    override fun reset() { isInitialized = false; playbackStarted = false; accumulator.reset() }
    override fun release() {}
}
