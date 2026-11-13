package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.data.repository.SosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object CallLogReconciler {
    private val reconcileMutex = Mutex()

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isUnansweredCallType(type: Int, duration: Long): Boolean {
        return type == CallLog.Calls.MISSED_TYPE ||
                type == CallLog.Calls.REJECTED_TYPE ||
                type == CallLog.Calls.BLOCKED_TYPE ||
                (type == CallLog.Calls.INCOMING_TYPE && duration == 0L)
    }

    /**
     * Reconciles recent unanswered/missed/rejected calls from Android's CallLog.
     * Safely exits with 0 if READ_CALL_LOG permission is not granted (avoiding SecurityException in Logcat).
     */
    suspend fun reconcileMissedCalls(context: Context, repository: SosRepository): Int = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission(context)) {
            return@withContext 0
        }

        reconcileMutex.withLock {
            if (!hasCallLogPermission(context)) {
                return@withLock 0
            }

            val now = System.currentTimeMillis()
            var baselineTimestamp = repository.preferences.getLastCallLogCheckTimestamp()
            val defaultCountryIso = PhoneNumberHelper.getDeviceCountryIso(context)

            // Initialize baseline timestamp once so pre-install history is ignored
            if (baselineTimestamp == 0L || baselineTimestamp > now) {
                baselineTimestamp = now - 10_000L
                repository.preferences.setLastCallLogCheckTimestamp(baselineTimestamp)
            }

            // Look back up to 5 minutes, bounded by baselineTimestamp
            val minTime = maxOf(baselineTimestamp, now - 300_000L)

            data class RawLogRow(
                val number: String,
                val date: Long,
                val type: Int,
                val duration: Long,
                val cachedName: String?
            )

            val rows = mutableListOf<RawLogRow>()

            // Simple single-column selection so no OEM CallLogProvider ever rejects the SQL query
            val cursor: Cursor? = try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.DATE,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DURATION,
                        CallLog.Calls.CACHED_NAME
                    ),
                    "${CallLog.Calls.DATE} >= ?",
                    arrayOf(minTime.toString()),
                    "${CallLog.Calls.DATE} ASC"
                )
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (it.moveToNext()) {
                    val number = if (numIdx >= 0) it.getString(numIdx) else null
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                    val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx) else null

                    if (!number.isNullOrBlank() && number != "UNKNOWN" && date >= minTime) {
                        rows.add(RawLogRow(number, date, type, duration, cachedName))
                    }
                }
            }

            var newlyProcessed = 0
            for (row in rows) {
                if (isUnansweredCallType(row.type, row.duration)) {
                    val norm = PhoneNumberHelper.normalizeToE164(row.number, defaultCountryIso)
                    val rule = repository.getRuleForNumber(norm) ?: repository.getRuleForNumber(row.number)
                    val canonicalNumber = rule?.normalizedNumber ?: norm

                    if (!repository.hasCallEvent(canonicalNumber, row.date, toleranceMs = 250L)) {
                        CallStateTracker.processMissedCallInternal(
                            context = context,
                            repository = repository,
                            rawNumber = row.number,
                            timestamp = row.date,
                            simSlot = -1,
                            defaultCountryIso = defaultCountryIso,
                            isTest = false,
                            callerNameHint = row.cachedName,
                            dedupToleranceMs = 250L
                        )
                        newlyProcessed++
                    }
                } else if ((row.type == CallLog.Calls.INCOMING_TYPE && row.duration > 0L) ||
                    row.type == CallLog.Calls.OUTGOING_TYPE
                ) {
                    val norm = PhoneNumberHelper.normalizeToE164(row.number, defaultCountryIso)
                    val rule = repository.getRuleForNumber(norm) ?: repository.getRuleForNumber(row.number)
                    val canonicalNumber = rule?.normalizedNumber ?: norm
                    repository.markCallsAsHandled(canonicalNumber)
                }
            }
            newlyProcessed
        }
    }

    suspend fun getLatestUnprocessedMissedCall(
        context: Context,
        repository: SosRepository,
        minDate: Long
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission(context)) {
            return@withContext null
        }
        val defaultCountryIso = PhoneNumberHelper.getDeviceCountryIso(context)
        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
            )

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(minDate.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L

                    if (!number.isNullOrBlank() && date >= minDate && isUnansweredCallType(type, duration)) {
                        val norm = PhoneNumberHelper.normalizeToE164(number, defaultCountryIso)
                        val rule = repository.getRuleForNumber(norm) ?: repository.getRuleForNumber(number)
                        val canonicalNumber = rule?.normalizedNumber ?: norm
                        if (!repository.hasCallEvent(canonicalNumber, date, toleranceMs = 250L)) {
                            return@withContext Pair(number, date)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Permission or security exception
        }
        null
    }

    suspend fun getLatestMissedCall(
        context: Context,
        maxAgeMs: Long = 180_000L
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission(context)) {
            return@withContext null
        }
        val now = System.currentTimeMillis()
        val minTime = now - maxAgeMs

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
            )

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(minTime.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L

                    if (!number.isNullOrBlank() && isUnansweredCallType(type, duration)) {
                        return@withContext Pair(number, date)
                    }
                }
            }
        } catch (_: Exception) {
            // Permission or security exception
        }
        null
    }
}
