package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
enum class LogEntryType {
    RULE_FIRED,
    RULE_SKIPPED,
    SCHEDULE_SENT,
    SCHEDULE_FAILED,
    CONNECTION_CONNECTED,
    CONNECTION_DISCONNECTED,
    CONNECTION_RECONNECTING,
    SESSION_EXPIRED,
    ERROR
}
