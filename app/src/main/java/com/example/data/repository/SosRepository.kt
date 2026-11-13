package com.example.data.repository

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.entity.CallEventEntity
import com.example.data.entity.ContactRuleEntity
import com.example.data.entity.OutboxSmsEntity
import com.example.data.entity.TriggerHistoryEntity
import com.example.data.preferences.AppPreferences
import com.example.telephony.PhoneNumberHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DeviceContact(
    val id: String,
    val name: String,
    val number: String,
    val lookupKey: String? = null
)

class SosRepository(
    private val database: AppDatabase,
    val preferences: AppPreferences
) {
    private val callEventDao = database.callEventDao()
    private val contactRuleDao = database.contactRuleDao()
    private val triggerHistoryDao = database.triggerHistoryDao()
    private val outboxSmsDao = database.outboxSmsDao()

    val allCallEventsFlow: Flow<List<CallEventEntity>> = callEventDao.getAllCallEvents(includeTest = false)
    val allRulesFlow: Flow<List<ContactRuleEntity>> = contactRuleDao.getAllRulesFlow()
    val alarmRulesFlow: Flow<List<ContactRuleEntity>> = contactRuleDao.getAlarmRulesFlow()
    val allTriggersFlow: Flow<List<TriggerHistoryEntity>> = triggerHistoryDao.getAllTriggersFlow(includeTest = false)
    val latestTriggerFlow: Flow<TriggerHistoryEntity?> = triggerHistoryDao.getLatestTriggerFlow()
    val totalTriggersCountFlow: Flow<Int> = triggerHistoryDao.getTotalTriggersCountFlow()
    val allOutboxSmsFlow: Flow<List<OutboxSmsEntity>> = outboxSmsDao.getAllOutboxSmsFlow(includeTest = false)

    fun getTodayCallsCountFlow(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return callEventDao.countCallsTodayFlow(calendar.timeInMillis)
    }

    suspend fun insertCallEvent(event: CallEventEntity): Long = withContext(Dispatchers.IO) {
        callEventDao.insertCallEvent(event)
    }

    suspend fun hasCallEvent(normalizedNumber: String, timestamp: Long, toleranceMs: Long = 3500L): Boolean = withContext(Dispatchers.IO) {
        callEventDao.countEventsNearTimestamp(
            normalizedNumber = normalizedNumber,
            minTimestamp = timestamp - toleranceMs,
            maxTimestamp = timestamp + toleranceMs
        ) > 0
    }

    suspend fun hasAnyCallEventSince(sinceTimestamp: Long): Boolean = withContext(Dispatchers.IO) {
        callEventDao.countAnyEventsSince(sinceTimestamp) > 0
    }

    suspend fun getUnhandledCallsSince(normalizedNumber: String, sinceTimestamp: Long, isTest: Boolean = false): List<CallEventEntity> =
        withContext(Dispatchers.IO) {
            callEventDao.getUnhandledCallsSince(normalizedNumber, sinceTimestamp, isTest)
        }

    suspend fun countUnhandledCallsSince(normalizedNumber: String, sinceTimestamp: Long, isTest: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            callEventDao.countUnhandledCallsSince(normalizedNumber, sinceTimestamp, isTest)
        }

    suspend fun markCallsAsHandled(normalizedNumber: String) = withContext(Dispatchers.IO) {
        callEventDao.markCallsAsHandled(normalizedNumber)
    }

    suspend fun markAllCallsBeforeAsHandled(beforeTimestamp: Long) = withContext(Dispatchers.IO) {
        callEventDao.markAllCallsBeforeAsHandled(beforeTimestamp)
    }

    suspend fun getRuleForNumber(number: String): ContactRuleEntity? = withContext(Dispatchers.IO) {
        val trimmed = number.trim()
        if (trimmed.isBlank() || trimmed == "UNKNOWN") return@withContext null

        // 1. Direct match on normalized / raw string
        val direct = contactRuleDao.getRuleForNumber(trimmed)
        if (direct != null) return@withContext direct

        // 2. Normalize and check again
        val norm = PhoneNumberHelper.normalizeToE164(trimmed)
        if (norm != "UNKNOWN" && norm != trimmed) {
            val normMatch = contactRuleDao.getRuleForNumber(norm)
            if (normMatch != null) return@withContext normMatch
        }

        // 3. Fallback: fuzzy/suffix match across all configured rules
        val allRules = contactRuleDao.getAllRulesList()
        val queryDigits = trimmed.filter { it.isDigit() }
        val queryLast10 = if (queryDigits.length >= 10) queryDigits.takeLast(10) else queryDigits

        allRules.firstOrNull { rule ->
            PhoneNumberHelper.isSameNumber(rule.normalizedNumber, trimmed) ||
            PhoneNumberHelper.isSameNumber(rule.rawNumber, trimmed) ||
            (queryLast10.length >= 7 && (
                rule.normalizedNumber.filter { it.isDigit() }.endsWith(queryLast10) ||
                rule.rawNumber.filter { it.isDigit() }.endsWith(queryLast10)
            ))
        }
    }

    suspend fun getPrimaryActiveRule(): ContactRuleEntity? = withContext(Dispatchers.IO) {
        val allRules = contactRuleDao.getAllRulesList()
        allRules.firstOrNull { it.isAlarmEnabled } ?: allRules.firstOrNull()
    }

    suspend fun saveRule(rule: ContactRuleEntity): Long = withContext(Dispatchers.IO) {
        contactRuleDao.insertOrUpdate(rule)
    }

    suspend fun saveRules(rules: List<ContactRuleEntity>) = withContext(Dispatchers.IO) {
        contactRuleDao.insertAll(rules)
    }

    suspend fun deleteRule(rule: ContactRuleEntity) = withContext(Dispatchers.IO) {
        contactRuleDao.delete(rule)
    }

    /**
     * Real complete removal of a contact:
     * - Deletes rule from contact_rules
     * - Deletes unhandled call events for this number to reset counters
     * - Cancels pending outbox SMS
     * - Optionally deletes trigger history & SMS logs for this number
     */
    suspend fun removeContact(normalizedNumber: String, deleteHistory: Boolean = false) = withContext(Dispatchers.IO) {
        contactRuleDao.deleteByNumber(normalizedNumber)
        callEventDao.deleteEventsForNumber(normalizedNumber)
        outboxSmsDao.cancelPendingSmsForNumber(normalizedNumber)
        if (deleteHistory) {
            triggerHistoryDao.deleteTriggersForNumber(normalizedNumber)
            outboxSmsDao.deleteSmsForNumber(normalizedNumber)
        }
    }

    suspend fun removeContacts(normalizedNumbers: List<String>, deleteHistory: Boolean = false) = withContext(Dispatchers.IO) {
        contactRuleDao.deleteByNumbers(normalizedNumbers)
        for (num in normalizedNumbers) {
            callEventDao.deleteEventsForNumber(num)
            outboxSmsDao.cancelPendingSmsForNumber(num)
            if (deleteHistory) {
                triggerHistoryDao.deleteTriggersForNumber(num)
                outboxSmsDao.deleteSmsForNumber(num)
            }
        }
    }

    suspend fun removeAllContacts(deleteHistory: Boolean = false) = withContext(Dispatchers.IO) {
        contactRuleDao.deleteAll()
        callEventDao.deleteAll()
        if (deleteHistory) {
            triggerHistoryDao.deleteAll()
            outboxSmsDao.deleteAll()
        }
    }

    suspend fun updateAlarmOnly(normalizedNumber: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val existing = contactRuleDao.getRuleForNumber(normalizedNumber)
        if (existing != null) {
            contactRuleDao.update(existing.copy(isAlarmEnabled = enabled))
        }
    }

    suspend fun purgeLegacySeededContacts(): Int = withContext(Dispatchers.IO) {
        val legacyNumbers = listOf(
            "+91" + "98765" + "43210",
            "+91" + "98765" + "43211",
            "112"
        )
        val deleted = contactRuleDao.deleteLegacyNumbers(legacyNumbers)
        if (deleted > 0) {
            Log.d("SosRepository", "Purged $deleted legacy seeded contacts on startup")
            for (num in legacyNumbers) {
                callEventDao.deleteEventsForNumber(num)
                triggerHistoryDao.deleteTriggersForNumber(num)
                outboxSmsDao.deleteSmsForNumber(num)
            }
        }
        deleted
    }

    suspend fun purgeAllTestData() = withContext(Dispatchers.IO) {
        callEventDao.deleteAllTestEvents()
        triggerHistoryDao.deleteAllTestTriggers()
        outboxSmsDao.deleteAllTestSms()
    }

    suspend fun updateLastTrigger(normalizedNumber: String, timestamp: Long) = withContext(Dispatchers.IO) {
        contactRuleDao.updateLastTrigger(normalizedNumber, timestamp)
    }

    suspend fun insertTrigger(trigger: TriggerHistoryEntity): Long = withContext(Dispatchers.IO) {
        triggerHistoryDao.insertTrigger(trigger)
    }

    suspend fun markTriggerDismissed(id: Long, dismissedAt: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            triggerHistoryDao.markDismissed(id, dismissedAt)
        }

    suspend fun updateTriggerDeliveryStatus(id: Long, status: String) = withContext(Dispatchers.IO) {
        triggerHistoryDao.updateDeliveryStatus(id, status)
    }

    suspend fun queueOutboxSms(sms: OutboxSmsEntity): Long = withContext(Dispatchers.IO) {
        outboxSmsDao.insertOutboxSms(sms)
    }

    suspend fun getPendingOutboxSms(): List<OutboxSmsEntity> = withContext(Dispatchers.IO) {
        outboxSmsDao.getPendingSmsList()
    }

    suspend fun updateOutboxSmsStatus(id: Long, status: String, error: String? = null) = withContext(Dispatchers.IO) {
        outboxSmsDao.updateStatus(id, status, error)
    }

    suspend fun countSmsSentToRecipientSince(number: String, sinceTimestamp: Long): Int = withContext(Dispatchers.IO) {
        outboxSmsDao.countSmsSentToRecipientSince(number, sinceTimestamp)
    }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        callEventDao.deleteAll()
        contactRuleDao.deleteAll()
        triggerHistoryDao.deleteAll()
        outboxSmsDao.deleteAll()
    }

    suspend fun fetchDeviceContacts(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<DeviceContact>()
        val contentResolver = context.contentResolver
        val cursor: Cursor? = try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
        } catch (e: SecurityException) {
            null
        }

        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val lookupIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

            while (it.moveToNext()) {
                val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                val num = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                val lookup = if (lookupIdx >= 0) it.getString(lookupIdx) else null
                if (num.isNotBlank()) {
                    contactsList.add(DeviceContact(id = id, name = name, number = num, lookupKey = lookup))
                }
            }
        }
        contactsList
    }
}
