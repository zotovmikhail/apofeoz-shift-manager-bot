# Offline-first sync policy

## Goals

- Android must keep the last authenticated foreman inside the app during long network outages.
- Auth failures must not delete offline work.
- Network failures must not clear tokens or local sessions.
- Sync conflicts must not block unrelated workers/sessions.
- Backend must keep enough audit data to diagnose devices and failed batches.

## Android policy

- The last successful `/users/me` profile is stored in `CachedUserRepository`.
- On app start, if `/users/me` is unavailable because of network failure, `AppRoot` opens the cached user.
- `TokenRefreshAuthenticator` clears tokens only when refresh returns `401` or `403`.
- Refresh network failures return control to the caller without clearing tokens.
- `sessionExpired` clears auth tokens, but does not clear local active sessions or outbound work.
- Manual logout clears tokens and the cached profile, but pending/failed sync data remains on the device.

## Outbound queue

`outbound_batches` is the local source for automatic sync attempts.

Each row now stores:

- `ownerUserId`
- `workerId`
- `sessionId`
- `eventTypes`
- `deviceId`
- `appVersion`
- `attemptCount`
- `lastAttemptAt`
- `lastHttpCode`
- `lastReason`
- `state`

Current states:

- `PENDING`
- `IN_FLIGHT`
- `BLOCKED_AUTH`

The worker claims only rows belonging to the current cached user, plus legacy rows without owner.
`workerId`, `sessionId`, and `eventTypes` are denormalized queue metadata. They make it possible to quarantine later work for one worker without parsing the whole JSON queue and without blocking unrelated workers.

## Conflict handling

`409 Conflict` means a business conflict. It is not retried automatically.

On `409`, Android:

- stores a local failed batch with `failedIndex`, `reason`, and `failedEventType`;
- removes the row from the automatic queue;
- blocks the affected worker/session in UI;
- moves later pending rows for the same `workerId` from the automatic queue into local failed/deferred storage with `blocked_by_previous_conflict`;
- continues syncing pending rows of other workers.

On fatal `400` or `403`, Android applies the same worker-level quarantine, but marks deferred rows with `blocked_by_previous_error`.

Backend already stores the same failed batch in `failed_sync_batches`.

## Backend audit

Existing tables remain the foundation:

- `sync_batches`
- `sync_events`
- `failed_sync_batches`

They are extended with device/app metadata. A new `user_devices` table tracks:

- `device_id`
- `last_user_id`
- `last_seen_at`
- `last_login_at`
- `app_version`
- `platform`
- `device_model`
- `os_version`
- `label`

`GET /api/v1/devices` is ADMIN-only and returns registered device diagnostics.

For ADMIN, `GET /api/v1/sync/failed-batches` returns all failed batches. For non-admin users, it returns only their own failed batches.
