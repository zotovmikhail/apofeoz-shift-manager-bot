package com.apofeoz.shiftmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [OutboundBatchEntity::class], version = 1, exportSchema = false)
abstract class ShiftDatabase : RoomDatabase() {
    abstract fun outboundDao(): OutboundBatchDao
}
