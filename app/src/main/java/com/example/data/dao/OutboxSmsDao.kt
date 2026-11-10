package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.OutboxSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxSmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxSms(sms: OutboxSmsEntity): Long

    @Update
    suspend fun updateOutboxSms(sms: OutboxSmsEntity)

    @Query("SELECT * FROM outbox_sms WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSmsList(): List<OutboxSmsEntity>

    @Query("SELECT * FROM outbox_sms WHERE (:includeTest = 1 OR isTest = 0) ORDER BY createdAt DESC")
    fun getAllOutboxSmsFlow(includeTest: Boolean = false): Flow<List<OutboxSmsEntity>>

    @Query("UPDATE outbox_sms SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE outbox_sms SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String?)

    @Query("SELECT COUNT(*) FROM outbox_sms WHERE recipientNumber = :number AND createdAt >= :sinceTimestamp AND isTest = 0")
    suspend fun countSmsSentToRecipientSince(number: String, sinceTimestamp: Long): Int

    @Query("DELETE FROM outbox_sms WHERE recipientNumber = :recipientNumber AND status = 'PENDING'")
    suspend fun cancelPendingSmsForNumber(recipientNumber: String): Int

    @Query("DELETE FROM outbox_sms WHERE recipientNumber = :recipientNumber")
    suspend fun deleteSmsForNumber(recipientNumber: String): Int

    @Query("DELETE FROM outbox_sms WHERE isTest = 1")
    suspend fun deleteAllTestSms(): Int

    @Query("DELETE FROM outbox_sms")
    suspend fun deleteAll()
}
