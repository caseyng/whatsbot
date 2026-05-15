package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class RuleTrigger {
    @Serializable
    data class ActivityTrigger(val activityType: ActivityType) : RuleTrigger()

    @Serializable
    data class TimeWindowTrigger(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int
    ) : RuleTrigger()

    @Serializable
    data class IncomingMessageTrigger(
        val keywordPattern: String?  // null = any message
    ) : RuleTrigger()

    @Serializable
    object ManualTrigger : RuleTrigger()
}
