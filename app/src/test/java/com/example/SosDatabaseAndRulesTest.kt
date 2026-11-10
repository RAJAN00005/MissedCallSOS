package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.CallEventEntity
import com.example.data.entity.ContactRuleEntity
import com.example.data.entity.OutboxSmsEntity
import com.example.data.preferences.AppPreferences
import com.example.data.repository.SosRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SosDatabaseAndRulesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var repository: SosRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences(context)
        repository = SosRepository(db, preferences)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFreshDatabaseHasZeroContacts() = runBlocking {
        // Requirement 1 & Step 5a: Fresh install / fresh DB -> contacts count == 0
        val rules = repository.allRulesFlow.first()
        assertEquals(0, rules.size)

        val alarmRules = repository.alarmRulesFlow.first()
        assertEquals(0, alarmRules.size)
    }

    @Test
    fun testLegacySeededDataPurgedWithoutTouchingRealUserContact() = runBlocking {
        // Step 5b: Migration / Purge test:
        // Simulate existing database that had the 3 legacy seeded rows + 1 real user contact
        val legacy1 = ContactRuleEntity(
            displayName = "Mom",
            rawNumber = "+919876543210",
            normalizedNumber = "+919876543210",
            customThreshold = 5,
            isAlarmEnabled = true,
            isLocationEnabled = true
        )
        val legacy2 = ContactRuleEntity(
            displayName = "Dad",
            rawNumber = "+919876543211",
            normalizedNumber = "+919876543211",
            customThreshold = 5,
            isAlarmEnabled = true,
            isLocationEnabled = true
        )
        val legacy3 = ContactRuleEntity(
            displayName = "Emergency Helpline",
            rawNumber = "112",
            normalizedNumber = "112",
            customThreshold = 5,
            isAlarmEnabled = true,
            isLocationEnabled = true
        )
        val realUserContact = ContactRuleEntity(
            displayName = "Alice Real Friend",
            rawNumber = "+15551234567",
            normalizedNumber = "+15551234567",
            customThreshold = 3,
            isAlarmEnabled = true,
            isLocationEnabled = true
        )

        db.contactRuleDao().insertOrUpdate(legacy1)
        db.contactRuleDao().insertOrUpdate(legacy2)
        db.contactRuleDao().insertOrUpdate(legacy3)
        db.contactRuleDao().insertOrUpdate(realUserContact)

        // Seed call events for legacy & user contact
        db.callEventDao().insertCallEvent(
            CallEventEntity(
                rawNumber = "9876543210",
                normalizedNumber = "+919876543210",
                timestamp = System.currentTimeMillis()
            )
        )
        db.callEventDao().insertCallEvent(
            CallEventEntity(
                rawNumber = "+15551234567",
                normalizedNumber = "+15551234567",
                timestamp = System.currentTimeMillis()
            )
        )

        // Execute purge
        val purgedCount = repository.purgeLegacySeededContacts()
        assertEquals(3, purgedCount)

        // Verify remaining contacts
        val remainingRules = repository.allRulesFlow.first()
        assertEquals(1, remainingRules.size)
        assertEquals("Alice Real Friend", remainingRules[0].displayName)
        assertEquals("+15551234567", remainingRules[0].normalizedNumber)

        // Verify legacy call events are removed, while real user events remain
        assertNull(db.contactRuleDao().getRuleForNumber("+919876543210"))
        assertNull(db.contactRuleDao().getRuleForNumber("+919876543211"))
        assertNull(db.contactRuleDao().getRuleForNumber("112"))
        assertNotNull(db.contactRuleDao().getRuleForNumber("+15551234567"))
    }

    @Test
    fun testRemoveContactFlowDeletesRulesAndCancelsPendingActions() = runBlocking {
        // Step 5c: Remove flow test:
        // User adds a contact
        val userContact = ContactRuleEntity(
            displayName = "Bob",
            rawNumber = "+15559876543",
            normalizedNumber = "+15559876543",
            customThreshold = 4,
            isAlarmEnabled = true,
            isLocationEnabled = true
        )
        db.contactRuleDao().insertOrUpdate(userContact)

        // Insert a pending SMS outbox entry and call event
        db.callEventDao().insertCallEvent(
            CallEventEntity(
                rawNumber = "+15559876543",
                normalizedNumber = "+15559876543",
                timestamp = System.currentTimeMillis()
            )
        )
        db.outboxSmsDao().insertOutboxSms(
            OutboxSmsEntity(
                recipientNumber = "+15559876543",
                messageBody = "SOS test",
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
        )

        // Remove contact completely with history cleanup
        repository.removeContact("+15559876543", deleteHistory = true)

        // Verify contact rule deleted
        assertNull(db.contactRuleDao().getRuleForNumber("+15559876543"))

        // Verify call events deleted
        val recentCalls = db.callEventDao().getUnhandledCallsSince("+15559876543", 0L)
        assertTrue(recentCalls.isEmpty())

        // Verify outbox SMS is deleted / cancelled
        val pendingSms = db.outboxSmsDao().getPendingSmsList()
        assertTrue(pendingSms.none { it.recipientNumber == "+15559876543" })
    }
}
