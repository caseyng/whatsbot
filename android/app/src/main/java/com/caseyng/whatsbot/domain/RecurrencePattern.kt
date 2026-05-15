package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
data class RecurrencePattern(
    val cronExpression: String  // e.g. "0 9 * * 1" = every Monday 9am
)
