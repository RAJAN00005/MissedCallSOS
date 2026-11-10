package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_sms",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["isTest"])
    ]
)
data class OutboxSmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipientNumber: String,
    val messageBody: String,
    val simSlot: Int = -1, // -1 for auto/default, 0 for SIM1, 1 for SIM2
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, SENT, DELIVERED, FAILED
    val retryCount: Int = 0,
    val lastError: String? = null,
    val triggerHistoryId: Long? = null,
    val isTest: Boolean = false
)
