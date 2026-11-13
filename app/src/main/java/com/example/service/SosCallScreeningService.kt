package com.example.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.data.preferences.AppPreferences
import com.example.telephony.BackgroundMonitorScheduler
import com.example.telephony.CallStateTracker

/**
 * Android Telecom CallScreeningService bound directly by system_server on every incoming call.
 * Works reliably even when the app has been swiped from Recents, without any foreground notification
 * or idle battery drain, and supplies the exact caller number on Android 10+.
 */
class SosCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Always let the call through untouched with zero delay
        val responseBuilder = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            responseBuilder.setSilenceCall(false)
        }
        respondToCall(callDetails, responseBuilder.build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
                return
            }
        }

        val appContext = applicationContext
        val rawNumber = callDetails.handle?.schemeSpecificPart
        val now = System.currentTimeMillis()

        val preferences = AppPreferences(appContext)
        preferences.setLastRingingTimestamp(now)
        if (!rawNumber.isNullOrBlank() && rawNumber != "UNKNOWN") {
            preferences.setLastRingingNumber(rawNumber)
        }

        CallStateTracker.recordExternalRinging(now, rawNumber)
        if (preferences.isProtectionActive()) {
            BackgroundMonitorScheduler.ensureMonitoringArmed(appContext)
            BackgroundMonitorScheduler.scheduleRingFollowUpChecks(appContext)
        }
    }
}
