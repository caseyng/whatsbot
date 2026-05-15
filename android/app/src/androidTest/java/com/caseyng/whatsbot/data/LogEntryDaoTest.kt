package com.caseyng.whatsbot.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caseyng.whatsbot.domain.LogEntryType
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogEntryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var logEntryDao: LogEntryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        logEntryDao = db.logEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun buildLogEntry(
        type: LogEntryType = LogEntryType.RULE_FIRED,
        timestamp: Long = System.currentTimeMillis(),
        ruleName: String? = null,
        scheduleName: String? = null,
        contactJid: String? = null,
        contactName: String? = null,
        messagePreview: String? = null,
        outcome: String? = null,
        detail: String? = null
    ) = LogEntryEntity(
        id = 0,
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

    // ──────────────────────────────────────────────────────────────────────
    // insert / getCount
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun insert_incrementsCount() = runTest {
        assertEquals(0, logEntryDao.getCount())

        logEntryDao.insert(buildLogEntry())
        assertEquals(1, logEntryDao.getCount())

        logEntryDao.insert(buildLogEntry())
        assertEquals(2, logEntryDao.getCount())
    }

    @Test
    fun getCount_returnsZeroOnEmptyTable() = runTest {
        assertEquals(0, logEntryDao.getCount())
    }

    // ──────────────────────────────────────────────────────────────────────
    // observeAll
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun observeAll_emitsAllInsertedEntries() = runTest {
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_SENT))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.ERROR))

        val entries = logEntryDao.observeAll().first()

        assertEquals(3, entries.size)
    }

    @Test
    fun observeAll_emitsEmptyListOnEmptyTable() = runTest {
        val entries = logEntryDao.observeAll().first()
        assertTrue("observeAll should emit empty list on empty table", entries.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────
    // observeByType
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun observeByType_returnsOnlyMatchingType() = runTest {
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_SKIPPED))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_SENT))

        val results = logEntryDao.observeByType(LogEntryType.RULE_FIRED.name).first()

        assertEquals(2, results.size)
        assertTrue(
            "All returned entries must have type RULE_FIRED",
            results.all { it.type == LogEntryType.RULE_FIRED }
        )
    }

    @Test
    fun observeByType_doesNotReturnOtherTypes() = runTest {
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_SENT))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.ERROR))

        val results = logEntryDao.observeByType(LogEntryType.RULE_FIRED.name).first()

        assertEquals(1, results.size)
        assertTrue(
            "No non-RULE_FIRED entries should be returned",
            results.none { it.type != LogEntryType.RULE_FIRED }
        )
    }

    @Test
    fun observeByType_returnsEmptyWhenNoMatchingEntries() = runTest {
        logEntryDao.insert(buildLogEntry(type = LogEntryType.ERROR))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.ERROR))

        val results = logEntryDao.observeByType(LogEntryType.RULE_FIRED.name).first()

        assertTrue("observeByType should return empty list when no entries match", results.isEmpty())
    }

    @Test
    fun observeByType_usedWithLogEntryTypeName_allTypesWork() = runTest {
        for (type in LogEntryType.values()) {
            logEntryDao.insert(buildLogEntry(type = type))
        }

        for (type in LogEntryType.values()) {
            val results = logEntryDao.observeByType(type.name).first()
            assertEquals(
                "observeByType(${type.name}) should return exactly one entry",
                1,
                results.size
            )
            assertEquals(type, results[0].type)
        }
    }

    @Test
    fun observeByType_scheduleSent_filterCorrect() = runTest {
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_SENT, scheduleName = "Daily Report"))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_FAILED, scheduleName = "Weekly Summary"))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED))

        val results = logEntryDao.observeByType(LogEntryType.SCHEDULE_SENT.name).first()

        assertEquals(1, results.size)
        assertEquals(LogEntryType.SCHEDULE_SENT, results[0].type)
    }

    // ──────────────────────────────────────────────────────────────────────
    // deleteOlderThan
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun deleteOlderThan_deletesEntriesWithTimestampLessThanThreshold() = runTest {
        val threshold = 1_700_000_000_000L
        logEntryDao.insert(buildLogEntry(timestamp = threshold - 1_000))
        logEntryDao.insert(buildLogEntry(timestamp = threshold - 1))
        logEntryDao.insert(buildLogEntry(timestamp = threshold))       // boundary — must be kept
        logEntryDao.insert(buildLogEntry(timestamp = threshold + 1))   // must be kept

        logEntryDao.deleteOlderThan(threshold)

        val count = logEntryDao.getCount()
        assertEquals(
            "deleteOlderThan(t) must keep entries with timestamp >= t",
            2,
            count
        )
    }

    @Test
    fun deleteOlderThan_keepsEntriesAtExactThreshold() = runTest {
        val threshold = 1_700_000_000_000L
        logEntryDao.insert(buildLogEntry(timestamp = threshold))

        logEntryDao.deleteOlderThan(threshold)

        val count = logEntryDao.getCount()
        assertEquals(
            "Entry with timestamp == threshold must NOT be deleted",
            1,
            count
        )
    }

    @Test
    fun deleteOlderThan_deletesAllEntriesBeforeThreshold() = runTest {
        val threshold = 1_700_000_000_000L
        logEntryDao.insert(buildLogEntry(timestamp = threshold - 10_000))
        logEntryDao.insert(buildLogEntry(timestamp = threshold - 5_000))
        logEntryDao.insert(buildLogEntry(timestamp = threshold - 1))

        logEntryDao.deleteOlderThan(threshold)

        val count = logEntryDao.getCount()
        assertEquals("All entries with timestamp < threshold must be deleted", 0, count)
    }

    @Test
    fun deleteOlderThan_whenNoEntriesMatchThreshold_doesNotDeleteAnything() = runTest {
        val now = System.currentTimeMillis()
        logEntryDao.insert(buildLogEntry(timestamp = now + 10_000))
        logEntryDao.insert(buildLogEntry(timestamp = now + 20_000))

        logEntryDao.deleteOlderThan(now)

        assertEquals("No entries should be deleted when none are before threshold", 2, logEntryDao.getCount())
    }

    @Test
    fun deleteOlderThan_mixedEntries_onlyOldOnesRemoved() = runTest {
        val threshold = 1_700_000_000_000L
        logEntryDao.insert(buildLogEntry(type = LogEntryType.ERROR, timestamp = threshold - 5_000))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED, timestamp = threshold + 5_000))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_SENT, timestamp = threshold))

        logEntryDao.deleteOlderThan(threshold)

        val remaining = logEntryDao.observeAll().first()
        assertEquals(2, remaining.size)
        assertTrue(
            "All remaining entries must have timestamp >= threshold",
            remaining.all { it.timestamp >= threshold }
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // deleteAll
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun deleteAll_leavesTableEmpty() = runTest {
        logEntryDao.insert(buildLogEntry(type = LogEntryType.RULE_FIRED))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.ERROR))
        logEntryDao.insert(buildLogEntry(type = LogEntryType.SCHEDULE_SENT))
        assertEquals(3, logEntryDao.getCount())

        logEntryDao.deleteAll()

        assertEquals("After deleteAll(), getCount() must return 0", 0, logEntryDao.getCount())
    }

    @Test
    fun deleteAll_onEmptyTable_doesNotThrow() = runTest {
        // Should not throw on empty table
        logEntryDao.deleteAll()
        assertEquals(0, logEntryDao.getCount())
    }

    @Test
    fun deleteAll_observeAll_emitsEmptyListAfterDeletion() = runTest {
        logEntryDao.insert(buildLogEntry())
        logEntryDao.insert(buildLogEntry())

        logEntryDao.deleteAll()

        val entries = logEntryDao.observeAll().first()
        assertTrue("After deleteAll(), observeAll() must emit empty list", entries.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Optional field persistence
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun insert_withAllOptionalFields_persistsCorrectly() = runTest {
        val entry = buildLogEntry(
            type = LogEntryType.RULE_FIRED,
            ruleName = "Morning Rule",
            scheduleName = null,
            contactJid = "target@s.whatsapp.net",
            contactName = "Alice",
            messagePreview = "Hello Alice",
            outcome = "sent",
            detail = "Matched keyword"
        )
        logEntryDao.insert(entry)

        val entries = logEntryDao.observeAll().first()
        assertEquals(1, entries.size)
        val retrieved = entries[0]
        assertEquals("Morning Rule", retrieved.ruleName)
        assertEquals("target@s.whatsapp.net", retrieved.contactJid)
        assertEquals("Alice", retrieved.contactName)
        assertEquals("Hello Alice", retrieved.messagePreview)
        assertEquals("sent", retrieved.outcome)
        assertEquals("Matched keyword", retrieved.detail)
    }

    @Test
    fun insert_withAllNullOptionalFields_persistsCorrectly() = runTest {
        val entry = buildLogEntry(
            type = LogEntryType.CONNECTION_CONNECTED,
            ruleName = null,
            scheduleName = null,
            contactJid = null,
            contactName = null,
            messagePreview = null,
            outcome = null,
            detail = null
        )
        logEntryDao.insert(entry)

        val entries = logEntryDao.observeAll().first()
        assertEquals(1, entries.size)
        val retrieved = entries[0]
        assertEquals(null, retrieved.ruleName)
        assertEquals(null, retrieved.contactJid)
    }
}
