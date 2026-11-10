package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contact_rules",
    indices = [
        Index(value = ["normalizedNumber"], unique = true)
    ]
)
data class ContactRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactLookupKey: String? = null,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String,
    val isAlarmEnabled: Boolean = true,
    val isLocationEnabled: Boolean = false,
    val customThreshold: Int? = null, // null means use global threshold
    val customWindowMinutes: Int? = null, // null means use global window
    val groupCategory: String = "Family", // "Family", "Emergency", "Custom"
    val lastTriggerTimestamp: Long = 0L,
    val isOptedOut: Boolean = false // user replied STOP to consent handshake
)
