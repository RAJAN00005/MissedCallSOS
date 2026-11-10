# MissedCall SOS

**MissedCall SOS** is a production-grade native Android safety application built with Kotlin and Jetpack Compose. It ensures that urgent, repeated calls from trusted family and emergency contacts are never ignored—even when the phone is on silent, vibrate, or Do Not Disturb (DND).

---

## Core Features

1. **Repeated Missed Call -> Loud SOS Alarm**:
   - Bypasses silent, vibrate, and DND modes via `AudioAttributes.USAGE_ALARM` and notification policy bypass.
   - Max volume override on `STREAM_ALARM`, restoring previous volume upon dismissal.
   - Full-screen lock-screen alert activity (`setShowWhenLocked`, `setTurnScreenOn`, `FLAG_KEEP_SCREEN_ON`).
   - Strobe camera flash and intense vibration pattern.
   - Accidental-touch-proof Slide to Stop with optional 4-digit PIN protection.

2. **Repeated Missed Call -> Automated Location via Offline SMS**:
   - Requests high-accuracy GPS fix with 20s timeout and multi-stage fallback (Fused -> raw GPS -> Network -> Last Known).
   - Automatically drafts and sends an emergency SMS to the caller via `SmsManager` without requiring internet connectivity.
   - GSM-7 encoded text under 160 characters containing Google Maps link, raw coordinates, timestamp, and battery percentage.
   - Room Outbox table backed by WorkManager with exponential backoff retries.
   - Dual-SIM support: user selects which SIM dispatches emergency SMS.

3. **User-Configurable Trigger Rules**:
   - Threshold $N$ (2 to 20 calls, default 5).
   - Sliding time window $T$ (10, 30, 60, 120 minutes, or 24h, default 30 min).
   - Cooldown period (default 15 minutes).
   - Per-contact overrides (Alarm Toggle, Location Toggle, Custom Threshold).
   - Quick groups: "Family", "Emergency", "Custom".

4. **Background Reliability Engine**:
   - Persistent Foreground Service (`CallDetectionService`) with `specialUse` foreground service type.
   - `TelephonyCallback` (API 31+) / `PhoneStateListener` (< 31) + manifest `PhoneStateReceiver`.
   - `CallScreeningService` for Android 10+ role integration.
   - `CallLogReconciler` scanning missed calls on boot and app start.
   - Automatic restart on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
   - OEM-specific auto-start guides (Xiaomi, Samsung, Oppo, Realme, Vivo, OnePlus).

5. **Liquid Glass Design System**:
   - Translucent glass panels with specular gradient borders, soft shadows, and drifting mesh gradients.
   - Capability tiers auto-detected: **Tier A** (full effects), **Tier B** (balanced), and **Tier C** (low-cost tinted frosted glass).
   - WCAG AAA high-contrast Lockscreen Alarm UI.
   - Bilingual support: **English** and **Hindi (हिन्दी)**.

---

## Google Play Store Permission Declaration Draft

### Sensitive Permissions Requested:
- `android.permission.READ_CALL_LOG`
- `android.permission.READ_PHONE_STATE`
- `android.permission.SEND_SMS`

### Justification Statement for Google Play Console Review:
> **Core App Functionality: Emergency Safety & Protection against Unanswered Repeated Calls**
>
> **1. READ_CALL_LOG & READ_PHONE_STATE**:
> MissedCall SOS is a personal safety application designed specifically to alert sleeping, disabled, or imperiled users when a family member or emergency contact calls repeatedly.
> The app must detect repeated missed calls in real-time. `READ_PHONE_STATE` is necessary to track the call state cycle (`RINGING -> IDLE` without `OFFHOOK`). `READ_CALL_LOG` is essential to verify genuine missed calls and to reconcile calls that occurred while the phone was rebooting or when the app was temporarily stopped by the operating system. No call log data ever leaves the local device or is transmitted to any third-party server.
>
> **2. SEND_SMS**:
> In life-threatening emergencies, internet connectivity (cellular data / Wi-Fi) is frequently unavailable or turned off to save battery. The core safety promise of MissedCall SOS is the ability to send the user's GPS coordinates to designated emergency contacts completely offline via SMS. An internet-only channel would leave the user unprotected in dead zones. Location is dispatched solely to contacts explicitly designated by the user.

---

## Known Limitations

1. **Powered-Off Devices**: No Android application can detect calls or play audio when the device is completely powered down.
2. **Zero Cellular Signal**: If the device has no cellular reception, incoming calls cannot reach the phone at the carrier level, and SMS cannot be dispatched until signal is restored.
3. **iOS Platform**: iOS does not offer public APIs for background telephony event inspection or automated SMS sending. MissedCall SOS is designed specifically for native Android.

---

## Architecture

```
com.example/
├── MissedCallSosApp.kt            # Application init, channels, DI
├── MainActivity.kt                # Compose Navigation & Glass Host
├── alarm/
│   └── SosAlarmManager.kt         # USAGE_ALARM, max volume, strobe, vibration
├── data/
│   ├── AppDatabase.kt             # Room DB (version 1)
│   ├── dao/                       # CallEventDao, ContactRuleDao, OutboxSmsDao, TriggerHistoryDao
│   ├── entity/                    # Entity models
│   ├── preferences/AppPreferences # Shared preferences & Flow state
│   └── repository/SosRepository   # Unified data layer & device contacts query
├── location/
│   └── LocationEngine.kt          # FusedLocation with GPS & Network fallbacks
├── receiver/                      # BootReceiver, PhoneStateReceiver, SmsStatusReceiver
├── service/
│   ├── CallDetectionService.kt    # Sticky foreground protection service
│   ├── CallScreeningServiceImpl.kt# Android 10+ CallScreeningService
│   └── SosAlarmService.kt         # Alarm playback & lockscreen launcher service
├── sms/
│   ├── SmsDispatcher.kt           # GSM-7 formatter, dual-SIM dispatch
│   └── OutboxWorker.kt            # WorkManager exponential retry worker
├── telephony/
│   ├── CallLogReconciler.kt       # Offline missed-call recovery
│   ├── CallStateTracker.kt        # State machine & sliding window evaluator
│   └── PhoneNumberHelper.kt       # E.164 normalization via libphonenumber
└── ui/
    ├── alarm/                     # SosAlarmActivity & SosAlarmContent
    ├── components/                # GlassBackground, GlassCard, GlassButton, StatusOrb, etc.
    ├── screens/                   # Onboarding, Home, Rules, History, Health, Test, Gallery
    └── theme/                     # GlassTheme, DesignTokens, Color, Type
```
