package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.alarm.SosAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class AlarmUiState(
    val isActive: Boolean = false,
    val callerName: String = "Emergency Contact",
    val callerNumber: String = "",
    val missedCount: Int = 5
)

object SosAlarmManager {
    const val CHANNEL_ID_ALARM = "sos_emergency_alarm_v9"
    const val CHANNEL_ID_PROTECTION = "sos_protection_channel"
    const val NOTIFICATION_ID_ALARM = 9001
    const val NOTIFICATION_ID_PROTECTION = 9002

    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var sirenJob: Job? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var previousAlarmVolume: Int = -1
    private var previousMusicVolume: Int = -1
    private var previousRingVolume: Int = -1
    private var strobeJob: Job? = null
    private var audioFocusRequest: Any? = null
    private val alarmScope = CoroutineScope(Dispatchers.Default)

    private val _alarmStateFlow = MutableStateFlow(AlarmUiState())
    val alarmStateFlow: StateFlow<AlarmUiState> = _alarmStateFlow.asStateFlow()

    @Volatile var isAlarmActive: Boolean = false
        private set
    @Volatile var activeCallerName: String? = null
        private set
    @Volatile var activeCallerNumber: String = ""
        private set
    @Volatile var activeMissedCount: Int = 0
        private set

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                // Remove any legacy persistent protection notification and old channels
                nm.cancel(NOTIFICATION_ID_PROTECTION)
                nm.deleteNotificationChannel(CHANNEL_ID_PROTECTION)
                nm.deleteNotificationChannel("sos_emergency_alarm_v8")
                nm.deleteNotificationChannel("sos_emergency_alarm_v7")
                nm.deleteNotificationChannel("sos_loud_alarm_channel_v4")
                nm.deleteNotificationChannel("sos_loud_alarm_channel_v3")
                nm.deleteNotificationChannel("sos_loud_alarm_channel_v2")
            } catch (_: Exception) {}

            // High-importance alarm channel that overrides DND and enables lockscreen full-screen intents
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "Emergency SOS Alarm Screen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS alarm and full-screen lockscreen alert for repeated missed calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.RED
                // Sound is played directly by SosAlarmManager (MediaPlayer + AudioTrack siren)
                setSound(null, null)
            }
            nm.createNotificationChannel(alarmChannel)
        }
    }

    /**
     * Acquires PARTIAL_WAKE_LOCK so CPU, AudioTrack, MediaPlayer, Vibrator, and Flashlight
     * stay running at 100% speed even when the screen is off and app is not in Recents,
     * WITHOUT prematurely setting PowerManager.isInteractive = true before fullScreenIntent fires.
     */
    fun acquireCpuWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (cpuWakeLock?.isHeld != true) {
                cpuWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MissedCallSOS:AlarmCpuWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 1000L) // 10 min max timeout
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Physically turns the display ON and wakes the screen over the lockscreen.
     * Call this AFTER posting the fullScreenIntent notification so Android's NotificationManagerService
     * sees the screen as non-interactive when launching SosAlarmActivity.
     */
    fun wakeUpScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (screenWakeLock?.isHeld == true) {
                try { screenWakeLock?.release() } catch (_: Exception) {}
            }
            @Suppress("DEPRECATION")
            screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "MissedCallSOS:AlarmScreenWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    fun startSosAlarm(
        context: Context,
        callerName: String?,
        callerNumber: String,
        missedCount: Int,
        tone: String = "system_alarm",
        enableFlashlight: Boolean = true,
        enableVibration: Boolean = true,
        wakeScreenImmediately: Boolean = false
    ) {
        val appContext = context.applicationContext
        val resolvedName = callerName?.takeIf { it.isNotBlank() } ?: callerNumber
        val alreadyPlaying = isAlarmActive && (mediaPlayer?.isPlaying == true || sirenJob?.isActive == true)
        isAlarmActive = true
        activeCallerName = resolvedName
        activeCallerNumber = callerNumber
        activeMissedCount = missedCount
        _alarmStateFlow.value = AlarmUiState(
            isActive = true,
            callerName = resolvedName,
            callerNumber = callerNumber,
            missedCount = missedCount
        )

        // Always hold CPU wake lock immediately so background audio never pauses
        acquireCpuWakeLock(appContext)
        if (wakeScreenImmediately) {
            wakeUpScreen(appContext)
        }

        // Show the System Overlay Window if SYSTEM_ALERT_WINDOW is granted
        SosAlarmOverlayManager.show(
            context = appContext,
            callerName = resolvedName,
            callerNumber = callerNumber,
            missedCount = missedCount
        )

        if (alreadyPlaying) {
            return
        }

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1. Force maximum volume across ALARM, MUSIC, and RING streams for extreme loudness
        try {
            if (previousAlarmVolume == -1) {
                previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            if (previousMusicVolume == -1) {
                previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            if (previousRingVolume == -1) {
                previousRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            }

            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)

            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
        } catch (_: Exception) {
            // Ignore volume set failures
        }

        // 2. Request audio focus with ducking allowed so incoming phone calls are NEVER blocked
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            try {
                                mediaPlayer?.setVolume(0.1f, 0.1f)
                            } catch (_: Exception) {}
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            try {
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                            } catch (_: Exception) {}
                        }
                    }
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (_: Exception) {
            // Ignore audio focus error
        }

        // 3. Play dual sound: MediaPlayer system alarm + High-pitch AudioTrack Emergency Siren
        stopAudio()
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(appContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(appContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                    setVolume(1.0f, 1.0f)
                    isLooping = true
                    start()
                }
            } catch (_: Exception) {
                // Fallback handled by synthesized siren
            }
        }

        // 4. Start synthesized high-urgency siren via AudioTrack at maximum physical SPL
        startExtremeAudioSiren()

        // 5. Vibration pattern
        if (enableVibration) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val pattern = longArrayOf(0, 600, 200, 600, 200, 800)
                val vibAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), vibAttrs)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (_: Exception) {
                // Ignore vibration failure
            }
        }

        // 6. Flashlight strobe
        if (enableFlashlight) {
            startFlashlightStrobe(appContext)
        }
    }

    private fun startExtremeAudioSiren() {
        try {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate)

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_ALARM,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2,
                    AudioTrack.MODE_STREAM
                )
            }

            track.setVolume(1.0f)
            track.play()
            audioTrack = track

            sirenJob?.cancel()
            sirenJob = alarmScope.launch {
                val chunkSize = 2205 // 50ms chunks
                val chunk = ShortArray(chunkSize)
                var phase = 0.0
                var step = 0

                while (isActive) {
                    val freq = 1200.0 + (1400.0 * ((step % 20) / 20.0))
                    val phaseInc = (2.0 * PI * freq) / sampleRate

                    for (i in 0 until chunkSize) {
                        chunk[i] = (sin(phase) * 32000.0).toInt().toShort()
                        phase += phaseInc
                        if (phase > 2.0 * PI) phase -= 2.0 * PI
                    }

                    track.write(chunk, 0, chunkSize)
                    step++
                }
            }
        } catch (_: Exception) {
            // AudioTrack fallback
        }
    }

    private fun startFlashlightStrobe(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        strobeJob?.cancel()
        strobeJob = alarmScope.launch {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@launch

                var torchOn = false
                while (isActive) {
                    torchOn = !torchOn
                    try {
                        cameraManager.setTorchMode(cameraId, torchOn)
                    } catch (_: CameraAccessException) {
                        break
                    }
                    delay(150)
                }
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    fun stopSosAlarm(context: Context) {
        stopAudio()
        strobeJob?.cancel()
        strobeJob = null

        // Dismiss system overlay window if showing
        SosAlarmOverlayManager.dismiss()

        // Cancel alarm and any legacy protection notifications
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_ALARM)
            nm?.cancel(NOTIFICATION_ID_PROTECTION)
        } catch (_: Exception) {}

        // Turn off torch
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {}

        // Stop vibration
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        // Release wake locks
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
            cpuWakeLock = null
        } catch (_: Exception) {}

        try {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
            screenWakeLock = null
        } catch (_: Exception) {}

        // Abandon audio focus and restore volumes
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest is AudioFocusRequest) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest as AudioFocusRequest)
            }
            if (previousAlarmVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
                previousAlarmVolume = -1
            }
            if (previousMusicVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousMusicVolume, 0)
                previousMusicVolume = -1
            }
            if (previousRingVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, previousRingVolume, 0)
                previousRingVolume = -1
            }
        } catch (_: Exception) {}

        isAlarmActive = false
        activeCallerName = null
        activeCallerNumber = ""
        activeMissedCount = 0
        _alarmStateFlow.value = AlarmUiState(isActive = false)
    }

    private fun stopAudio() {
        try {
            sirenJob?.cancel()
            sirenJob = null
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    fun buildAlarmNotification(
        context: Context,
        callerName: String?,
        callerNumber: String,
        missedCount: Int
    ): Notification {
        createNotificationChannels(context)
        val title = context.getString(R.string.alarm_title)
        val displayName = callerName?.takeIf { it.isNotBlank() } ?: callerNumber
        val content = context.getString(R.string.alarm_caller_count, "$missedCount ($displayName)")

        val fullScreenIntent = Intent(context, SosAlarmActivity::class.java).apply {
            setAction("com.example.action.SHOW_SOS_ALARM")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(SosAlarmActivity.EXTRA_CALLER_NAME, displayName)
            putExtra(SosAlarmActivity.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(SosAlarmActivity.EXTRA_MISSED_COUNT, missedCount)
        }

        val creatorOptionsBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                android.app.ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            creatorOptionsBundle
        )

        val stopIntent = Intent(context, com.example.service.SosAlarmService::class.java).apply {
            action = com.example.service.SosAlarmService.ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "STOP ALARM", stopPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "OPEN SCREEN", fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
}
