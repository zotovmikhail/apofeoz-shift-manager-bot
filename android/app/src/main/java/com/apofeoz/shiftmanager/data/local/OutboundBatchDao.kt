package com.apofeoz.shiftmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboundBatchDao {
    @Insert
    suspend fun insert(entity: OutboundBatchEntity): Long

    @Query("SELECT id FROM outbound_batches WHERE state = :pending ORDER BY id ASC LIMIT 1")
    suspend fun findFirstPendingId(pending: String = OutboundBatchEntity.STATE_PENDING): Long?

    @Query("SELECT * FROM outbound_batches WHERE id = :id")
    suspend fun getById(id: Long): OutboundBatchEntity?

    @Query(
        "UPDATE outbound_batches SET state = :inFlight WHERE id = :id AND state = :pending",
    )
    suspend fun markInFlight(
        id: Long,
        inFlight: String = OutboundBatchEntity.STATE_IN_FLIGHT,
        pending: String = OutboundBatchEntity.STATE_PENDING,
    ): Int

    @Query("UPDATE outbound_batches SET state = :pending WHERE id = :id")
    suspend fun resetPending(
        id: Long,
        pending: String = OutboundBatchEntity.STATE_PENDING,
    )

    @Query("DELETE FROM outbound_batches WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM outbound_batches WHERE state = :pending")
    suspend fun countPending(pending: String = OutboundBatchEntity.STATE_PENDING): Int
}
