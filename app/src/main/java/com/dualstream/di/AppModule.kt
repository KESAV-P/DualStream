package com.dualstream.di

import android.content.Context
import com.dualstream.audio.AudioMixer
import com.dualstream.audio.JitterBuffer
import com.dualstream.audio.OpusDecoder
import com.dualstream.audio.OpusEncoder
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.util.NotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNearbyConnectionManager(
        @ApplicationContext context: Context
    ): NearbyConnectionManager {
        return NearbyConnectionManager(context)
    }

    @Provides
    @Singleton
    fun provideAudioMixer(): AudioMixer {
        return AudioMixer()
    }

    @Provides
    @Singleton
    fun provideJitterBuffer(): JitterBuffer {
        return JitterBuffer()
    }

    @Provides
    @Singleton
    fun provideOpusEncoder(): OpusEncoder {
        return OpusEncoder()
    }

    @Provides
    @Singleton
    fun provideOpusDecoder(): OpusDecoder {
        return OpusDecoder()
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper {
        return NotificationHelper(context)
    }
}
