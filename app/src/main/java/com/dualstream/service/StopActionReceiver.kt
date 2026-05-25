package com.dualstream.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StopActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DualStream", "Stop action received from notification")
        if (intent.action == "com.dualstream.ACTION_STOP") {
            try {
                val senderIntent = Intent(context, SenderForegroundService::class.java)
                context.stopService(senderIntent)
            } catch (e: Exception) {
                Log.e("DualStream", "Error stopping SenderForegroundService", e)
            }
            try {
                val receiverIntent = Intent(context, ReceiverForegroundService::class.java)
                context.stopService(receiverIntent)
            } catch (e: Exception) {
                Log.e("DualStream", "Error stopping ReceiverForegroundService", e)
            }
        }
    }
}
