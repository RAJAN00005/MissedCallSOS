package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import com.example.telephony.BackgroundMonitorScheduler
import com.example.telephony.CallLogReconciler
import com.example.telephony.CallStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Exact AlarmManager follow-up receiver armed during incoming call rings.
 * Guarantees missed call detection and SOS alarm trigger even on OEM ROMs that
 * delay or suppress IDLE phone state broadcasts when the app is not in Recents.
 */
class CallReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECONCILE_CALLS) return

        val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, 0)
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MissedCallSOS:CallReconcileWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(12_000L)
        }

        val preferences = AppPreferences(appContext)
        if (!preferences.isProtectionActive()) {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
            pendingResult.finish()
            return
        }

        val database = AppDatabase.getDatabase(appContext)
        val repository = SosRepository(database, preferences)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                BackgroundMonitorScheduler.ensureMonitoringArmed(appContext)

                val processed = CallLogReconciler.reconcileMissedCalls(appContext, repository)
                if (processed > 0) {
                    BackgroundMonitorScheduler.cancelRingFollowUpChecks(appContext)
                } else if (stepIndex >= 2) {
                    // On late follow-up steps (40s/65s), if CallLog wasn't accessible and IDLE broadcast was dropped,
                    // reconcile any pending un-answered ring safely via CallStateTracker fallback.
                    CallStateTracker.reconcilePendingRingIfStale(appContext, repository)
                }

                if (SosAlarmManager.isAlarmActive) {
                    delay(1500L)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RECONCILE_CALLS = "com.example.action.RECONCILE_MISSED_CALLS"
        const val EXTRA_STEP_INDEX = "extra_step_index"
    }
}
