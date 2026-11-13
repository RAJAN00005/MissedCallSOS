package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import com.example.service.CallDetectionService
import com.example.sms.SmsDispatcher
import com.example.telephony.CallLogReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val appContext = context.applicationContext
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)
            } catch (_: Exception) {}

            val database = AppDatabase.getDatabase(appContext)
            val preferences = AppPreferences(appContext)
            val repository = SosRepository(database, preferences)

            // Start zero-notification observer & OS JobScheduler / WorkManager watchers
            CallDetectionService.start(appContext)
            com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(appContext, forceRescheduleJob = true)

            // Schedule WorkManager outbox retry
            SmsDispatcher.scheduleOutboxWorker(appContext)

            // Reconcile any missed calls that happened while phone was restarting
            CoroutineScope(Dispatchers.IO).launch {
                CallLogReconciler.reconcileMissedCalls(appContext, repository)
            }
        }
    }
}
