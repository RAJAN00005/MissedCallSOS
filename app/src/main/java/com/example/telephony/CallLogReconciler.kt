package com.example.telephony

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.example.data.repository.SosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CallLogReconciler {

    suspend fun reconcileMissedCalls(context: Context, repository: SosRepository) = withContext(Dispatchers.IO) {
        val lastCheck = repository.preferences.getLastCallLogCheckTimestamp()
        val now = System.currentTimeMillis()
        val defaultCountryIso = PhoneNumberHelper.getDeviceCountryIso(context)

        // Always update timestamp to now
        repository.preferences.setLastCallLogCheckTimestamp(now)

        // If this is the initial check or app startup without prior timestamp, never process historical logs!
        if (lastCheck == 0L) {
            return@withContext
        }

        // Only reconcile calls that occurred strictly within the last 180 seconds and after lastCheck
        val minTime = Math.max(lastCheck, now - 180_000L)
        if (minTime >= now) {
            return@withContext
        }

        val cursor: Cursor? = try {
            val selection = "${CallLog.Calls.TYPE} IN (3, 5) AND ${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(minTime.toString())
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.CACHED_NAME
                ),
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} ASC"
            )
        } catch (e: SecurityException) {
            null
        }

        cursor?.use {
            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)

            while (it.moveToNext()) {
                val number = if (numIdx >= 0) it.getString(numIdx) else null
                val date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L

                // Strictly ensure call is recent (within 180s) and not historical
                if (!number.isNullOrBlank() && date > minTime && (now - date) <= 180_000L) {
                    CallStateTracker.processMissedCall(
                        context = context,
                        repository = repository,
                        rawNumber = number,
                        timestamp = date,
                        simSlot = -1,
                        defaultCountryIso = defaultCountryIso
                    )
                }
            }
        }
    }

    suspend fun getLatestUnprocessedMissedCall(
        context: Context,
        repository: SosRepository,
        minDate: Long
    ): Pair<String, Long>? = withContext(Dispatchers.IO) {
        val defaultCountryIso = PhoneNumberHelper.getDeviceCountryIso(context)
        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            )
            val selection = "${CallLog.Calls.TYPE} IN (3, 5) AND ${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(minDate.toString())

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

                while (cursor.moveToNext()) {
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L

                    if (!number.isNullOrBlank() && date >= minDate) {
                        val norm = PhoneNumberHelper.normalizeToE164(number, defaultCountryIso)
                        if (!repository.hasCallEvent(norm, date)) {
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
        val now = System.currentTimeMillis()
        val minTime = now - maxAgeMs

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            )
            val selection = "${CallLog.Calls.TYPE} IN (3, 5) AND ${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(minTime.toString())

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val number = if (numIdx >= 0) cursor.getString(numIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L

                    if (!number.isNullOrBlank()) {
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
