package com.caseyng.whatsbot.domain

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val phoneNumber: String) : ConnectionState()
    data class Reconnecting(val attempt: Int, val nextRetryInSeconds: Int) : ConnectionState()
    object PairingRequired : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
