package com.example.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import com.example.telephony.CallLogReconciler
import com.example.telephony.CallStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lightweight, zero-notification, event-driven call observer service.
 * Does NOT show any persistent foreground notification and does NOT run any background polling loop,
 * ensuring 0% idle battery drain and no stuck notifications when swiped from Recents.
 * When the app is not in Recents, the manifest PhoneStateReceiver wakes up automatically on incoming calls.
 */
class CallDetectionService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var repository: SosRepository

    private var telephonyCallback: Any? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    private var callLogObserver: ContentObserver? = null

    private val dynamicCallReceiver = object : BroadcastReceiver() {
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

                CallStateTracker.onCallStateChanged(
                    context = applicationContext,
                    repository = repository,
                    state = state,
                    incomingNumber = incomingNumber,
                    simSlot = simSlot
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SosAlarmManager.createNotificationChannels(this)
        // Ensure any old persistent protection notification is removed immediately
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)
        } catch (_: Exception) {}

        val database = AppDatabase.getDatabase(this)
        val preferences = AppPreferences(this)
        repository = SosRepository(database, preferences)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (preferences.getLastCallLogCheckTimestamp() == 0L) {
            preferences.setLastCallLogCheckTimestamp(System.currentTimeMillis() - 5_000L)
        }

        registerTelephonyListener()
        registerCallLogObserver()

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            priority = 1000
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dynamicCallReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(dynamicCallReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)
        } catch (_: Exception) {}

        registerTelephonyListener()
        registerCallLogObserver()
        serviceScope.launch {
            try {
                CallLogReconciler.reconcileMissedCalls(applicationContext, repository)
            } catch (_: Exception) {}
        }
        return START_STICKY
    }

    private fun registerTelephonyListener() {
        val hasPhonePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhonePermission) return

        if (phoneStateListener == null) {
            try {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        CallStateTracker.onCallStateChanged(
                            context = applicationContext,
                            repository = repository,
                            state = state,
                            incomingNumber = phoneNumber
                        )
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback == null) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    CallStateTracker.onCallStateChanged(
                        context = applicationContext,
                        repository = repository,
                        state = state,
                        incomingNumber = null
                    )
                }
            }
            try {
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    telephonyManager.unregisterTelephonyCallback(it)
                } catch (_: Exception) {}
            }
        }
        @Suppress("DEPRECATION")
        phoneStateListener?.let {
            try {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            } catch (_: Exception) {}
        }
        try {
            unregisterReceiver(dynamicCallReceiver)
        } catch (_: Exception) {}
        callLogObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
        }
    }

    private fun registerCallLogObserver() {
        if (callLogObserver != null) return

        val hasCallLogPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallLogPermission) {
            return
        }

        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    serviceScope.launch {
                        try {
                            delay(350L)
                            CallLogReconciler.reconcileMissedCalls(applicationContext, repository)
                            delay(1000L)
                            CallLogReconciler.reconcileMissedCalls(applicationContext, repository)
                        } catch (_: Exception) {}
                    }
                }
            }
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            callLogObserver = observer
        } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure no notification remains when user swipes the app away from Recents
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                // Cancel any legacy protection notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)

                val intent = Intent(context, CallDetectionService::class.java)
                context.startService(intent)
            } catch (_: Exception) {
                // When in background on Android 8+, PhoneStateReceiver handles all calls directly with 0 battery drain
            }
        }

        fun stop(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(SosAlarmManager.NOTIFICATION_ID_PROTECTION)

                val intent = Intent(context, CallDetectionService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
