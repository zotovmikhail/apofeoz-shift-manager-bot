CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           TEXT UNIQUE,
    phone           TEXT UNIQUE,
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('USER','FOREMAN','ADMIN')),
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE workers (
    id              UUID PRIMARY KEY,
    user_id         UUID UNIQUE REFERENCES users(id),
    foreman_id      UUID NOT NULL REFERENCES users(id),
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    position        TEXT,
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workers_foreman ON workers(foreman_id);

CREATE TABLE sessions (
    id              UUID PRIMARY KEY,
    worker_id       UUID NOT NULL REFERENCES workers(id),
    foreman_id      UUID NOT NULL REFERENCES users(id),
    start_at        TIMESTAMPTZ NOT NULL,
    end_at          TIMESTAMPTZ,
    status          TEXT NOT NULL CHECK (status IN ('ACTIVE','CLOSED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_worker_active ON sessions(worker_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_sessions_foreman ON sessions(foreman_id);
CREATE INDEX idx_sessions_time ON sessions(start_at, end_at);

CREATE UNIQUE INDEX ux_sessions_worker_active
    ON sessions(worker_id)
    WHERE status = 'ACTIVE';

CREATE TABLE sync_batches (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id),
    batch_uid       TEXT NOT NULL UNIQUE,
    submitted_at    TIMESTAMPTZ NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    result_json     JSONB
);

CREATE TABLE sync_events (
    id              UUID PRIMARY KEY,
    batch_id        UUID NOT NULL REFERENCES sync_batches(id) ON DELETE CASCADE,
    type            TEXT NOT NULL CHECK (type IN ('START_SESSION', 'END_SESSION')),
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sync_events_batch ON sync_events(batch_id);

CREATE TABLE failed_sync_batches (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    batch_uid       TEXT NOT NULL,
    submitted_at    TIMESTAMPTZ NOT NULL,
    events_snapshot JSONB NOT NULL,
    failed_index    INT NOT NULL,
    reason          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_failed_sync_batches_user ON failed_sync_batches(user_id);
