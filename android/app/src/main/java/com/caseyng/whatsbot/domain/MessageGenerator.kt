package com.caseyng.whatsbot.domain

data class MessageContext(
    val triggerType: String,
    val contactName: String,
    val contactJid: String,
    val incomingMessage: String?,
    val currentActivity: String?,
    val currentTimeMillis: Long
)

interface MessageGenerator {
    suspend fun generate(context: MessageContext): String
}

class StaticMessageGenerator(private val template: String) : MessageGenerator {
    override suspend fun generate(context: MessageContext): String = template
}
