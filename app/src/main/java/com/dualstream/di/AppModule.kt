package com.dualstream.di

import android.content.Context
import com.dualstream.audio.DualAudioPlayer
import com.dualstream.audio.JitterBuffer
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
    fun provideConnectionsClient(
        @ApplicationContext context: Context
    ): com.google.android.gms.nearby.connection.ConnectionsClient {
        return com.google.android.gms.nearby.Nearby.getConnectionsClient(context)
    }

    @Provides
    @Singleton
    fun provideNearbyConnectionManager(
        @ApplicationContext context: Context,
        streamReceiver: com.dualstream.network.StreamReceiver
    ): NearbyConnectionManager {
        return NearbyConnectionManager(context, streamReceiver)
    }

    @Provides
    @Singleton
    fun provideDualAudioPlayer(): DualAudioPlayer {
        return DualAudioPlayer()
    }

    @Provides
    @Singleton
    fun provideJitterBuffer(): JitterBuffer {
        return JitterBuffer()
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper {
        return NotificationHelper(context)
    }
}
