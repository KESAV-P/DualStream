package com.dualstream.audio

object AudioConstants {
    const val SAMPLE_RATE = 48000
    const val CHANNEL_COUNT_MONO = 1
    const val CHANNEL_COUNT_STEREO = 2
    const val BYTES_PER_SAMPLE = 2
    const val FRAME_SIZE_MS = 20
    const val SAMPLES_PER_FRAME = 960
    const val MONO_FRAME_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE           // 1920
    const val STEREO_FRAME_BYTES = SAMPLES_PER_FRAME * 2 * BYTES_PER_SAMPLE     // 3840
}
