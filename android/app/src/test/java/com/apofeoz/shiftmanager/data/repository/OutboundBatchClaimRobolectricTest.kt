package com.apofeoz.shiftmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apofeoz.shiftmanager.data.local.OutboundBatchEntity
import com.apofeoz.shiftmanager.data.local.ShiftDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class OutboundBatchClaimRobolectricTest {

    private lateinit var db: ShiftDatabase
    private lateinit var repo: OutboundBatchQueueRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ShiftDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OutboundBatchQueueRepository(ctx, db, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun claimNextBatch_fifo_andExhaustsQueue() = runBlocking {
        val dao = db.outboundDao()
        dao.insert(OutboundBatchEntity(batchUid = "first", submittedAt = "t", bodyJson = "{}"))
        dao.insert(OutboundBatchEntity(batchUid = "second", submittedAt = "t", bodyJson = "{}"))

        val a = repo.claimNextBatch()
        assertEquals("first", a?.batchUid)

        val b = repo.claimNextBatch()
        assertEquals("second", b?.batchUid)

        assertNull(repo.claimNextBatch())
    }
}
