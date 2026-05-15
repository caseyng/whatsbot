package com.caseyng.whatsbot.domain

data class Rule(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val trigger: RuleTrigger,
    val contactFilter: ContactFilter,
    val action: RuleAction,
    val notifyOnAction: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastFiredAt: Long? = null
)
