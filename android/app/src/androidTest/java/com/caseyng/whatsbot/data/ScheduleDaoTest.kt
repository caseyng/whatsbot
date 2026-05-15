package com.caseyng.whatsbot.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caseyng.whatsbot.domain.RecurrencePattern
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
class ScheduleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var scheduleDao: ScheduleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        scheduleDao = db.scheduleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun buildScheduleEntity(
        name: String = "Test Schedule",
        toJid: String = "dest@s.whatsapp.net",
        messageTemplate: String = "Hello!",
        sendAtMillis: Long = System.currentTimeMillis() + 60_000,
        recurrence: RecurrencePattern? = null,
        isActive: Boolean = true,
        createdAt: Long = System.currentTimeMillis(),
        lastSentAt: Long? = null
    ) = ScheduleEntity(
        id = 0,
        name = name,
        toJid = toJid,
        messageTemplate = messageTemplate,
        sendAtMillis = sendAtMillis,
        recurrence = recurrence,
        isActive = isActive,
        createdAt = createdAt,
        lastSentAt = lastSentAt
    )

    // ──────────────────────────────────────────────────────────────────────
    // insert / getById
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun insert_andGetById_returnsInsertedSchedule() = runTest {
        val entity = buildScheduleEntity(name = "Morning Greeting")
        val id = scheduleDao.insert(entity)
        val retrieved = scheduleDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Morning Greeting", retrieved!!.name)
        assertEquals(id, retrieved.id)
    }

    @Test
    fun getById_nonExistentId_returnsNull() = runTest {
        val retrieved = scheduleDao.getById(99999L)
        assertNull(retrieved)
    }

    // ──────────────────────────────────────────────────────────────────────
    // getActiveSchedules
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun getActiveSchedules_returnsOnlyActiveSchedules() = runTest {
        scheduleDao.insert(buildScheduleEntity(name = "Active", isActive = true))
        scheduleDao.insert(buildScheduleEntity(name = "Inactive", isActive = false))

        val activeSchedules = scheduleDao.getActiveSchedules()

        assertEquals(1, activeSchedules.size)
        assertEquals("Active", activeSchedules[0].name)
    }

    @Test
    fun getActiveSchedules_withNoActiveSchedules_returnsEmpty() = runTest {
        scheduleDao.insert(buildScheduleEntity(name = "Sched A", isActive = false))
        scheduleDao.insert(buildScheduleEntity(name = "Sched B", isActive = false))

        val activeSchedules = scheduleDao.getActiveSchedules()

        assertTrue("Should return empty list when all schedules are inactive", activeSchedules.isEmpty())
    }

    @Test
    fun getActiveSchedules_withAllActive_returnsAll() = runTest {
        scheduleDao.insert(buildScheduleEntity(name = "S1", isActive = true))
        scheduleDao.insert(buildScheduleEntity(name = "S2", isActive = true))
        scheduleDao.insert(buildScheduleEntity(name = "S3", isActive = true))

        val activeSchedules = scheduleDao.getActiveSchedules()

        assertEquals(3, activeSchedules.size)
    }

    // ──────────────────────────────────────────────────────────────────────
    // getPendingBefore
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun getPendingBefore_returnsOnlyActiveAndBeforeThreshold() = runTest {
        val now = 1_700_000_000_000L
        // Active and before threshold — should be returned
        scheduleDao.insert(buildScheduleEntity(name = "Due", sendAtMillis = now - 1_000, isActive = true))
        // Active but in the future — must NOT appear
        scheduleDao.insert(buildScheduleEntity(name = "Future", sendAtMillis = now + 1_000, isActive = true))
        // Inactive and before threshold — must NOT appear
        scheduleDao.insert(buildScheduleEntity(name = "Inactive Due", sendAtMillis = now - 1_000, isActive = false))

        val pending = scheduleDao.getPendingBefore(now)

        assertEquals(1, pending.size)
        assertEquals("Due", pending[0].name)
    }

    @Test
    fun getPendingBefore_activeScheduleExactlyAtThreshold_isIncluded() = runTest {
        val threshold = 1_700_000_000_000L
        scheduleDao.insert(buildScheduleEntity(name = "ExactlyAt", sendAtMillis = threshold, isActive = true))

        val pending = scheduleDao.getPendingBefore(threshold)

        assertEquals(
            "A schedule with sendAtMillis == threshold should be included (<=)",
            1,
            pending.size
        )
    }

    @Test
    fun getPendingBefore_activeScheduleOneMillisAfterThreshold_isExcluded() = runTest {
        val threshold = 1_700_000_000_000L
        scheduleDao.insert(buildScheduleEntity(name = "JustAfter", sendAtMillis = threshold + 1, isActive = true))

        val pending = scheduleDao.getPendingBefore(threshold)

        assertTrue(
            "A schedule with sendAtMillis > threshold must not appear",
            pending.isEmpty()
        )
    }

    @Test
    fun getPendingBefore_inactiveScheduleBeforeThreshold_isExcluded() = runTest {
        val threshold = 1_700_000_000_000L
        scheduleDao.insert(buildScheduleEntity(name = "InactivePast", sendAtMillis = threshold - 5_000, isActive = false))

        val pending = scheduleDao.getPendingBefore(threshold)

        assertTrue(
            "Inactive schedules must not appear even when sendAtMillis <= threshold",
            pending.isEmpty()
        )
    }

    @Test
    fun getPendingBefore_multipleEligible_allReturned() = runTest {
        val threshold = 1_700_000_000_000L
        scheduleDao.insert(buildScheduleEntity(name = "S1", sendAtMillis = threshold - 10_000, isActive = true))
        scheduleDao.insert(buildScheduleEntity(name = "S2", sendAtMillis = threshold - 5_000, isActive = true))
        scheduleDao.insert(buildScheduleEntity(name = "S3", sendAtMillis = threshold - 1, isActive = true))

        val pending = scheduleDao.getPendingBefore(threshold)

        assertEquals(3, pending.size)
    }

    // ──────────────────────────────────────────────────────────────────────
    // observeAll
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun observeAll_emitsAllSchedulesRegardlessOfActiveStatus() = runTest {
        scheduleDao.insert(buildScheduleEntity(name = "Active", isActive = true))
        scheduleDao.insert(buildScheduleEntity(name = "Inactive", isActive = false))

        val schedules = scheduleDao.observeAll().first()

        assertEquals(2, schedules.size)
    }

    @Test
    fun observeAll_emitsEmptyListWhenNoSchedules() = runTest {
        val schedules = scheduleDao.observeAll().first()
        assertTrue("observeAll should emit empty list on empty table", schedules.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────
    // setActive
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun setActive_false_removesScheduleFromActiveSchedules() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "Will Deactivate", isActive = true))
        assertEquals(1, scheduleDao.getActiveSchedules().size)

        scheduleDao.setActive(id, false)

        val activeSchedules = scheduleDao.getActiveSchedules()
        assertTrue(
            "After setActive(false), schedule must not appear in getActiveSchedules()",
            activeSchedules.none { it.id == id }
        )
    }

    @Test
    fun setActive_true_makesScheduleAppearInActiveSchedules() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "Initially Inactive", isActive = false))
        assertTrue("Schedule should not be active initially", scheduleDao.getActiveSchedules().isEmpty())

        scheduleDao.setActive(id, true)

        val activeSchedules = scheduleDao.getActiveSchedules()
        assertEquals(1, activeSchedules.size)
        assertEquals(id, activeSchedules[0].id)
    }

    @Test
    fun setActive_false_scheduleStillVisibleInObserveAll() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "Deactivated", isActive = true))

        scheduleDao.setActive(id, false)

        val all = scheduleDao.observeAll().first()
        assertEquals("Deactivated schedule should still appear in observeAll()", 1, all.size)
    }

    // ──────────────────────────────────────────────────────────────────────
    // updateLastSent
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun updateLastSent_updatesLastSentAtTimestamp() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "Schedule", lastSentAt = null))
        val sentAt = 1_700_000_000_000L

        scheduleDao.updateLastSent(id, sentAt)

        val retrieved = scheduleDao.getById(id)
        assertNotNull(retrieved)
        assertEquals(
            "getById should return the updated lastSentAt timestamp",
            sentAt,
            retrieved!!.lastSentAt
        )
    }

    @Test
    fun updateLastSent_overwritesPreviousTimestamp() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "Schedule", lastSentAt = 1_000_000L))
        val newTimestamp = 2_000_000L

        scheduleDao.updateLastSent(id, newTimestamp)

        val retrieved = scheduleDao.getById(id)
        assertEquals(newTimestamp, retrieved!!.lastSentAt)
    }

    // ──────────────────────────────────────────────────────────────────────
    // update / delete
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun update_modifiesExistingSchedule() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "Original"))
        val existing = scheduleDao.getById(id)!!
        val updated = existing.copy(name = "Updated", messageTemplate = "New message")

        scheduleDao.update(updated)

        val retrieved = scheduleDao.getById(id)
        assertEquals("Updated", retrieved!!.name)
        assertEquals("New message", retrieved.messageTemplate)
    }

    @Test
    fun delete_removesScheduleFromTable() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "To Delete"))
        val entity = scheduleDao.getById(id)!!

        scheduleDao.delete(entity)

        assertNull("After delete(), getById() must return null", scheduleDao.getById(id))
    }

    @Test
    fun delete_removesOnlyTargetedSchedule_othersUnaffected() = runTest {
        val id1 = scheduleDao.insert(buildScheduleEntity(name = "Schedule 1"))
        val id2 = scheduleDao.insert(buildScheduleEntity(name = "Schedule 2"))
        val entity1 = scheduleDao.getById(id1)!!

        scheduleDao.delete(entity1)

        assertNull(scheduleDao.getById(id1))
        assertNotNull(scheduleDao.getById(id2))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Recurrence persistence
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun insert_oneTimeSchedule_nullRecurrence_persistsCorrectly() = runTest {
        val id = scheduleDao.insert(buildScheduleEntity(name = "One-Time", recurrence = null))
        val retrieved = scheduleDao.getById(id)
        assertNotNull(retrieved)
        assertNull("One-time schedule must have null recurrence", retrieved!!.recurrence)
    }

    @Test
    fun insert_recurringSchedule_nonNullRecurrence_persistsCorrectly() = runTest {
        val pattern = RecurrencePattern(cronExpression = "0 9 * * MON-FRI")
        val id = scheduleDao.insert(buildScheduleEntity(name = "Daily Standup", recurrence = pattern))
        val retrieved = scheduleDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("RecurrencePattern must round-trip through database", pattern, retrieved!!.recurrence)
    }

    @Test
    fun insert_recurringSchedule_cronExpressionPreservedExactly() = runTest {
        val cron = "0 8 1 * *"
        val pattern = RecurrencePattern(cronExpression = cron)
        val id = scheduleDao.insert(buildScheduleEntity(recurrence = pattern))
        val retrieved = scheduleDao.getById(id)
        assertEquals(cron, retrieved!!.recurrence?.cronExpression)
    }

    @Test
    fun bothOneTimeAndRecurringSchedules_persistAndRetrieveCorrectly() = runTest {
        val recurring = RecurrencePattern(cronExpression = "0 9 * * *")
        val idOneTime = scheduleDao.insert(buildScheduleEntity(name = "One-Time", recurrence = null))
        val idRecurring = scheduleDao.insert(buildScheduleEntity(name = "Recurring", recurrence = recurring))

        val oneTime = scheduleDao.getById(idOneTime)
        val recurringResult = scheduleDao.getById(idRecurring)

        assertNull(oneTime!!.recurrence)
        assertEquals(recurring, recurringResult!!.recurrence)
    }
}
