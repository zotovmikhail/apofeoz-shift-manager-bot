package com.apofeoz.backend.data

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Users : UUIDTable("users") {
    val email = text("email").nullable().uniqueIndex()
    val phone = text("phone").nullable().uniqueIndex()
    val firstName = text("first_name")
    val lastName = text("last_name")
    val passwordHash = text("password_hash")
    val role = text("role")
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}

object RefreshTokens : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val tokenHash = text("token_hash").uniqueIndex()
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
}

object Workers : UUIDTable("workers") {
    val userId = optReference("user_id", Users, onDelete = ReferenceOption.SET_NULL).uniqueIndex()
    val foremanId = reference("foreman_id", Users)
    val firstName = text("first_name")
    val lastName = text("last_name")
    val position = text("position").nullable()
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}

object Sessions : UUIDTable("sessions") {
    val workerId = reference("worker_id", Workers)
    val foremanId = reference("foreman_id", Users)
    val startAt = timestampWithTimeZone("start_at")
    val endAt = timestampWithTimeZone("end_at").nullable()
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}

object SyncBatches : UUIDTable("sync_batches") {
    val userId = reference("user_id", Users)
    val batchUid = text("batch_uid").uniqueIndex()
    val submittedAt = timestampWithTimeZone("submitted_at")
    val appliedAt = timestampWithTimeZone("applied_at")
    val resultJson = jsonb("result_json").nullable()
    val deviceId = text("device_id").nullable()
    val appVersion = text("app_version").nullable()
    val platform = text("platform").nullable()
    val deviceModel = text("device_model").nullable()
    val osVersion = text("os_version").nullable()
    val status = text("status")
}

object SyncEvents : UUIDTable("sync_events") {
    val batchId = reference("batch_id", SyncBatches, onDelete = ReferenceOption.CASCADE)
    val type = text("type")
    val payload = jsonb("payload")
    val createdAt = timestampWithTimeZone("created_at")
    val operationId = text("operation_id").nullable()
    val status = text("status")
    val serverEntityId = uuid("server_entity_id").nullable()
}

object FailedSyncBatches : UUIDTable("failed_sync_batches") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val batchUid = text("batch_uid")
    val submittedAt = timestampWithTimeZone("submitted_at")
    val eventsSnapshot = jsonb("events_snapshot")
    val failedIndex = integer("failed_index")
    val reason = text("reason")
    val createdAt = timestampWithTimeZone("created_at")
}

object UserDevices : Table("user_devices") {
    val deviceId = text("device_id")
    val lastUserId = reference("last_user_id", Users, onDelete = ReferenceOption.SET_NULL).nullable()
    val lastSeenAt = timestampWithTimeZone("last_seen_at")
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val appVersion = text("app_version").nullable()
    val platform = text("platform")
    val deviceModel = text("device_model").nullable()
    val osVersion = text("os_version").nullable()
    val label = text("label").nullable()

    override val primaryKey = PrimaryKey(deviceId)
}
