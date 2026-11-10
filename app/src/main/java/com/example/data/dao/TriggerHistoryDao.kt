package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.TriggerHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: TriggerHistoryEntity): Long

    @Update
    suspend fun updateTrigger(trigger: TriggerHistoryEntity)

    @Query("SELECT * FROM trigger_history WHERE (:includeTest = 1 OR isTest = 0) ORDER BY triggeredAt DESC")
    fun getAllTriggersFlow(includeTest: Boolean = false): Flow<List<TriggerHistoryEntity>>

    @Query("SELECT * FROM trigger_history WHERE isTest = 0 ORDER BY triggeredAt DESC LIMIT 1")
    fun getLatestTriggerFlow(): Flow<TriggerHistoryEntity?>

    @Query("SELECT COUNT(*) FROM trigger_history WHERE isTest = 0")
    fun getTotalTriggersCountFlow(): Flow<Int>

    @Query("UPDATE trigger_history SET alarmDismissedAt = :dismissedAt, deliveryStatus = 'DISMISSED' WHERE id = :id")
    suspend fun markDismissed(id: Long, dismissedAt: Long)

    @Query("UPDATE trigger_history SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, status: String)

    @Query("DELETE FROM trigger_history WHERE callerNumber = :callerNumber")
    suspend fun deleteTriggersForNumber(callerNumber: String): Int

    @Query("DELETE FROM trigger_history WHERE isTest = 1")
    suspend fun deleteAllTestTriggers(): Int

    @Query("DELETE FROM trigger_history")
    suspend fun deleteAll()
}
