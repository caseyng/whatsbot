package com.caseyng.whatsbot.domain

import kotlinx.serialization.Serializable

@Serializable
enum class MessageGeneratorType { STATIC, LLM }
