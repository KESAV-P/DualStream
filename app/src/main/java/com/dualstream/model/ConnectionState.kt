package com.dualstream.model

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Discovering : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    object Disconnected : ConnectionState()
}
