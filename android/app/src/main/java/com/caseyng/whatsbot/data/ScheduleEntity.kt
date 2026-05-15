package com.caseyng.whatsbot.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.caseyng.whatsbot.domain.RecurrencePattern
import com.caseyng.whatsbot.domain.ScheduledMessage

/**
 * Room entity for persisted scheduled messages. [recurrence] is stored as JSON via
 * [Converters.kt] and is nullable for one-time schedules.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val toJid: String,
    val messageTemplate: String,
    val sendAtMillis: Long,
    // Stored as JSON via Converters.kt; null for one-time schedules
    val recurrence: RecurrencePattern?,
    val isActive: Boolean,
    val createdAt: Long,
    val lastSentAt: Long?
) {
    fun toDomain(): ScheduledMessage = ScheduledMessage(
        id = id,
        name = name,
        toJid = toJid,
        messageTemplate = messageTemplate,
        sendAtMillis = sendAtMillis,
        recurrence = recurrence,
        isActive = isActive,
        createdAt = createdAt,
        lastSentAt = lastSentAt
    )

    companion object {
        fun fromDomain(scheduledMessage: ScheduledMessage): ScheduleEntity = ScheduleEntity(
            id = scheduledMessage.id,
            name = scheduledMessage.name,
            toJid = scheduledMessage.toJid,
            messageTemplate = scheduledMessage.messageTemplate,
            sendAtMillis = scheduledMessage.sendAtMillis,
            recurrence = scheduledMessage.recurrence,
            isActive = scheduledMessage.isActive,
            createdAt = scheduledMessage.createdAt,
            lastSentAt = scheduledMessage.lastSentAt
        )
    }
}
