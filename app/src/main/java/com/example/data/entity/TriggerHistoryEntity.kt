package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trigger_history",
    indices = [
        Index(value = ["triggeredAt"]),
        Index(value = ["callerNumber"]),
        Index(value = ["isTest"])
    ]
)
data class TriggerHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val callerNumber: String,
    val callerName: String? = null,
    val missedCount: Int,
    val triggeredAt: Long = System.currentTimeMillis(),
    val alarmDismissedAt: Long? = null,
    val isLocationSent: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val batteryPercent: Int? = null,
    val deliveryStatus: String = "ALARM_FIRED", // ALARM_FIRED, SMS_SENT, SMS_FAILED, DISMISSED
    val isTest: Boolean = false
)
