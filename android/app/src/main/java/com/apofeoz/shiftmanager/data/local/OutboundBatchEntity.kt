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
    val state: String = STATE_PENDING,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_IN_FLIGHT = "IN_FLIGHT"
    }
}
