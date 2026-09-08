package com.dualstream.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import com.dualstream.model.AudioStats
import com.dualstream.model.ConnectionState
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.service.ReceiverForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    private val nearbyConnectionManager: NearbyConnectionManager,
    application: Application
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val connectionState: StateFlow<ConnectionState> = nearbyConnectionManager.connectionState

    private val _leftChannelLevel = MutableStateFlow(0f)
    private val _rightChannelLevel = MutableStateFlow(0f)

    val leftChannelLevel: StateFlow<Float> = _leftChannelLevel.asStateFlow()
    val rightChannelLevel: StateFlow<Float> = _rightChannelLevel.asStateFlow()

    val audioStats: StateFlow<AudioStats> = combine(
        ReceiverForegroundService.audioStats,
        _leftChannelLevel,
        _rightChannelLevel
    ) { stats, left, right ->
        stats.copy(leftChannelLevel = left, rightChannelLevel = right)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioStats())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isRemoteCallActive = MutableStateFlow(false)
    val isRemoteCallActive: StateFlow<Boolean> = _isRemoteCallActive.asStateFlow()

    private val _isRemoteSilenceDetected = MutableStateFlow(false)
    val isRemoteSilenceDetected: StateFlow<Boolean> = _isRemoteSilenceDetected.asStateFlow()

    val isRemoteAudioFlowing: StateFlow<Boolean> = ReceiverForegroundService.isRemoteAudioFlowing

    fun isAlternateOutputAvailable(): Boolean {
        return com.dualstream.audio.AudioCaptureManager.isAlternateOutputAvailable(context)
    }

    private val levelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val channel = intent.getStringExtra("channel")
            val level = intent.getFloatExtra("level", 0f)
            when (channel) {
                "left"  -> _leftChannelLevel.value = level
                "right" -> _rightChannelLevel.value = level
            }
        }
    }

    init {
        val filter = IntentFilter("com.dualstream.AUDIO_LEVEL")
        LocalBroadcastManager.getInstance(context).registerReceiver(levelReceiver, filter)

        viewModelScope.launch {
            nearbyConnectionManager.controlMessages.collect { json ->
                when (json.optString("type")) {
                    "CALL_ACTIVE" -> _isRemoteCallActive.value = true
                    "CALL_ENDED" -> _isRemoteCallActive.value = false
                    "SILENCE_DETECTED" -> _isRemoteSilenceDetected.value = json.optBoolean("status", false)
                }
            }
        }
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent, useFallbackGainMakeup: Boolean = false) {
        Log.d("DualStream", "ReceiverViewModel onMediaProjectionResult() triggered")
        val intent = Intent(context, ReceiverForegroundService::class.java).apply {
            putExtra("PROJECTION_INTENT", data)
            putExtra("PROJECTION_RESULT_CODE", resultCode)
            putExtra("USE_FALLBACK_GAIN_MAKEUP", useFallbackGainMakeup)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _isPlaying.value = true
    }

    fun stopReceiver() {
        Log.d("DualStream", "ReceiverViewModel stopReceiver() triggered")
        sendStopStreamCommand()
        val intent = Intent(context, ReceiverForegroundService::class.java)
        context.stopService(intent)
        nearbyConnectionManager.disconnect()
        _isPlaying.value = false
    }

    fun sendStopStreamCommand() {
        Log.d("DualStream", "Sending STOP_STREAM control command to Phone A")
        val json = JSONObject().apply {
            put("type", "STOP_STREAM")
        }
        nearbyConnectionManager.sendControlMessage(json)
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(levelReceiver)
    }
}

