package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.telephony.TelephonyManager
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import com.example.telephony.CallStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            val simSlot = intent.getIntExtra("simSlot", -1)

            val state = when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                else -> TelephonyManager.CALL_STATE_IDLE
            }

            val pendingResult = goAsync()
            val appContext = context.applicationContext

            // Ensure no legacy persistent protection notification is shown
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)
            } catch (_: Exception) {}

            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MissedCallSOS:PhoneStateReceiverWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(20_000L) // 20 seconds max timeout
            }

            val preferences = AppPreferences(appContext)
            com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(appContext)
            val database = AppDatabase.getDatabase(appContext)
            val repository = SosRepository(database, preferences)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CallStateTracker.handlePhoneStateBroadcast(
                        context = appContext,
                        repository = repository,
                        state = state,
                        incomingNumber = incomingNumber,
                        simSlot = simSlot
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PhoneStateReceiver", "Error handling call state: ${e.message}", e)
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) {
                            wakeLock.release()
                        }
                    } catch (_: Exception) {}
                    pendingResult.finish()
                }
            }
        }
    }
}
