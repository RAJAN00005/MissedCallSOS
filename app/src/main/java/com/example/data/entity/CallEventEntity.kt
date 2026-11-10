package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_events",
    indices = [
        Index(value = ["normalizedNumber"]),
        Index(value = ["timestamp"]),
        Index(value = ["isTest"])
    ]
)
data class CallEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawNumber: String,
    val normalizedNumber: String,
    val callerName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val simSlot: Int = 0, // 0 for SIM1, 1 for SIM2, -1 unknown
    val isHandled: Boolean = false, // true if user answered, called back, or dismissed
    val callType: Int = 3, // 3 = MISSED (matching android.provider.CallLog.Calls.MISSED_TYPE)
    val isTest: Boolean = false
)
