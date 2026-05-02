package com.apofeoz.shiftmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbound_batches")
data class OutboundBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchUid: String,
    val submittedAt: String,
    /** Полное тело `SyncBatchRequest` в JSON */
    val bodyJson: String,
    val ownerUserId: String? = null,
    val deviceId: String? = null,
    val appVersion: String? = null,
    val attemptCount: Int = 0,
    val lastAttemptAt: String? = null,
    val lastHttpCode: Int? = null,
    val lastReason: String? = null,
    val state: String = STATE_PENDING,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_IN_FLIGHT = "IN_FLIGHT"
        const val STATE_BLOCKED_AUTH = "BLOCKED_AUTH"
    }
}
