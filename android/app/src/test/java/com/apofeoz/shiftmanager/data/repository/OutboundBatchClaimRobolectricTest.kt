package com.apofeoz.shiftmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.CachedUserRepository
import com.apofeoz.shiftmanager.data.local.LocalFailedBatch
import com.apofeoz.shiftmanager.data.local.LocalFailedOutboundBatchesRepository
import com.apofeoz.shiftmanager.data.local.OutboundBatchEntity
import com.apofeoz.shiftmanager.data.local.ShiftDatabase
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        AppContainer.cachedUserRepository = CachedUserRepository(ctx)
        repo = OutboundBatchQueueRepository(ctx, db, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun claimNextBatch_fifo_andExhaustsQueue() = runBlocking {
        AppContainer.cachedUserRepository.save(
            UserResponseDto(
                id = "foreman-1",
                firstName = "Test",
                lastName = "Foreman",
                role = "FOREMAN",
                status = "ACTIVE",
            ),
        )
        val dao = db.outboundDao()
        dao.insert(OutboundBatchEntity(batchUid = "first", submittedAt = "t", bodyJson = "{}"))
        dao.insert(OutboundBatchEntity(batchUid = "second", submittedAt = "t", bodyJson = "{}"))

        val a = repo.claimNextBatch()
        assertEquals("first", a?.batchUid)

        val b = repo.claimNextBatch()
        assertEquals("second", b?.batchUid)

        assertNull(repo.claimNextBatch())
    }

    @Test
    fun quarantinePendingForWorkerAfter_movesOnlyFollowingBatchesForThatWorkerToFailed() = runBlocking {
        val dao = db.outboundDao()
        val currentId = dao.insert(
            OutboundBatchEntity(
                batchUid = "a2-start",
                submittedAt = "t",
                bodyJson = "{}",
                workerId = "worker-a",
                sessionId = "a2",
            ),
        )
        dao.insert(
            OutboundBatchEntity(
                batchUid = "a2-end",
                submittedAt = "t",
                bodyJson = "{}",
                workerId = "worker-a",
                sessionId = "a2",
            ),
        )
        dao.insert(
            OutboundBatchEntity(
                batchUid = "b1-start",
                submittedAt = "t",
                bodyJson = "{}",
                workerId = "worker-b",
                sessionId = "b1",
            ),
        )
        dao.insert(
            OutboundBatchEntity(
                batchUid = "a3-start",
                submittedAt = "t",
                bodyJson = "{}",
                workerId = "worker-a",
                sessionId = "a3",
            ),
        )

        val moved = repo.quarantinePendingForWorkerAfter(
            afterId = currentId,
            workerId = "worker-a",
            httpCode = 409,
            message = "deferred",
            reason = "blocked_by_previous_conflict",
        )

        assertEquals(2, moved)
        val remaining = dao.listPendingAfter(currentId).map { it.batchUid }
        assertEquals(listOf("b1-start"), remaining)
        val failedBodies = db.localFailedDao().list().map { it.bodyJson }
        assertEquals(listOf("{}", "{}"), failedBodies)
        assertTrue(db.localFailedDao().list().all { it.reason == "blocked_by_previous_conflict" })
    }

    @Test
    fun localFailedRepository_keepsMoreThanLegacyDataStoreCap() = runBlocking {
        val failed = LocalFailedOutboundBatchesRepository(
            ApplicationProvider.getApplicationContext(),
            db,
        )

        repeat(60) { index ->
            failed.add(
                LocalFailedBatch(
                    httpCode = 409,
                    message = "failed-$index",
                    submittedAt = "t-$index",
                    bodyJson = "{}",
                ),
            )
        }

        assertEquals(60, failed.list().size)
    }
}
