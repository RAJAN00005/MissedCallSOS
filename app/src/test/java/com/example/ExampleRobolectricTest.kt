package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.SosAlarmManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MissedCall SOS", appName)
    }

    @Test
    fun `alarm state flow updates on start and stop alarm`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        SosAlarmManager.stopSosAlarm(context)
        assertFalse(SosAlarmManager.alarmStateFlow.value.isActive)

        SosAlarmManager.startSosAlarm(
            context = context,
            callerName = "Trusted Family",
            callerNumber = "9876543210",
            missedCount = 4,
            enableFlashlight = false,
            enableVibration = false
        )

        val activeState = SosAlarmManager.alarmStateFlow.value
        assertTrue(activeState.isActive)
        assertEquals("Trusted Family", activeState.callerName)
        assertEquals(4, activeState.missedCount)

        SosAlarmManager.stopSosAlarm(context)
        assertFalse(SosAlarmManager.alarmStateFlow.value.isActive)
    }
}
