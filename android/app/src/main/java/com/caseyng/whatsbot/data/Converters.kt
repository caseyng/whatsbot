package com.caseyng.whatsbot.data

import androidx.room.TypeConverter
import com.caseyng.whatsbot.domain.ContactFilter
import com.caseyng.whatsbot.domain.LogEntryType
import com.caseyng.whatsbot.domain.MessageGeneratorType
import com.caseyng.whatsbot.domain.RecurrencePattern
import com.caseyng.whatsbot.domain.RuleAction
import com.caseyng.whatsbot.domain.RuleTrigger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // RuleTrigger

    @TypeConverter
    fun ruleTriggerToString(trigger: RuleTrigger): String =
        json.encodeToString(trigger)

    @TypeConverter
    fun stringToRuleTrigger(value: String): RuleTrigger =
        json.decodeFromString(value)

    // ContactFilter

    @TypeConverter
    fun contactFilterToString(filter: ContactFilter): String =
        json.encodeToString(filter)

    @TypeConverter
    fun stringToContactFilter(value: String): ContactFilter =
        json.decodeFromString(value)

    // RuleAction

    @TypeConverter
    fun ruleActionToString(action: RuleAction): String =
        json.encodeToString(action)

    @TypeConverter
    fun stringToRuleAction(value: String): RuleAction =
        json.decodeFromString(value)

    // RecurrencePattern (nullable)

    @TypeConverter
    fun recurrencePatternToString(pattern: RecurrencePattern?): String? =
        pattern?.let { json.encodeToString(it) }

    @TypeConverter
    fun stringToRecurrencePattern(value: String?): RecurrencePattern? =
        value?.let { json.decodeFromString(it) }

    // LogEntryType

    @TypeConverter
    fun logEntryTypeToString(type: LogEntryType): String = type.name

    @TypeConverter
    fun stringToLogEntryType(value: String): LogEntryType =
        LogEntryType.valueOf(value)

    // MessageGeneratorType

    @TypeConverter
    fun messageGeneratorTypeToString(type: MessageGeneratorType): String = type.name

    @TypeConverter
    fun stringToMessageGeneratorType(value: String): MessageGeneratorType =
        MessageGeneratorType.valueOf(value)
}
