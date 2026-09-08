package com.dualstream.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualstream.model.ConnectionState
import com.dualstream.network.NearbyConnectionManager
import com.dualstream.service.SenderForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SenderViewModel @Inject constructor(
    private val nearbyConnectionManager: NearbyConnectionManager,
    application: Application
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val connectionState: StateFlow<ConnectionState> = nearbyConnectionManager.connectionState

    val isStreaming: StateFlow<Boolean> = SenderForegroundService.isStreaming

    val audioLevel: StateFlow<Float> = SenderForegroundService.audioLevel

    val isSilenceDetected: StateFlow<Boolean> = SenderForegroundService.isSilenceDetected
    
    val isRemoteAudioFlowing: StateFlow<Boolean> = SenderForegroundService.isRemoteAudioFlowing

    fun isAlternateOutputAvailable(): Boolean {
        return com.dualstream.audio.AudioCaptureManager.isAlternateOutputAvailable(context)
    }

    fun startSender() {
        // Discovery is started by the service on start.
        // Screen capture flow is launched from the UI, which then feeds the result to onMediaProjectionResult
    }

    fun stopSender() {
        LogStopCommand()
        val intent = Intent(context, SenderForegroundService::class.java)
        context.stopService(intent)
        nearbyConnectionManager.disconnect()
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent, useFallbackGainMakeup: Boolean = false) {
        val intent = Intent(context, SenderForegroundService::class.java).apply {
            putExtra("PROJECTION_RESULT_CODE", resultCode)
            putExtra("PROJECTION_INTENT", data)
            putExtra("USE_FALLBACK_GAIN_MAKEUP", useFallbackGainMakeup)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun LogStopCommand() {
        android.util.Log.d("DualStream", "SenderViewModel stopSender() triggered")
    }
}
