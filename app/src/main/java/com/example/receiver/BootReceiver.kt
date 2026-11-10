package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val database = AppDatabase.getDatabase(context.applicationContext)
            val preferences = AppPreferences(context.applicationContext)
            val repository = SosRepository(database, preferences)

            // Re-start call detection foreground service
            CallDetectionService.start(context.applicationContext)

            // Schedule WorkManager outbox retry
            SmsDispatcher.scheduleOutboxWorker(context.applicationContext)

            // Reconcile any missed calls that happened while phone was off/restarting
            CoroutineScope(Dispatchers.IO).launch {
                CallLogReconciler.reconcileMissedCalls(context.applicationContext, repository)
            }
        }
    }
}
