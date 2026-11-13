package com.example.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.PowerManager
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import com.example.telephony.BackgroundMonitorScheduler
import com.example.telephony.CallLogReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native OS JobService triggered by JobScheduler when CallLog.Calls.CONTENT_URI changes.
 * Runs inside Android's system_server watcher even when the app has been removed from Recents,
 * with zero persistent notification and zero idle battery drain.
 */
class CallLogTriggerJobService : JobService() {
    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeTask: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val appContext = applicationContext

        // Immediately re-arm the next ContentUri job watcher before processing current change
        BackgroundMonitorScheduler.scheduleCallLogJob(appContext, forceReschedule = true)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MissedCallSOS:CallLogJobWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(12_000L)
        }

        val preferences = AppPreferences(appContext)
        if (!preferences.isProtectionActive()) {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
            return false
        }

        val database = AppDatabase.getDatabase(appContext)
        val repository = SosRepository(database, preferences)

        activeTask = jobScope.launch {
            try {
                var processed = CallLogReconciler.reconcileMissedCalls(appContext, repository)
                if (processed == 0) {
                    delay(550L)
                    processed = CallLogReconciler.reconcileMissedCalls(appContext, repository)
                }
                if (processed == 0) {
                    delay(1000L)
                    processed = CallLogReconciler.reconcileMissedCalls(appContext, repository)
                }
                if (processed > 0) {
                    BackgroundMonitorScheduler.cancelRingFollowUpChecks(appContext)
                }
                if (SosAlarmManager.isAlarmActive) {
                    delay(1500L)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {}
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeTask?.cancel()
        BackgroundMonitorScheduler.scheduleCallLogJob(applicationContext, forceReschedule = true)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        jobScope.cancel()
    }
}
