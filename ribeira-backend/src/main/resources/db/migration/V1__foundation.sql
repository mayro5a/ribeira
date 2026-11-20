-- Fundação: organizações, auditoria e feed de alterações para sincronização.

CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_organizations_name_not_blank CHECK (length(trim(name)) > 0)
);

-- Trilha de auditoria. Nunca guardar dados pessoais ou corpos de requisição.
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    community_id    UUID,
    actor_user_id   UUID,
    action          VARCHAR(64)  NOT NULL,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       UUID,
    details         JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_logs_org_time ON audit_logs (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- Contador por organização: incrementado no último passo da transação (ver ADR-0003).
CREATE TABLE sync_counters (
    organization_id UUID PRIMARY KEY REFERENCES organizations (id),
    last_seq        BIGINT NOT NULL DEFAULT 0 CHECK (last_seq >= 0)
);

CREATE TABLE change_log (
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    seq             BIGINT      NOT NULL,
    community_id    UUID,
    entity_type     VARCHAR(40) NOT NULL,
    entity_id       UUID        NOT NULL,
    changed_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, seq)
);
