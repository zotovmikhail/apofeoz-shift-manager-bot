package com.apofeoz.shiftmanager.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [OutboundBatchEntity::class], version = 3, exportSchema = false)
abstract class ShiftDatabase : RoomDatabase() {
    abstract fun outboundDao(): OutboundBatchDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN ownerUserId TEXT")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN deviceId TEXT")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN appVersion TEXT")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN lastAttemptAt TEXT")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN lastHttpCode INTEGER")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN lastReason TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN workerId TEXT")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN sessionId TEXT")
                db.execSQL("ALTER TABLE outbound_batches ADD COLUMN eventTypes TEXT")
            }
        }
    }
}
