ALTER TABLE sync_batches
    ADD COLUMN device_id TEXT,
    ADD COLUMN app_version TEXT,
    ADD COLUMN platform TEXT,
    ADD COLUMN device_model TEXT,
    ADD COLUMN os_version TEXT,
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACKED';

ALTER TABLE sync_events
    ADD COLUMN operation_id TEXT,
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACKED',
    ADD COLUMN server_entity_id UUID;

CREATE TABLE user_devices (
    device_id       TEXT PRIMARY KEY,
    last_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    last_seen_at    TIMESTAMPTZ NOT NULL,
    last_login_at   TIMESTAMPTZ,
    app_version     TEXT,
    platform        TEXT NOT NULL DEFAULT 'android',
    device_model    TEXT,
    os_version      TEXT,
    label           TEXT
);

CREATE INDEX idx_user_devices_last_user ON user_devices(last_user_id);
