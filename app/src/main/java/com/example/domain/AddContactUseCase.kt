package com.example.domain

import com.example.data.entity.ContactRuleEntity
import com.example.data.repository.SosRepository
import com.example.telephony.PhoneNumberHelper

sealed class AddContactResult {
    data class Success(val id: Long, val isNew: Boolean) : AddContactResult()
    data class InvalidNumber(val reason: String) : AddContactResult()
    data class EmptyName(val reason: String) : AddContactResult()
}

class AddContactUseCase(
    private val repository: SosRepository
) {
    suspend operator fun invoke(
        displayName: String,
        rawNumber: String,
        groupCategory: String = "Family",
        isAlarmEnabled: Boolean = true,
        customThreshold: Int? = null,
        customWindowMinutes: Int? = null,
        contactLookupKey: String? = null,
        defaultCountryIso: String = "IN"
    ): AddContactResult {
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            return AddContactResult.EmptyName("Contact name cannot be blank")
        }

        val normalized = PhoneNumberHelper.normalizeToE164(rawNumber, defaultCountryIso)
        if (normalized == "UNKNOWN" || normalized.length < 5) {
            return AddContactResult.InvalidNumber("Invalid phone number format")
        }

        // De-duplication: check if already exists
        val existing = repository.getRuleForNumber(normalized)
        val ruleToSave = if (existing != null) {
            existing.copy(
                displayName = trimmedName,
                isAlarmEnabled = isAlarmEnabled,
                isLocationEnabled = false,
                groupCategory = groupCategory,
                customThreshold = customThreshold ?: existing.customThreshold,
                customWindowMinutes = customWindowMinutes ?: existing.customWindowMinutes,
                contactLookupKey = contactLookupKey ?: existing.contactLookupKey
            )
        } else {
            ContactRuleEntity(
                displayName = trimmedName,
                rawNumber = rawNumber.trim(),
                normalizedNumber = normalized,
                isAlarmEnabled = isAlarmEnabled,
                isLocationEnabled = false,
                customThreshold = customThreshold,
                customWindowMinutes = customWindowMinutes,
                groupCategory = groupCategory,
                contactLookupKey = contactLookupKey
            )
        }

        val id = repository.saveRule(ruleToSave)
        return AddContactResult.Success(id = id, isNew = (existing == null))
    }
}
