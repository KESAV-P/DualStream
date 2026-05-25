package com.dualstream.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.dualstream.model.AudioStats
import com.dualstream.model.ConnectionState
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.service.ReceiverForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    private val nearbyConnectionManager: NearbyConnectionManager,
    application: Application
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val connectionState: StateFlow<ConnectionState> = nearbyConnectionManager.connectionState

    val audioStats: StateFlow<AudioStats> = ReceiverForegroundService.audioStats

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun startReceiver() {
        Log.d("DualStream", "ReceiverViewModel startReceiver() triggered")
        val intent = Intent(context, ReceiverForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopReceiver() {
        Log.d("DualStream", "ReceiverViewModel stopReceiver() triggered")
        sendStopStreamCommand()
        val intent = Intent(context, ReceiverForegroundService::class.java)
        context.stopService(intent)
        nearbyConnectionManager.disconnect()
        _isPlaying.value = false
    }

    fun sendStartStreamCommand() {
        Log.d("DualStream", "Sending START_STREAM control command to Phone A")
        val json = JSONObject().apply {
            put("type", "START_STREAM")
        }
        nearbyConnectionManager.sendControlMessage(json)
    }

    fun sendStopStreamCommand() {
        Log.d("DualStream", "Sending STOP_STREAM control command to Phone A")
        val json = JSONObject().apply {
            put("type", "STOP_STREAM")
        }
        nearbyConnectionManager.sendControlMessage(json)
    }

    fun playLocalFile(uri: Uri) {
        Log.d("DualStream", "Playing local file: $uri")
        val intent = Intent(context, ReceiverForegroundService::class.java).apply {
            action = ReceiverForegroundService.ACTION_PLAY
            putExtra(ReceiverForegroundService.EXTRA_AUDIO_URI, uri.toString())
        }
        context.startService(intent)
        _isPlaying.value = true
    }

    fun stopLocalPlayback() {
        Log.d("DualStream", "Stopping local playback")
        val intent = Intent(context, ReceiverForegroundService::class.java).apply {
            action = ReceiverForegroundService.ACTION_STOP_MEDIA
        }
        context.startService(intent)
        _isPlaying.value = false
    }
}
