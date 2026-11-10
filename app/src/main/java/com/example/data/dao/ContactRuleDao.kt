package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ContactRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: ContactRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ContactRuleEntity>)

    @Update
    suspend fun update(rule: ContactRuleEntity)

    @Delete
    suspend fun delete(rule: ContactRuleEntity)

    @Delete
    suspend fun deleteRules(rules: List<ContactRuleEntity>)

    @Query("SELECT * FROM contact_rules ORDER BY displayName ASC")
    fun getAllRulesFlow(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules")
    suspend fun getAllRulesList(): List<ContactRuleEntity>

    @Query("SELECT * FROM contact_rules WHERE isAlarmEnabled = 1 ORDER BY displayName ASC")
    fun getAlarmRulesFlow(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    suspend fun getRuleForNumber(normalizedNumber: String): ContactRuleEntity?

    @Query("UPDATE contact_rules SET lastTriggerTimestamp = :timestamp WHERE normalizedNumber = :normalizedNumber")
    suspend fun updateLastTrigger(normalizedNumber: String, timestamp: Long)

    @Query("DELETE FROM contact_rules WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteByNumber(normalizedNumber: String): Int

    @Query("DELETE FROM contact_rules WHERE normalizedNumber IN (:numbers)")
    suspend fun deleteByNumbers(numbers: List<String>): Int

    @Query("DELETE FROM contact_rules WHERE normalizedNumber IN (:legacyNumbers)")
    suspend fun deleteLegacyNumbers(legacyNumbers: List<String>): Int

    @Query("DELETE FROM contact_rules")
    suspend fun deleteAll()
}
