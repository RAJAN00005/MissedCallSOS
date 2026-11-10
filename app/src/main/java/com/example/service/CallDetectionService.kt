package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.PowerManager
import com.example.MainActivity
import com.example.R
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        val database = AppDatabase.getDatabase(this)
        val preferences = AppPreferences(this)
        repository = SosRepository(database, preferences)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Ensure last call log check timestamp is initialized to NOW on startup,
        // so that past missed calls from before the app opened NEVER trigger alarms!
        if (preferences.getLastCallLogCheckTimestamp() == 0L) {
            preferences.setLastCallLogCheckTimestamp(System.currentTimeMillis())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SosAlarmManager.NOTIFICATION_ID_PROTECTION,
                createProtectionNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SosAlarmManager.NOTIFICATION_ID_PROTECTION,
                createProtectionNotification(),
                0
            )
        } else {
            startForeground(SosAlarmManager.NOTIFICATION_ID_PROTECTION, createProtectionNotification())
        }
        registerTelephonyListener()
        registerCallLogObserver()

        // Register high-priority dynamic broadcast receiver in the foreground service (unthrottled by MIUI)
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
        registerTelephonyListener()
        registerCallLogObserver()
        return START_STICKY
    }

    private fun registerTelephonyListener() {
        val hasPhonePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhonePermission) return

        // 1. Register PhoneStateListener (supplies phoneNumber on Android 8 through 12!)
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

        // 2. On Android 12+, also register TelephonyCallback as secondary state listener
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

    private fun createProtectionNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SosAlarmManager.CHANNEL_ID_PROTECTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.status_active))
            .setContentText(getString(R.string.status_desc_active))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
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
                            delay(500)
                            val minDate = System.currentTimeMillis() - 180_000L
                            val latest = CallLogReconciler.getLatestUnprocessedMissedCall(
                                context = applicationContext,
                                repository = repository,
                                minDate = minDate
                            )
                            if (latest != null) {
                                CallStateTracker.processMissedCallInternal(
                                    context = applicationContext,
                                    repository = repository,
                                    rawNumber = latest.first,
                                    timestamp = latest.second,
                                    simSlot = -1
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CallDetectionService", "Error in CallLog observer: ${e.message}")
                        }
                    }
                }
            }
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            callLogObserver = observer
        } catch (_: SecurityException) {
            // Permission not granted or revoked
        } catch (e: Exception) {
            android.util.Log.e("CallDetectionService", "Failed to register CallLog observer: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (::repository.isInitialized && repository.preferences.isProtectionActive()) {
            val restartIntent = Intent(applicationContext, CallDetectionService::class.java)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    1002,
                    restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    1002,
                    restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000L,
                pendingIntent
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CallDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallDetectionService::class.java)
            context.stopService(intent)
        }
    }
}
