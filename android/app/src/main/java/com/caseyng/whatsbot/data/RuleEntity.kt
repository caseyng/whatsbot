package com.caseyng.whatsbot.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.caseyng.whatsbot.domain.ContactFilter
import com.caseyng.whatsbot.domain.Rule
import com.caseyng.whatsbot.domain.RuleAction
import com.caseyng.whatsbot.domain.RuleTrigger

/**
 * Room entity for persisted rules. Complex domain types (trigger, contactFilter, action) are
 * stored as JSON strings via [Converters]. Entities are pure data containers — no business logic.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean,
    // Stored as JSON via Converters.kt TypeConverters
    val trigger: RuleTrigger,
    val contactFilter: ContactFilter,
    val action: RuleAction,
    val notifyOnAction: Boolean,
    val createdAt: Long,
    val lastFiredAt: Long?
) {
    fun toDomain(): Rule = Rule(
        id = id,
        name = name,
        isEnabled = isEnabled,
        trigger = trigger,
        contactFilter = contactFilter,
        action = action,
        notifyOnAction = notifyOnAction,
        createdAt = createdAt,
        lastFiredAt = lastFiredAt
    )

    companion object {
        fun fromDomain(rule: Rule): RuleEntity = RuleEntity(
            id = rule.id,
            name = rule.name,
            isEnabled = rule.isEnabled,
            trigger = rule.trigger,
            contactFilter = rule.contactFilter,
            action = rule.action,
            notifyOnAction = rule.notifyOnAction,
            createdAt = rule.createdAt,
            lastFiredAt = rule.lastFiredAt
        )
    }
}
