package com.example.telephony

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.preferences.AppPreferences
import com.example.receiver.CallReconcileReceiver
import com.example.service.CallLogTriggerJobService
import java.util.concurrent.TimeUnit

/**
 * Zero-battery, zero-notification background monitoring scheduler.
 * Uses Android OS system_server triggers (JobScheduler TriggerContentUri + WorkManager ContentUriTrigger)
 * and exact follow-up checks during incoming call rings so calls are detected reliably even when
 * the app has been swiped away from Recents and the screen is turned off.
 */
object BackgroundMonitorScheduler {
    private const val JOB_ID_CALLLOG_TRIGGER = 4101
    private const val WORK_NAME_CALLLOG_SYNC = "sos_calllog_content_sync"
    private val RING_FOLLOWUP_DELAYS_MS = longArrayOf(6_000L, 18_000L, 40_000L, 65_000L)
    private val RING_FOLLOWUP_REQ_CODES = intArrayOf(2101, 2102, 2103, 2104)

    fun ensureMonitoringArmed(
        context: Context,
        forceRescheduleJob: Boolean = false,
        replaceWork: Boolean = false
    ) {
        val appContext = context.applicationContext
        val preferences = AppPreferences(appContext)
        if (!preferences.isOnboardingCompleted() && !CallLogReconciler.hasCallLogPermission(appContext)) {
            return
        }

        scheduleCallLogJob(appContext, forceReschedule = forceRescheduleJob)
        scheduleCallLogWork(appContext, replace = replaceWork)
    }

    fun scheduleCallLogJob(context: Context, forceReschedule: Boolean = false) {
        val appContext = context.applicationContext
        try {
            val js = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (!forceReschedule && js.getPendingJob(JOB_ID_CALLLOG_TRIGGER) != null) {
                return
            }

            val component = ComponentName(appContext, CallLogTriggerJobService::class.java)
            val triggerUri = JobInfo.TriggerContentUri(
                CallLog.Calls.CONTENT_URI,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
            )

            // Do NOT set setPersisted(true) on TriggerContentUri jobs (disallowed by Android framework)
            val jobInfo = JobInfo.Builder(JOB_ID_CALLLOG_TRIGGER, component)
                .addTriggerContentUri(triggerUri)
                .setTriggerContentUpdateDelay(0L)
                .setTriggerContentMaxDelay(150L)
                .build()

            js.schedule(jobInfo)
        } catch (_: Exception) {
        }
    }

    fun scheduleCallLogWork(context: Context, replace: Boolean = false) {
        val appContext = context.applicationContext
        try {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CallLog.Calls.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(0L, TimeUnit.MILLISECONDS)
                .setTriggerContentMaxDelay(150L, TimeUnit.MILLISECONDS)
                .build()

            val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                WORK_NAME_CALLLOG_SYNC,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Schedules exact wakeups while an incoming call is actively ringing so even if an OEM
     * drops the IDLE broadcast or kills the process mid-ring, Android AlarmManager wakes
     * CallReconcileReceiver to check the missed call right after ringing finishes.
     */
    fun scheduleRingFollowUpChecks(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        RING_FOLLOWUP_DELAYS_MS.forEachIndexed { idx, delayMs ->
            val reqCode = RING_FOLLOWUP_REQ_CODES[idx]
            val intent = Intent(appContext, CallReconcileReceiver::class.java).apply {
                action = CallReconcileReceiver.ACTION_RECONCILE_CALLS
                putExtra(CallReconcileReceiver.EXTRA_STEP_INDEX, idx)
            }
            val pi = PendingIntent.getBroadcast(
                appContext,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = now + delayMs
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (_: SecurityException) {
                try {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    fun cancelRingFollowUpChecks(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        RING_FOLLOWUP_REQ_CODES.forEach { reqCode ->
            val intent = Intent(appContext, CallReconcileReceiver::class.java).apply {
                action = CallReconcileReceiver.ACTION_RECONCILE_CALLS
            }
            val pi = PendingIntent.getBroadcast(
                appContext,
                reqCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                try {
                    am.cancel(pi)
                    pi.cancel()
                } catch (_: Exception) {}
            }
        }
    }
}
