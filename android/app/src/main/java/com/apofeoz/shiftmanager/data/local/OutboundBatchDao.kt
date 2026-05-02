package com.apofeoz.shiftmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboundBatchDao {
    @Insert
    suspend fun insert(entity: OutboundBatchEntity): Long

    @Query(
        "SELECT id FROM outbound_batches " +
            "WHERE state = :pending AND (ownerUserId = :ownerUserId OR ownerUserId IS NULL) " +
            "ORDER BY id ASC LIMIT 1",
    )
    suspend fun findFirstPendingIdForOwner(
        ownerUserId: String,
        pending: String = OutboundBatchEntity.STATE_PENDING,
    ): Long?

    @Query("SELECT * FROM outbound_batches WHERE id = :id")
    suspend fun getById(id: Long): OutboundBatchEntity?

    @Query(
        "UPDATE outbound_batches SET state = :inFlight, attemptCount = attemptCount + 1, lastAttemptAt = :attemptedAt " +
            "WHERE id = :id AND state = :pending",
    )
    suspend fun markInFlight(
        id: Long,
        attemptedAt: String,
        inFlight: String = OutboundBatchEntity.STATE_IN_FLIGHT,
        pending: String = OutboundBatchEntity.STATE_PENDING,
    ): Int

    @Query("UPDATE outbound_batches SET state = :pending, lastHttpCode = :httpCode, lastReason = :reason WHERE id = :id")
    suspend fun resetPending(
        id: Long,
        httpCode: Int? = null,
        reason: String? = null,
        pending: String = OutboundBatchEntity.STATE_PENDING,
    )

    @Query("UPDATE outbound_batches SET state = :blockedAuth, lastHttpCode = :httpCode, lastReason = :reason WHERE id = :id")
    suspend fun markBlockedAuth(
        id: Long,
        httpCode: Int? = null,
        reason: String? = null,
        blockedAuth: String = OutboundBatchEntity.STATE_BLOCKED_AUTH,
    )

    @Query("UPDATE outbound_batches SET state = :pending WHERE state = :blockedAuth AND ownerUserId = :ownerUserId")
    suspend fun unblockAuthForOwner(
        ownerUserId: String,
        pending: String = OutboundBatchEntity.STATE_PENDING,
        blockedAuth: String = OutboundBatchEntity.STATE_BLOCKED_AUTH,
    ): Int

    @Query("DELETE FROM outbound_batches WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM outbound_batches WHERE state = :pending")
    suspend fun countPending(pending: String = OutboundBatchEntity.STATE_PENDING): Int

    @Query("SELECT COUNT(*) FROM outbound_batches WHERE state = :blockedAuth")
    suspend fun countBlockedAuth(blockedAuth: String = OutboundBatchEntity.STATE_BLOCKED_AUTH): Int

    @Query(
        "SELECT bodyJson FROM outbound_batches WHERE state = :pending OR state = :inFlight OR state = :blockedAuth ORDER BY id ASC",
    )
    suspend fun listOpenBatchBodies(
        pending: String = OutboundBatchEntity.STATE_PENDING,
        inFlight: String = OutboundBatchEntity.STATE_IN_FLIGHT,
        blockedAuth: String = OutboundBatchEntity.STATE_BLOCKED_AUTH,
    ): List<String>
}
