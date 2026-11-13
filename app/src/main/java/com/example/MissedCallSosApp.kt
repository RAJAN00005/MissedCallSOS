package com.example

import android.app.Application
import com.example.alarm.SosAlarmManager
import com.example.data.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import com.example.service.CallDetectionService
import kotlinx.coroutines.launch

class MissedCallSosApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var repository: SosRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        SosAlarmManager.createNotificationChannels(this)
        database = AppDatabase.getDatabase(this)
        preferences = AppPreferences(this)
        repository = SosRepository(database, preferences)

        if (preferences.isOnboardingCompleted() && preferences.isProtectionActive()) {
            CallDetectionService.start(this)
            com.example.telephony.BackgroundMonitorScheduler.ensureMonitoringArmed(this)
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.purgeLegacySeededContacts()
        }
    }

    companion object {
        lateinit var instance: MissedCallSosApp
            private set
    }
}
