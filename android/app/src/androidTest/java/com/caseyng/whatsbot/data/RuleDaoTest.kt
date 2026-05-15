package com.caseyng.whatsbot.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caseyng.whatsbot.domain.ActivityType
import com.caseyng.whatsbot.domain.ContactFilter
import com.caseyng.whatsbot.domain.MessageGeneratorType
import com.caseyng.whatsbot.domain.RuleAction
import com.caseyng.whatsbot.domain.RuleTrigger
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var ruleDao: RuleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        ruleDao = db.ruleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun buildRuleEntity(
        name: String = "Test Rule",
        isEnabled: Boolean = true,
        trigger: RuleTrigger = RuleTrigger.ManualTrigger,
        contactFilter: ContactFilter = ContactFilter.All,
        action: RuleAction = RuleAction.AutoReply(messageTemplate = "OK"),
        notifyOnAction: Boolean = true,
        createdAt: Long = System.currentTimeMillis(),
        lastFiredAt: Long? = null
    ) = RuleEntity(
        id = 0,
        name = name,
        isEnabled = isEnabled,
        trigger = trigger,
        contactFilter = contactFilter,
        action = action,
        notifyOnAction = notifyOnAction,
        createdAt = createdAt,
        lastFiredAt = lastFiredAt
    )

    // ──────────────────────────────────────────────────────────────────────
    // insert / getById
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun insert_andGetById_returnsInsertedRule() = runTest {
        val entity = buildRuleEntity(name = "My Rule")
        val id = ruleDao.insert(entity)
        val retrieved = ruleDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("My Rule", retrieved!!.name)
        assertEquals(id, retrieved.id)
    }

    @Test
    fun getById_nonExistentId_returnsNull() = runTest {
        val retrieved = ruleDao.getById(99999L)
        assertNull(retrieved)
    }

    // ──────────────────────────────────────────────────────────────────────
    // getActiveRules
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun getActiveRules_returnsOnlyEnabledRules() = runTest {
        ruleDao.insert(buildRuleEntity(name = "Enabled Rule", isEnabled = true))
        ruleDao.insert(buildRuleEntity(name = "Disabled Rule", isEnabled = false))

        val activeRules = ruleDao.getActiveRules()

        assertEquals(1, activeRules.size)
        assertEquals("Enabled Rule", activeRules[0].name)
    }

    @Test
    fun getActiveRules_withNoEnabledRules_returnsEmpty() = runTest {
        ruleDao.insert(buildRuleEntity(name = "Rule A", isEnabled = false))
        ruleDao.insert(buildRuleEntity(name = "Rule B", isEnabled = false))

        val activeRules = ruleDao.getActiveRules()

        assertTrue("Should return empty list when all rules are disabled", activeRules.isEmpty())
    }

    @Test
    fun getActiveRules_withAllEnabled_returnsAll() = runTest {
        ruleDao.insert(buildRuleEntity(name = "Rule 1", isEnabled = true))
        ruleDao.insert(buildRuleEntity(name = "Rule 2", isEnabled = true))
        ruleDao.insert(buildRuleEntity(name = "Rule 3", isEnabled = true))

        val activeRules = ruleDao.getActiveRules()

        assertEquals(3, activeRules.size)
    }

    // ──────────────────────────────────────────────────────────────────────
    // observeAll
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun observeAll_emitsAllRulesRegardlessOfEnabledStatus() = runTest {
        ruleDao.insert(buildRuleEntity(name = "Enabled", isEnabled = true))
        ruleDao.insert(buildRuleEntity(name = "Disabled", isEnabled = false))

        val rules = ruleDao.observeAll().first()

        assertEquals(2, rules.size)
    }

    @Test
    fun observeAll_orderedNewestFirst() = runTest {
        val now = System.currentTimeMillis()
        ruleDao.insert(buildRuleEntity(name = "Older", createdAt = now - 10_000))
        ruleDao.insert(buildRuleEntity(name = "Newer", createdAt = now))

        val rules = ruleDao.observeAll().first()

        assertEquals(2, rules.size)
        assertEquals("Newer", rules[0].name)
        assertEquals("Older", rules[1].name)
    }

    @Test
    fun observeAll_emitsEmptyListWhenNoRules() = runTest {
        val rules = ruleDao.observeAll().first()
        assertTrue("observeAll should emit empty list on empty table", rules.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────
    // setEnabled
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun setEnabled_false_removesRuleFromActiveRules() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "Active Rule", isEnabled = true))
        // Verify it's active before
        assertEquals(1, ruleDao.getActiveRules().size)

        ruleDao.setEnabled(id, false)

        val activeRules = ruleDao.getActiveRules()
        assertTrue(
            "After setEnabled(false), getActiveRules() must not return the rule",
            activeRules.none { it.id == id }
        )
    }

    @Test
    fun setEnabled_true_makesRuleAppearInActiveRules() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "Initially Disabled", isEnabled = false))
        assertTrue("Rule should not be active initially", ruleDao.getActiveRules().isEmpty())

        ruleDao.setEnabled(id, true)

        val activeRules = ruleDao.getActiveRules()
        assertEquals(1, activeRules.size)
        assertEquals(id, activeRules[0].id)
    }

    @Test
    fun setEnabled_false_doesNotDeleteRule_stillVisibleInObserveAll() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "Will Be Disabled", isEnabled = true))

        ruleDao.setEnabled(id, false)

        val allRules = ruleDao.observeAll().first()
        assertEquals(
            "Disabled rule should still appear in observeAll()",
            1,
            allRules.size
        )
        assertEquals(id, allRules[0].id)
    }

    // ──────────────────────────────────────────────────────────────────────
    // updateLastFired
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun updateLastFired_updatesLastFiredAtTimestamp() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "Rule", lastFiredAt = null))
        val firedAt = 1_700_000_000_000L

        ruleDao.updateLastFired(id, firedAt)

        val retrieved = ruleDao.getById(id)
        assertNotNull(retrieved)
        assertEquals(
            "getById should return the updated lastFiredAt timestamp",
            firedAt,
            retrieved!!.lastFiredAt
        )
    }

    @Test
    fun updateLastFired_overwritesPreviousTimestamp() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "Rule", lastFiredAt = 1_000_000L))
        val newTimestamp = 2_000_000L

        ruleDao.updateLastFired(id, newTimestamp)

        val retrieved = ruleDao.getById(id)
        assertEquals(newTimestamp, retrieved!!.lastFiredAt)
    }

    // ──────────────────────────────────────────────────────────────────────
    // update
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun update_modifiesExistingRule() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "Original Name"))
        val existing = ruleDao.getById(id)!!
        val updated = existing.copy(name = "Updated Name")

        ruleDao.update(updated)

        val retrieved = ruleDao.getById(id)
        assertEquals("Updated Name", retrieved!!.name)
    }

    // ──────────────────────────────────────────────────────────────────────
    // delete
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun delete_removesRuleFromTable() = runTest {
        val id = ruleDao.insert(buildRuleEntity(name = "To Delete"))
        val entity = ruleDao.getById(id)!!

        ruleDao.delete(entity)

        val retrieved = ruleDao.getById(id)
        assertNull("After delete(), getById() must return null", retrieved)
    }

    @Test
    fun delete_removesOnlyTargetedRule_othersUnaffected() = runTest {
        val id1 = ruleDao.insert(buildRuleEntity(name = "Rule 1"))
        val id2 = ruleDao.insert(buildRuleEntity(name = "Rule 2"))
        val entity1 = ruleDao.getById(id1)!!

        ruleDao.delete(entity1)

        assertNull(ruleDao.getById(id1))
        assertNotNull(ruleDao.getById(id2))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Domain type persistence — complex trigger/filter/action types
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun insert_withActivityTrigger_persistsCorrectly() = runTest {
        val trigger = RuleTrigger.ActivityTrigger(activityType = ActivityType.RUNNING)
        val id = ruleDao.insert(buildRuleEntity(trigger = trigger))
        val retrieved = ruleDao.getById(id)
        assertEquals(trigger, retrieved!!.trigger)
    }

    @Test
    fun insert_withTimeWindowTrigger_persistsCorrectly() = runTest {
        val trigger = RuleTrigger.TimeWindowTrigger(
            startHour = 9, startMinute = 0, endHour = 17, endMinute = 30
        )
        val id = ruleDao.insert(buildRuleEntity(trigger = trigger))
        val retrieved = ruleDao.getById(id)
        assertEquals(trigger, retrieved!!.trigger)
    }

    @Test
    fun insert_withIncomingMessageTrigger_withKeyword_persistsCorrectly() = runTest {
        val trigger = RuleTrigger.IncomingMessageTrigger(keywordPattern = "help|urgent")
        val id = ruleDao.insert(buildRuleEntity(trigger = trigger))
        val retrieved = ruleDao.getById(id)
        assertEquals(trigger, retrieved!!.trigger)
    }

    @Test
    fun insert_withIncomingMessageTrigger_nullKeyword_persistsCorrectly() = runTest {
        val trigger = RuleTrigger.IncomingMessageTrigger(keywordPattern = null)
        val id = ruleDao.insert(buildRuleEntity(trigger = trigger))
        val retrieved = ruleDao.getById(id)
        assertEquals(trigger, retrieved!!.trigger)
    }

    @Test
    fun insert_withSpecificContactFilter_persistsJids() = runTest {
        val filter = ContactFilter.Specific(jids = listOf("a@s.whatsapp.net", "b@s.whatsapp.net"))
        val id = ruleDao.insert(buildRuleEntity(contactFilter = filter))
        val retrieved = ruleDao.getById(id)
        assertEquals(filter, retrieved!!.contactFilter)
    }

    @Test
    fun insert_withAllExceptContactFilter_persistsJids() = runTest {
        val filter = ContactFilter.AllExcept(jids = listOf("excluded@s.whatsapp.net"))
        val id = ruleDao.insert(buildRuleEntity(contactFilter = filter))
        val retrieved = ruleDao.getById(id)
        assertEquals(filter, retrieved!!.contactFilter)
    }

    @Test
    fun insert_withSendMessageAction_persistsCorrectly() = runTest {
        val action = RuleAction.SendMessage(
            toJid = "dest@s.whatsapp.net",
            messageTemplate = "Hello!",
            generatorType = MessageGeneratorType.STATIC
        )
        val id = ruleDao.insert(buildRuleEntity(action = action))
        val retrieved = ruleDao.getById(id)
        assertEquals(action, retrieved!!.action)
    }

    @Test
    fun insert_withAutoReplyLlmAction_persistsGeneratorType() = runTest {
        val action = RuleAction.AutoReply(
            messageTemplate = "{{ai_response}}",
            generatorType = MessageGeneratorType.LLM
        )
        val id = ruleDao.insert(buildRuleEntity(action = action))
        val retrieved = ruleDao.getById(id)
        assertEquals(action, retrieved!!.action)
    }
}
