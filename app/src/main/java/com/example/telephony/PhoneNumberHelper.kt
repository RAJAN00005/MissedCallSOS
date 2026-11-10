package com.example.telephony

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object PhoneNumberHelper {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun normalizeToE164(rawNumber: String?, defaultCountryIso: String = "IN"): String {
        if (rawNumber.isNullOrBlank()) return "UNKNOWN"
        val trimmed = rawNumber.trim()
        if (trimmed == "-1" || trimmed == "-2" || trimmed.equals("unknown", ignoreCase = true) || trimmed.equals("private", ignoreCase = true)) {
            return "UNKNOWN"
        }

        return try {
            val parsed = phoneUtil.parse(trimmed, defaultCountryIso.uppercase(Locale.ROOT))
            if (phoneUtil.isValidNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                cleanDigits(trimmed)
            }
        } catch (e: NumberParseException) {
            cleanDigits(trimmed)
        }
    }

    private fun cleanDigits(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() || it == '+' }
        return if (digitsOnly.isNotBlank()) digitsOnly else "UNKNOWN"
    }

    fun formatForDisplay(rawOrE164: String, defaultCountryIso: String = "IN"): String {
        if (rawOrE164 == "UNKNOWN" || rawOrE164.isBlank()) return "Unknown Caller"
        return try {
            val parsed = phoneUtil.parse(rawOrE164, defaultCountryIso.uppercase(Locale.ROOT))
            phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        } catch (e: Exception) {
            rawOrE164
        }
    }

    fun isSameNumber(number1: String, number2: String, defaultCountryIso: String = "IN"): Boolean {
        val norm1 = normalizeToE164(number1, defaultCountryIso)
        val norm2 = normalizeToE164(number2, defaultCountryIso)
        if (norm1 == "UNKNOWN" || norm2 == "UNKNOWN") return false
        if (norm1 == norm2) return true

        return try {
            val match = phoneUtil.isNumberMatch(norm1, norm2)
            match == PhoneNumberUtil.MatchType.EXACT_MATCH || match == PhoneNumberUtil.MatchType.NSN_MATCH
        } catch (e: Exception) {
            false
        }
    }

    fun getDeviceCountryIso(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simCountry = tm?.simCountryIso
        if (!simCountry.isNullOrBlank() && simCountry.length == 2) {
            return simCountry.uppercase(Locale.ROOT)
        }
        val networkCountry = tm?.networkCountryIso
        if (!networkCountry.isNullOrBlank() && networkCountry.length == 2) {
            return networkCountry.uppercase(Locale.ROOT)
        }
        return Locale.getDefault().country.ifBlank { "IN" }.uppercase(Locale.ROOT)
    }
}
