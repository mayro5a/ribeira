-- Aparelhos, idempotência de operações offline, conflitos e checkpoints de sincronização.

CREATE TABLE devices (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    name            VARCHAR(120) NOT NULL,
    last_user_id    UUID         NOT NULL REFERENCES users (id),
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    registered_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID REFERENCES users (id),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_devices_org ON devices (organization_id);

ALTER TABLE communities
    ADD CONSTRAINT fk_communities_primary_device FOREIGN KEY (primary_device_id) REFERENCES devices (id);

-- RN08: operation_id como chave primária garante processamento único.
CREATE TABLE sync_operations (
    operation_id      UUID PRIMARY KEY,
    organization_id   UUID        NOT NULL REFERENCES organizations (id),
    community_id      UUID        NOT NULL REFERENCES communities (id),
    device_id         UUID        NOT NULL REFERENCES devices (id),
    user_id           UUID        NOT NULL REFERENCES users (id),
    entity_type       VARCHAR(24) NOT NULL,
    operation_type    VARCHAR(32) NOT NULL,
    schema_version    INTEGER     NOT NULL,
    payload           JSONB       NOT NULL,
    payload_hash      VARCHAR(64) NOT NULL,
    status            VARCHAR(16) NOT NULL CHECK (status IN ('APPLIED', 'REJECTED', 'CONFLICT', 'DISCARDED')),
    error_code        VARCHAR(64),
    error_message     VARCHAR(1000),
    server_seq        BIGINT,
    client_created_at TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL,
    processed_at      TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_sync_operations_applied_seq CHECK (status <> 'APPLIED' OR server_seq IS NOT NULL)
);
CREATE INDEX ix_sync_operations_device ON sync_operations (device_id, received_at DESC);

-- RN13: operações rejeitadas ou em conflito ficam preservadas para revisão humana.
CREATE TABLE sync_conflicts (
    id               UUID PRIMARY KEY,
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    community_id     UUID          NOT NULL REFERENCES communities (id),
    operation_id     UUID          NOT NULL UNIQUE REFERENCES sync_operations (operation_id),
    kind             VARCHAR(16)   NOT NULL CHECK (kind IN ('CONFLICT', 'REJECTED')),
    reason_code      VARCHAR(64)   NOT NULL,
    message          VARCHAR(1000) NOT NULL,
    status           VARCHAR(24)   NOT NULL CHECK (status IN ('OPEN', 'RESOLVED_APPLIED', 'RESOLVED_DISCARDED')),
    created_at       TIMESTAMPTZ   NOT NULL,
    resolved_at      TIMESTAMPTZ,
    resolved_by      UUID REFERENCES users (id),
    resolution_notes VARCHAR(1000),
    version          BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_sync_conflicts_community_status ON sync_conflicts (community_id, status);

CREATE TABLE sync_conflict_events (
    id            UUID PRIMARY KEY,
    conflict_id   UUID          NOT NULL REFERENCES sync_conflicts (id),
    event_type    VARCHAR(24)   NOT NULL CHECK (event_type IN ('DETECTED', 'RETRY_FAILED', 'APPLIED', 'DISCARDED')),
    message       VARCHAR(1000),
    actor_user_id UUID REFERENCES users (id),
    occurred_at   TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_sync_conflict_events_conflict ON sync_conflict_events (conflict_id, occurred_at);

CREATE TABLE sync_checkpoints (
    device_id   UUID PRIMARY KEY REFERENCES devices (id),
    last_cursor BIGINT      NOT NULL CHECK (last_cursor >= 0),
    updated_at  TIMESTAMPTZ NOT NULL
);
