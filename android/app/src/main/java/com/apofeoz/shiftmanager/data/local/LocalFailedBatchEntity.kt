package com.apofeoz.shiftmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "local_failed_batches")
data class LocalFailedBatchEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val httpCode: Int,
    val message: String,
    val submittedAt: String,
    val bodyJson: String,
    val failedIndex: Int? = null,
    val reason: String? = null,
    val failedEventType: String? = null,
    val createdAt: String,
)
