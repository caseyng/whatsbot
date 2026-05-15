package com.caseyng.whatsbot.domain

data class LogEntry(
    val id: Long = 0,
    val type: LogEntryType,
    val timestamp: Long = System.currentTimeMillis(),
    val ruleName: String? = null,
    val scheduleName: String? = null,
    val contactJid: String? = null,
    val contactName: String? = null,
    val messagePreview: String? = null,  // first 100 chars of message sent
    val outcome: String? = null,         // "sent", "failed: <reason>", "skipped: <reason>"
    val detail: String? = null           // additional context, e.g. rule eval reason
)
