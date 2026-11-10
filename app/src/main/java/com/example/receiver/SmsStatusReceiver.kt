package com.example.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val outboxId = intent.getLongExtra(EXTRA_OUTBOX_ID, -1L)
        if (outboxId == -1L) return

        val database = AppDatabase.getDatabase(context.applicationContext)
        val preferences = AppPreferences(context.applicationContext)
        val repository = SosRepository(database, preferences)

        val resultCode = resultCode
        val isSuccess = resultCode == Activity.RESULT_OK
        val action = intent.action

        CoroutineScope(Dispatchers.IO).launch {
            if (action == ACTION_SMS_SENT) {
                if (isSuccess) {
                    repository.updateOutboxSmsStatus(outboxId, "SENT")
                } else {
                    val errorMsg = when (resultCode) {
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic SMS failure"
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off / Airplane mode"
                        else -> "SMS Error code $resultCode"
                    }
                    repository.updateOutboxSmsStatus(outboxId, "FAILED", errorMsg)
                }
            } else if (action == ACTION_SMS_DELIVERED) {
                if (isSuccess) {
                    repository.updateOutboxSmsStatus(outboxId, "DELIVERED")
                }
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.example.receiver.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.receiver.ACTION_SMS_DELIVERED"
        const val EXTRA_OUTBOX_ID = "extra_outbox_id"
    }
}
