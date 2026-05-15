package com.caseyng.whatsbot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE type = :type ORDER BY timestamp DESC")
    fun observeByType(type: String): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE timestamp < :timestampMillis")
    suspend fun deleteOlderThan(timestampMillis: Long)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun getCount(): Int
}
