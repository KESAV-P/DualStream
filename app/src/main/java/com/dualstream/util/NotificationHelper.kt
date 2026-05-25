package com.dualstream.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dualstream.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val CHANNEL_STREAMING_ID = "dualstream_streaming"
    val CHANNEL_RECEIVING_ID = "dualstream_receiving"
    
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val streamingChannel = NotificationChannel(
                CHANNEL_STREAMING_ID,
                "Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when DualStream is actively streaming audio"
                setShowBadge(false)
            }

            val receivingChannel = NotificationChannel(
                CHANNEL_RECEIVING_ID,
                "Receiving",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when DualStream is receiving and mixing audio"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(streamingChannel)
            notificationManager.createNotificationChannel(receivingChannel)
        }
    }

    fun buildSenderNotification(deviceName: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent("com.dualstream.ACTION_STOP")
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_STREAMING_ID)
            .setContentTitle("DualStream Sender")
            .setContentText("📡 DualStream — Streaming audio to $deviceName")
            .setSmallIcon(android.R.drawable.presence_audio_busy)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun buildReceiverNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent("com.dualstream.ACTION_STOP")
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_RECEIVING_ID)
            .setContentTitle("DualStream Receiver")
            .setContentText("🎧 DualStream — Mixing audio to earbuds")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
