package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class RuleAction {
    @Serializable
    data class AutoReply(
        val messageTemplate: String,
        val generatorType: MessageGeneratorType = MessageGeneratorType.STATIC
    ) : RuleAction()

    @Serializable
    data class SendMessage(
        val toJid: String,
        val messageTemplate: String,
        val generatorType: MessageGeneratorType = MessageGeneratorType.STATIC
    ) : RuleAction()
}
