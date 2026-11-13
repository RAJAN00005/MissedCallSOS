package com.example.telephony

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import kotlinx.coroutines.delay

/**
 * WorkManager backup worker bound to CallLog.Calls.CONTENT_URI changes.
 */
class CallLogSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val preferences = AppPreferences(appContext)
        val database = AppDatabase.getDatabase(appContext)
        val repository = SosRepository(database, preferences)

        try {
            if (preferences.isProtectionActive()) {
                var processed = CallLogReconciler.reconcileMissedCalls(appContext, repository)
                if (processed == 0) {
                    delay(550L)
                    processed = CallLogReconciler.reconcileMissedCalls(appContext, repository)
                }
                if (processed > 0) {
                    BackgroundMonitorScheduler.cancelRingFollowUpChecks(appContext)
                }
                if (SosAlarmManager.isAlarmActive) {
                    delay(1200L)
                }
            }
        } catch (_: Exception) {
        } finally {
            // Re-arm watcher for future missed calls
            BackgroundMonitorScheduler.scheduleCallLogWork(appContext, replace = true)
            BackgroundMonitorScheduler.scheduleCallLogJob(appContext, forceReschedule = false)
        }

        return Result.success()
    }
}
