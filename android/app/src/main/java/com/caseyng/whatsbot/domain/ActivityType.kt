package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityType { IN_VEHICLE, ON_BICYCLE, RUNNING, WALKING, STILL, UNKNOWN }
