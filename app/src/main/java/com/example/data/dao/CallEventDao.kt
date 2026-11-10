package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.CallEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallEvent(event: CallEventEntity): Long

    @Query("SELECT * FROM call_events WHERE (:includeTest = 1 OR isTest = 0) ORDER BY timestamp DESC")
    fun getAllCallEvents(includeTest: Boolean = false): Flow<List<CallEventEntity>>

    @Query("SELECT * FROM call_events WHERE normalizedNumber = :normalizedNumber AND timestamp >= :sinceTimestamp AND isHandled = 0 AND isTest = :isTest ORDER BY timestamp DESC")
    suspend fun getUnhandledCallsSince(normalizedNumber: String, sinceTimestamp: Long, isTest: Boolean = false): List<CallEventEntity>

    @Query("SELECT COUNT(*) FROM call_events WHERE normalizedNumber = :normalizedNumber AND timestamp >= :sinceTimestamp AND isHandled = 0 AND isTest = :isTest")
    suspend fun countUnhandledCallsSince(normalizedNumber: String, sinceTimestamp: Long, isTest: Boolean = false): Int

    @Query("SELECT COUNT(*) FROM call_events WHERE normalizedNumber = :normalizedNumber AND timestamp = :timestamp")
    suspend fun countEventsWithTimestamp(normalizedNumber: String, timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM call_events WHERE timestamp >= :sinceTimestamp AND isTest = 0")
    fun countCallsTodayFlow(sinceTimestamp: Long): Flow<Int>

    @Query("UPDATE call_events SET isHandled = 1 WHERE normalizedNumber = :normalizedNumber")
    suspend fun markCallsAsHandled(normalizedNumber: String)

    @Query("UPDATE call_events SET isHandled = 1 WHERE timestamp <= :beforeTimestamp")
    suspend fun markAllCallsBeforeAsHandled(beforeTimestamp: Long)

    @Query("SELECT MAX(timestamp) FROM call_events WHERE isTest = 0")
    suspend fun getLatestEventTimestamp(): Long?

    @Query("DELETE FROM call_events WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteEventsForNumber(normalizedNumber: String): Int

    @Query("DELETE FROM call_events WHERE isTest = 1")
    suspend fun deleteAllTestEvents(): Int

    @Query("DELETE FROM call_events")
    suspend fun deleteAll()
}
