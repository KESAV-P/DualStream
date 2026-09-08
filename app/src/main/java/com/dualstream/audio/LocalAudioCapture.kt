package com.dualstream.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAudioCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Channel holds raw PCM frames from ExoPlayer
    // Capacity 8 = 8 × 20ms = 160ms max buffer
    // Old frames are dropped if consumer is slow
    var localPcmChannel = Channel<ByteArray>(
        capacity = 20,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var exoPlayer: ExoPlayer? = null

    fun initialize() {
        // Recreate the channel to avoid closed channel exceptions on restart
        localPcmChannel = Channel(
            capacity = 20,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )

        val customSink = CustomPcmAudioSink { pcmBytes ->
            // Non-blocking offer — drop if channel is full
            localPcmChannel.trySend(pcmBytes)
        }

        // Build ExoPlayer with custom audio sink
        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory { handler, _, audioListener, _, _ ->
                arrayOf(
                    MediaCodecAudioRenderer(
                        context,
                        MediaCodecSelector.DEFAULT,
                        handler,
                        audioListener,
                        customSink // ← Plug in our interceptor
                    )
                )
            }
            .build()
    }

    fun playFromUri(uri: android.net.Uri) {
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
    }

    fun playFromUrl(url: String) {
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
