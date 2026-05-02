package com.apofeoz.shiftmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalFailedBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocalFailedBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalFailedBatchEntity>)

    @Query("SELECT * FROM local_failed_batches ORDER BY createdAt DESC")
    suspend fun list(): List<LocalFailedBatchEntity>

    @Query("DELETE FROM local_failed_batches WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_failed_batches")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM local_failed_batches")
    suspend fun count(): Int
}
