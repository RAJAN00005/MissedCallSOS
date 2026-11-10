package com.example

import com.example.sms.SmsDispatcher
import com.example.telephony.PhoneNumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosEngineUnitTest {

    @Test
    fun testPhoneNumberNormalizationE164() {
        val indianNumberWithSpaces = "98765 43210"
        val normalized = PhoneNumberHelper.normalizeToE164(indianNumberWithSpaces, "IN")
        assertEquals("+919876543210", normalized)

        val numberWithPlus = "+91 98765-43210"
        val normalizedPlus = PhoneNumberHelper.normalizeToE164(numberWithPlus, "IN")
        assertEquals("+919876543210", normalizedPlus)

        val unknownNumber = "-1"
        assertEquals("UNKNOWN", PhoneNumberHelper.normalizeToE164(unknownNumber))
    }

    @Test
    fun testPhoneNumberMatching() {
        val num1 = "+919876543210"
        val num2 = "09876543210"
        assertTrue(PhoneNumberHelper.isSameNumber(num1, num2, "IN"))

        val num3 = "9876543210"
        assertTrue(PhoneNumberHelper.isSameNumber(num1, num3, "IN"))

        val differentNum = "+919123456789"
        assertFalse(PhoneNumberHelper.isSameNumber(num1, differentNum, "IN"))
    }

    @Test
    fun testGsm7SmsFormattingUnder160Chars() {
        val message = SmsDispatcher.buildEmergencySmsMessage(5)

        // Verify message starts with SOS and contains missed call count
        assertTrue(message.startsWith("SOS: I missed your 5 emergency calls!"))
        // Verify plain GSM characters (no emojis)
        assertTrue(message.all { it.code < 128 })
        // Under 160 characters for single SMS delivery
        assertTrue(message.length <= 160)
    }

    @Test
    fun testSlidingWindowCalculation() {
        val now = 1_000_000L
        val windowMinutes = 30
        val windowStart = now - (windowMinutes * 60_000L)

        val callTimes = listOf(
            now - 5 * 60_000L,  // 5m ago (inside)
            now - 15 * 60_000L, // 15m ago (inside)
            now - 25 * 60_000L, // 25m ago (inside)
            now - 35 * 60_000L  // 35m ago (OUTSIDE window)
        )

        val callsInWindow = callTimes.count { it >= windowStart }
        assertEquals(3, callsInWindow)
    }

    @Test
    fun testCooldownLogic() {
        val now = 100_000_000L
        val cooldownMinutes = 15
        val cooldownMillis = cooldownMinutes * 60_000L

        val lastTriggerWithinCooldown = now - (10 * 60_000L) // 10 min ago
        val isCooldownActive1 = (now - lastTriggerWithinCooldown) < cooldownMillis
        assertTrue(isCooldownActive1) // Should block new trigger

        val lastTriggerOutsideCooldown = now - (20 * 60_000L) // 20 min ago
        val isCooldownActive2 = (now - lastTriggerOutsideCooldown) < cooldownMillis
        assertFalse(isCooldownActive2) // Should allow new trigger
    }
}
