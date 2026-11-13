package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("missed_call_sos_prefs", Context.MODE_PRIVATE)

    private val _thresholdFlow = MutableStateFlow(getGlobalThreshold())
    val thresholdFlow: StateFlow<Int> = _thresholdFlow.asStateFlow()

    private val _windowMinutesFlow = MutableStateFlow(getGlobalWindowMinutes())
    val windowMinutesFlow: StateFlow<Int> = _windowMinutesFlow.asStateFlow()

    private val _cooldownMinutesFlow = MutableStateFlow(getCooldownMinutes())
    val cooldownMinutesFlow: StateFlow<Int> = _cooldownMinutesFlow.asStateFlow()

    private val _pausedUntilFlow = MutableStateFlow(getProtectionPausedUntil())
    val pausedUntilFlow: StateFlow<Long> = _pausedUntilFlow.asStateFlow()

    private val _visualQualityFlow = MutableStateFlow(getVisualQuality())
    val visualQualityFlow: StateFlow<String> = _visualQualityFlow.asStateFlow()

    private val _reduceTransparencyFlow = MutableStateFlow(isReduceTransparency())
    val reduceTransparencyFlow: StateFlow<Boolean> = _reduceTransparencyFlow.asStateFlow()

    private val _reduceMotionFlow = MutableStateFlow(isReduceMotion())
    val reduceMotionFlow: StateFlow<Boolean> = _reduceMotionFlow.asStateFlow()

    private val _batterySaverFlow = MutableStateFlow(isBatterySaverMode())
    val batterySaverFlow: StateFlow<Boolean> = _batterySaverFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(getLanguage())
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    fun getGlobalThreshold(): Int = prefs.getInt(KEY_THRESHOLD, 5).coerceIn(2, 20)
    fun setGlobalThreshold(value: Int) {
        prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(2, 20)).apply()
        _thresholdFlow.value = value
    }

    fun getGlobalWindowMinutes(): Int = prefs.getInt(KEY_WINDOW_MINUTES, 30)
    fun setGlobalWindowMinutes(value: Int) {
        prefs.edit().putInt(KEY_WINDOW_MINUTES, value).apply()
        _windowMinutesFlow.value = value
    }

    fun getCooldownMinutes(): Int = prefs.getInt(KEY_COOLDOWN_MINUTES, 15)
    fun setCooldownMinutes(value: Int) {
        prefs.edit().putInt(KEY_COOLDOWN_MINUTES, value).apply()
        _cooldownMinutesFlow.value = value
    }

    fun getAlarmTone(): String = prefs.getString(KEY_ALARM_TONE, "system_alarm") ?: "system_alarm"
    fun setAlarmTone(tone: String) {
        prefs.edit().putString(KEY_ALARM_TONE, tone).apply()
    }

    fun getMaxAlarmDurationMinutes(): Int = prefs.getInt(KEY_MAX_DURATION_MINUTES, 3)
    fun setMaxAlarmDurationMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_MAX_DURATION_MINUTES, minutes).apply()
    }

    fun isFlashlightEnabled(): Boolean = prefs.getBoolean(KEY_FLASHLIGHT, true)
    fun setFlashlightEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLASHLIGHT, enabled).apply()
    }

    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION, true)
    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun getSimSlotForSms(): Int = prefs.getInt(KEY_SIM_SLOT, -1)
    fun setSimSlotForSms(slot: Int) {
        prefs.edit().putInt(KEY_SIM_SLOT, slot).apply()
    }

    fun isPinRequired(): Boolean = prefs.getBoolean(KEY_PIN_REQUIRED, false)
    fun setPinRequired(required: Boolean) {
        prefs.edit().putBoolean(KEY_PIN_REQUIRED, required).apply()
    }

    fun getAlarmPin(): String = prefs.getString(KEY_ALARM_PIN, "1234") ?: "1234"
    fun setAlarmPin(pin: String) {
        prefs.edit().putString(KEY_ALARM_PIN, pin).apply()
    }

    fun getProtectionPausedUntil(): Long = prefs.getLong(KEY_PAUSED_UNTIL, 0L)
    fun setProtectionPausedUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_PAUSED_UNTIL, timestamp).apply()
        _pausedUntilFlow.value = timestamp
    }

    fun isProtectionActive(): Boolean {
        val pausedUntil = getProtectionPausedUntil()
        return pausedUntil == 0L || pausedUntil <= System.currentTimeMillis()
    }

    fun getVisualQuality(): String = prefs.getString(KEY_VISUAL_QUALITY, "auto") ?: "auto"
    fun setVisualQuality(quality: String) {
        prefs.edit().putString(KEY_VISUAL_QUALITY, quality).apply()
        _visualQualityFlow.value = quality
    }

    fun isReduceTransparency(): Boolean = prefs.getBoolean(KEY_REDUCE_TRANSPARENCY, false)
    fun setReduceTransparency(reduce: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_TRANSPARENCY, reduce).apply()
        _reduceTransparencyFlow.value = reduce
    }

    fun isReduceMotion(): Boolean = prefs.getBoolean(KEY_REDUCE_MOTION, false)
    fun setReduceMotion(reduce: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, reduce).apply()
        _reduceMotionFlow.value = reduce
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _languageFlow.value = lang
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isBatterySaverMode(): Boolean = prefs.getBoolean(KEY_BATTERY_SAVER, false)
    fun setBatterySaverMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_SAVER, enabled).apply()
        _batterySaverFlow.value = enabled
    }

    fun getLastCallLogCheckTimestamp(): Long = prefs.getLong(KEY_LAST_CALLLOG_CHECK, 0L)
    fun setLastCallLogCheckTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_CALLLOG_CHECK, ts).commit()
    }

    fun getLastRingingTimestamp(): Long = prefs.getLong(KEY_LAST_RINGING_TIME, 0L)
    fun setLastRingingTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_RINGING_TIME, ts).commit()
    }

    fun getLastRingingNumber(): String? = prefs.getString(KEY_LAST_RINGING_NUMBER, null)
    fun setLastRingingNumber(num: String?) {
        prefs.edit().putString(KEY_LAST_RINGING_NUMBER, num).commit()
    }

    fun getLastOffhookTimestamp(): Long = prefs.getLong(KEY_LAST_OFFHOOK_TIME, 0L)
    fun setLastOffhookTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_OFFHOOK_TIME, ts).commit()
    }

    companion object {
        private const val KEY_THRESHOLD = "key_threshold"
        private const val KEY_WINDOW_MINUTES = "key_window_minutes"
        private const val KEY_COOLDOWN_MINUTES = "key_cooldown_minutes"
        private const val KEY_ALARM_TONE = "key_alarm_tone"
        private const val KEY_MAX_DURATION_MINUTES = "key_max_duration_minutes"
        private const val KEY_FLASHLIGHT = "key_flashlight"
        private const val KEY_VIBRATION = "key_vibration"
        private const val KEY_SIM_SLOT = "key_sim_slot"
        private const val KEY_PIN_REQUIRED = "key_pin_required"
        private const val KEY_ALARM_PIN = "key_alarm_pin"
        private const val KEY_PAUSED_UNTIL = "key_paused_until"
        private const val KEY_VISUAL_QUALITY = "key_visual_quality"
        private const val KEY_REDUCE_TRANSPARENCY = "key_reduce_transparency"
        private const val KEY_REDUCE_MOTION = "key_reduce_motion"
        private const val KEY_BATTERY_SAVER = "key_battery_saver"
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_LAST_CALLLOG_CHECK = "key_last_calllog_check"
        private const val KEY_LAST_RINGING_TIME = "key_last_ringing_time"
        private const val KEY_LAST_RINGING_NUMBER = "key_last_ringing_number"
        private const val KEY_LAST_OFFHOOK_TIME = "key_last_offhook_time"
    }
}
