package com.caseyng.whatsbot.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.caseyng.whatsbot.domain.LogEntry
import com.caseyng.whatsbot.domain.LogEntryType

/**
 * Room entity for persisted log entries. [type] is stored as its name string via
 * [Converters.kt].
 */
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Stored as name string via Converters.kt
    val type: LogEntryType,
    val timestamp: Long,
    val ruleName: String?,
    val scheduleName: String?,
    val contactJid: String?,
    val contactName: String?,
    val messagePreview: String?,
    val outcome: String?,
    val detail: String?
) {
    fun toDomain(): LogEntry = LogEntry(
        id = id,
        type = type,
        timestamp = timestamp,
        ruleName = ruleName,
        scheduleName = scheduleName,
        contactJid = contactJid,
        contactName = contactName,
        messagePreview = messagePreview,
        outcome = outcome,
        detail = detail
    )

    companion object {
        fun fromDomain(logEntry: LogEntry): LogEntryEntity = LogEntryEntity(
            id = logEntry.id,
            type = logEntry.type,
            timestamp = logEntry.timestamp,
            ruleName = logEntry.ruleName,
            scheduleName = logEntry.scheduleName,
            contactJid = logEntry.contactJid,
            contactName = logEntry.contactName,
            messagePreview = logEntry.messagePreview,
            outcome = logEntry.outcome,
            detail = logEntry.detail
        )
    }
}
