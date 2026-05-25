package com.dualstream.audio

const val SAMPLE_RATE = 44100          // Hz
const val CHANNEL_COUNT = 2            // Stereo
const val BIT_DEPTH = 16               // PCM_16BIT
const val BYTES_PER_SAMPLE = 2
const val FRAME_SIZE_MS = 20           // 20ms per audio frame (standard for OPUS)
const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000  // = 882
const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * CHANNEL_COUNT * BYTES_PER_SAMPLE // = 3528
