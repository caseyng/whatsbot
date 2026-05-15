package com.caseyng.whatsbot.domain

data class ScheduledMessage(
    val id: Long = 0,
    val name: String,
    val toJid: String,
    val messageTemplate: String,
    val sendAtMillis: Long,
    val recurrence: RecurrencePattern?,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSentAt: Long? = null
)
