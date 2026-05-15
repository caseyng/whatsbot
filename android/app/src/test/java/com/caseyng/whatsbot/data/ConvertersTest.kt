package com.caseyng.whatsbot.data

import com.caseyng.whatsbot.domain.ActivityType
import com.caseyng.whatsbot.domain.ContactFilter
import com.caseyng.whatsbot.domain.LogEntryType
import com.caseyng.whatsbot.domain.MessageGeneratorType
import com.caseyng.whatsbot.domain.RecurrencePattern
import com.caseyng.whatsbot.domain.RuleAction
import com.caseyng.whatsbot.domain.RuleTrigger
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM tests for [Converters].
 *
 * Strategy: for every converter, test BOTH:
 *   (a) Round-trip fidelity  — decode(encode(x)) == x
 *   (b) Format correctness   — the encoded String contains expected field values.
 *
 * Round-trip alone can pass when both encode and decode share the same bug.
 * The format-correctness assertions catch that class of failure.
 */
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    // ──────────────────────────────────────────────────────────────────────
    // RuleTrigger ↔ String
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun ruleTrigger_ActivityTrigger_roundTrip() {
        val trigger = RuleTrigger.ActivityTrigger(activityType = ActivityType.RUNNING)
        val encoded = converters.ruleTriggerToString(trigger)
        val decoded = converters.stringToRuleTrigger(encoded)
        assertEquals(trigger, decoded)
    }

    @Test
    fun ruleTrigger_ActivityTrigger_encodedStringContainsVariantAndFieldValue() {
        val trigger = RuleTrigger.ActivityTrigger(activityType = ActivityType.WALKING)
        val encoded = converters.ruleTriggerToString(trigger)
        assertTrue(
            "Encoded string should reference the ActivityTrigger variant. Got: $encoded",
            encoded.contains("ActivityTrigger") || encoded.contains("activityTrigger") || encoded.contains("ACTIVITY")
        )
        assertTrue(
            "Encoded string should contain the activityType value WALKING. Got: $encoded",
            encoded.contains("WALKING")
        )
    }

    @Test
    fun ruleTrigger_TimeWindowTrigger_roundTrip() {
        val trigger = RuleTrigger.TimeWindowTrigger(
            startHour = 9, startMinute = 30, endHour = 17, endMinute = 0
        )
        val encoded = converters.ruleTriggerToString(trigger)
        val decoded = converters.stringToRuleTrigger(encoded)
        assertEquals(trigger, decoded)
    }

    @Test
    fun ruleTrigger_TimeWindowTrigger_encodedStringContainsFieldValues() {
        val trigger = RuleTrigger.TimeWindowTrigger(
            startHour = 8, startMinute = 15, endHour = 20, endMinute = 45
        )
        val encoded = converters.ruleTriggerToString(trigger)
        assertTrue(
            "Encoded string should contain startHour value 8. Got: $encoded",
            encoded.contains("8")
        )
        assertTrue(
            "Encoded string should contain startMinute value 15. Got: $encoded",
            encoded.contains("15")
        )
        assertTrue(
            "Encoded string should contain endHour value 20. Got: $encoded",
            encoded.contains("20")
        )
        assertTrue(
            "Encoded string should contain endMinute value 45. Got: $encoded",
            encoded.contains("45")
        )
        assertTrue(
            "Encoded string should reference the TimeWindowTrigger variant. Got: $encoded",
            encoded.contains("TimeWindow") || encoded.contains("timeWindow") || encoded.contains("TIME_WINDOW")
        )
    }

    @Test
    fun ruleTrigger_IncomingMessageTrigger_withKeyword_roundTrip() {
        val trigger = RuleTrigger.IncomingMessageTrigger(keywordPattern = "urgent|help")
        val encoded = converters.ruleTriggerToString(trigger)
        val decoded = converters.stringToRuleTrigger(encoded)
        assertEquals(trigger, decoded)
    }

    @Test
    fun ruleTrigger_IncomingMessageTrigger_withKeyword_encodedStringContainsFieldValue() {
        val keyword = "urgent|help"
        val trigger = RuleTrigger.IncomingMessageTrigger(keywordPattern = keyword)
        val encoded = converters.ruleTriggerToString(trigger)
        assertTrue(
            "Encoded string should contain keyword pattern value. Got: $encoded",
            encoded.contains("urgent") && encoded.contains("help")
        )
        assertTrue(
            "Encoded string should reference the IncomingMessageTrigger variant. Got: $encoded",
            encoded.contains("IncomingMessage") || encoded.contains("incomingMessage") || encoded.contains("INCOMING_MESSAGE")
        )
    }

    @Test
    fun ruleTrigger_IncomingMessageTrigger_nullKeyword_roundTrip() {
        val trigger = RuleTrigger.IncomingMessageTrigger(keywordPattern = null)
        val encoded = converters.ruleTriggerToString(trigger)
        val decoded = converters.stringToRuleTrigger(encoded)
        assertEquals(trigger, decoded)
    }

    @Test
    fun ruleTrigger_ManualTrigger_roundTrip() {
        val trigger = RuleTrigger.ManualTrigger
        val encoded = converters.ruleTriggerToString(trigger)
        val decoded = converters.stringToRuleTrigger(encoded)
        assertEquals(trigger, decoded)
    }

    @Test
    fun ruleTrigger_ManualTrigger_encodedStringContainsVariantName() {
        val trigger = RuleTrigger.ManualTrigger
        val encoded = converters.ruleTriggerToString(trigger)
        assertTrue(
            "Encoded string should reference the ManualTrigger variant. Got: $encoded",
            encoded.contains("Manual") || encoded.contains("manual") || encoded.contains("MANUAL")
        )
    }

    @Test
    fun ruleTrigger_allActivityTypes_roundTrip() {
        for (activityType in ActivityType.values()) {
            val trigger = RuleTrigger.ActivityTrigger(activityType)
            val encoded = converters.ruleTriggerToString(trigger)
            val decoded = converters.stringToRuleTrigger(encoded)
            assertEquals("Round-trip failed for ActivityType.$activityType", trigger, decoded)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // ContactFilter ↔ String
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun contactFilter_All_roundTrip() {
        val filter = ContactFilter.All
        val encoded = converters.contactFilterToString(filter)
        val decoded = converters.stringToContactFilter(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun contactFilter_All_encodedStringContainsVariantName() {
        val encoded = converters.contactFilterToString(ContactFilter.All)
        assertTrue(
            "Encoded string should reference the All variant. Got: $encoded",
            encoded.contains("All") || encoded.contains("ALL")
        )
    }

    @Test
    fun contactFilter_Specific_roundTrip() {
        val filter = ContactFilter.Specific(jids = listOf("1234567890@s.whatsapp.net", "9876543210@s.whatsapp.net"))
        val encoded = converters.contactFilterToString(filter)
        val decoded = converters.stringToContactFilter(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun contactFilter_Specific_encodedStringContainsJids() {
        val jid1 = "1234567890@s.whatsapp.net"
        val jid2 = "9876543210@s.whatsapp.net"
        val filter = ContactFilter.Specific(jids = listOf(jid1, jid2))
        val encoded = converters.contactFilterToString(filter)
        assertTrue("Encoded string should contain jid1. Got: $encoded", encoded.contains("1234567890"))
        assertTrue("Encoded string should contain jid2. Got: $encoded", encoded.contains("9876543210"))
        assertTrue(
            "Encoded string should reference the Specific variant. Got: $encoded",
            encoded.contains("Specific") || encoded.contains("specific") || encoded.contains("SPECIFIC")
        )
    }

    @Test
    fun contactFilter_Specific_emptyList_roundTrip() {
        val filter = ContactFilter.Specific(jids = emptyList())
        val encoded = converters.contactFilterToString(filter)
        val decoded = converters.stringToContactFilter(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun contactFilter_GroupsOnly_roundTrip() {
        val filter = ContactFilter.GroupsOnly
        val encoded = converters.contactFilterToString(filter)
        val decoded = converters.stringToContactFilter(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun contactFilter_GroupsOnly_encodedStringContainsVariantName() {
        val encoded = converters.contactFilterToString(ContactFilter.GroupsOnly)
        assertTrue(
            "Encoded string should reference the GroupsOnly variant. Got: $encoded",
            encoded.contains("GroupsOnly") || encoded.contains("groupsOnly") || encoded.contains("GROUPS_ONLY")
        )
    }

    @Test
    fun contactFilter_IndividualsOnly_roundTrip() {
        val filter = ContactFilter.IndividualsOnly
        val encoded = converters.contactFilterToString(filter)
        val decoded = converters.stringToContactFilter(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun contactFilter_IndividualsOnly_encodedStringContainsVariantName() {
        val encoded = converters.contactFilterToString(ContactFilter.IndividualsOnly)
        assertTrue(
            "Encoded string should reference the IndividualsOnly variant. Got: $encoded",
            encoded.contains("IndividualsOnly") || encoded.contains("individualsOnly") || encoded.contains("INDIVIDUALS_ONLY")
        )
    }

    @Test
    fun contactFilter_AllExcept_roundTrip() {
        val filter = ContactFilter.AllExcept(jids = listOf("excluded@s.whatsapp.net"))
        val encoded = converters.contactFilterToString(filter)
        val decoded = converters.stringToContactFilter(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun contactFilter_AllExcept_encodedStringContainsVariantNameAndJid() {
        val excludedJid = "excluded@s.whatsapp.net"
        val filter = ContactFilter.AllExcept(jids = listOf(excludedJid))
        val encoded = converters.contactFilterToString(filter)
        assertTrue("Encoded string should contain excluded jid. Got: $encoded", encoded.contains("excluded"))
        assertTrue(
            "Encoded string should reference the AllExcept variant. Got: $encoded",
            encoded.contains("AllExcept") || encoded.contains("allExcept") || encoded.contains("ALL_EXCEPT")
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // RuleAction ↔ String
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun ruleAction_AutoReply_static_roundTrip() {
        val action = RuleAction.AutoReply(
            messageTemplate = "I am currently unavailable.",
            generatorType = MessageGeneratorType.STATIC
        )
        val encoded = converters.ruleActionToString(action)
        val decoded = converters.stringToRuleAction(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun ruleAction_AutoReply_encodedStringContainsTemplateAndVariant() {
        val template = "I am currently unavailable."
        val action = RuleAction.AutoReply(
            messageTemplate = template,
            generatorType = MessageGeneratorType.STATIC
        )
        val encoded = converters.ruleActionToString(action)
        assertTrue("Encoded string should contain message template. Got: $encoded", encoded.contains("unavailable"))
        assertTrue(
            "Encoded string should reference AutoReply variant. Got: $encoded",
            encoded.contains("AutoReply") || encoded.contains("autoReply") || encoded.contains("AUTO_REPLY")
        )
        assertTrue(
            "Encoded string should contain generator type STATIC. Got: $encoded",
            encoded.contains("STATIC")
        )
    }

    @Test
    fun ruleAction_AutoReply_llm_roundTrip() {
        val action = RuleAction.AutoReply(
            messageTemplate = "{{context}}",
            generatorType = MessageGeneratorType.LLM
        )
        val encoded = converters.ruleActionToString(action)
        val decoded = converters.stringToRuleAction(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun ruleAction_AutoReply_llm_encodedStringContainsLlmGeneratorType() {
        val action = RuleAction.AutoReply(
            messageTemplate = "{{context}}",
            generatorType = MessageGeneratorType.LLM
        )
        val encoded = converters.ruleActionToString(action)
        assertTrue(
            "Encoded string should contain generator type LLM. Got: $encoded",
            encoded.contains("LLM")
        )
    }

    @Test
    fun ruleAction_SendMessage_roundTrip() {
        val action = RuleAction.SendMessage(
            toJid = "1234567890@s.whatsapp.net",
            messageTemplate = "Hello from bot",
            generatorType = MessageGeneratorType.STATIC
        )
        val encoded = converters.ruleActionToString(action)
        val decoded = converters.stringToRuleAction(encoded)
        assertEquals(action, decoded)
    }

    @Test
    fun ruleAction_SendMessage_encodedStringContainsJidAndTemplate() {
        val toJid = "1234567890@s.whatsapp.net"
        val template = "Hello from bot"
        val action = RuleAction.SendMessage(
            toJid = toJid,
            messageTemplate = template,
            generatorType = MessageGeneratorType.STATIC
        )
        val encoded = converters.ruleActionToString(action)
        assertTrue("Encoded string should contain toJid. Got: $encoded", encoded.contains("1234567890"))
        assertTrue("Encoded string should contain message template text. Got: $encoded", encoded.contains("Hello from bot"))
        assertTrue(
            "Encoded string should reference SendMessage variant. Got: $encoded",
            encoded.contains("SendMessage") || encoded.contains("sendMessage") || encoded.contains("SEND_MESSAGE")
        )
    }

    @Test
    fun ruleAction_SendMessage_llm_roundTrip() {
        val action = RuleAction.SendMessage(
            toJid = "9876@s.whatsapp.net",
            messageTemplate = "Daily report: {{report}}",
            generatorType = MessageGeneratorType.LLM
        )
        val encoded = converters.ruleActionToString(action)
        val decoded = converters.stringToRuleAction(encoded)
        assertEquals(action, decoded)
    }

    // ──────────────────────────────────────────────────────────────────────
    // RecurrencePattern? ↔ String?
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun recurrencePattern_nonNull_roundTrip() {
        val pattern = RecurrencePattern(cronExpression = "0 9 * * MON-FRI")
        val encoded = converters.recurrencePatternToString(pattern)
        assertNotNull("Encoded non-null RecurrencePattern should not be null", encoded)
        val decoded = converters.stringToRecurrencePattern(encoded)
        assertEquals(pattern, decoded)
    }

    @Test
    fun recurrencePattern_nonNull_encodedStringContainsCronExpression() {
        val cron = "0 9 * * MON-FRI"
        val pattern = RecurrencePattern(cronExpression = cron)
        val encoded = converters.recurrencePatternToString(pattern)
        assertNotNull(encoded)
        assertTrue(
            "Encoded string should contain the cron expression. Got: $encoded",
            encoded!!.contains("0 9") || encoded.contains("0 9") || encoded.contains("MON-FRI")
        )
    }

    @Test
    fun recurrencePattern_null_encodesToNull() {
        val encoded = converters.recurrencePatternToString(null)
        assertNull("Encoding null RecurrencePattern should produce null string", encoded)
    }

    @Test
    fun recurrencePattern_null_decodesFromNull() {
        val decoded = converters.stringToRecurrencePattern(null)
        assertNull("Decoding null string should produce null RecurrencePattern", decoded)
    }

    @Test
    fun recurrencePattern_differentCronExpressions_allRoundTrip() {
        val expressions = listOf(
            "0 9 * * MON-FRI",
            "0 0 * * *",
            "*/15 * * * *",
            "0 8 1 * *"
        )
        for (cron in expressions) {
            val pattern = RecurrencePattern(cronExpression = cron)
            val encoded = converters.recurrencePatternToString(pattern)
            val decoded = converters.stringToRecurrencePattern(encoded)
            assertEquals("Round-trip failed for cron: $cron", pattern, decoded)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // LogEntryType → String / String → LogEntryType
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun logEntryType_encodesToName() {
        for (type in LogEntryType.values()) {
            val encoded = converters.logEntryTypeToString(type)
            assertEquals(
                "LogEntryType.$type should encode to its .name string",
                type.name,
                encoded
            )
        }
    }

    @Test
    fun logEntryType_decodesFromName() {
        for (type in LogEntryType.values()) {
            val decoded = converters.stringToLogEntryType(type.name)
            assertEquals(
                "String '${type.name}' should decode back to LogEntryType.$type",
                type,
                decoded
            )
        }
    }

    @Test
    fun logEntryType_roundTrip_allValues() {
        for (type in LogEntryType.values()) {
            val encoded = converters.logEntryTypeToString(type)
            val decoded = converters.stringToLogEntryType(encoded)
            assertEquals("Round-trip failed for LogEntryType.$type", type, decoded)
        }
    }

    @Test
    fun logEntryType_RULE_FIRED_encodedStringIsExactName() {
        val encoded = converters.logEntryTypeToString(LogEntryType.RULE_FIRED)
        assertEquals("RULE_FIRED", encoded)
    }

    @Test
    fun logEntryType_ERROR_encodedStringIsExactName() {
        val encoded = converters.logEntryTypeToString(LogEntryType.ERROR)
        assertEquals("ERROR", encoded)
    }

    @Test
    fun logEntryType_SESSION_EXPIRED_encodedStringIsExactName() {
        val encoded = converters.logEntryTypeToString(LogEntryType.SESSION_EXPIRED)
        assertEquals("SESSION_EXPIRED", encoded)
    }

    // ──────────────────────────────────────────────────────────────────────
    // MessageGeneratorType → String / String → MessageGeneratorType
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun messageGeneratorType_encodesToName() {
        for (type in MessageGeneratorType.values()) {
            val encoded = converters.messageGeneratorTypeToString(type)
            assertEquals(
                "MessageGeneratorType.$type should encode to its .name string",
                type.name,
                encoded
            )
        }
    }

    @Test
    fun messageGeneratorType_decodesFromName() {
        for (type in MessageGeneratorType.values()) {
            val decoded = converters.stringToMessageGeneratorType(type.name)
            assertEquals(
                "String '${type.name}' should decode back to MessageGeneratorType.$type",
                type,
                decoded
            )
        }
    }

    @Test
    fun messageGeneratorType_STATIC_encodedStringIsExactName() {
        val encoded = converters.messageGeneratorTypeToString(MessageGeneratorType.STATIC)
        assertEquals("STATIC", encoded)
    }

    @Test
    fun messageGeneratorType_LLM_encodedStringIsExactName() {
        val encoded = converters.messageGeneratorTypeToString(MessageGeneratorType.LLM)
        assertEquals("LLM", encoded)
    }

    @Test
    fun messageGeneratorType_roundTrip_allValues() {
        for (type in MessageGeneratorType.values()) {
            val encoded = converters.messageGeneratorTypeToString(type)
            val decoded = converters.stringToMessageGeneratorType(encoded)
            assertEquals("Round-trip failed for MessageGeneratorType.$type", type, decoded)
        }
    }
}
