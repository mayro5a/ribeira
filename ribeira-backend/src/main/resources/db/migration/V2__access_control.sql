-- Comunidades, usuários, papéis, permissões e tokens de renovação.

CREATE TABLE communities (
    id                   UUID PRIMARY KEY,
    organization_id      UUID         NOT NULL REFERENCES organizations (id),
    name                 VARCHAR(160) NOT NULL,
    description          VARCHAR(1000),
    municipality         VARCHAR(120),
    -- Localização aproximada e opcional da comunidade (nunca de moradores).
    approximate_location VARCHAR(255),
    primary_device_id    UUID,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_communities_name_not_blank CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX ux_communities_org_name ON communities (organization_id, lower(name));

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    email           VARCHAR(254) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DISABLED'))
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_org ON users (organization_id);

-- Papéis de sistema têm organization_id nulo; organizações podem criar os seus.
CREATE TABLE roles (
    id              UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations (id),
    code            VARCHAR(48)  NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(255),
    system          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_roles_system_scope CHECK (system = (organization_id IS NULL))
);
CREATE UNIQUE INDEX ux_roles_scope_code
    ON roles (COALESCE(organization_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

CREATE TABLE role_permissions (
    role_id    UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission VARCHAR(48) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- Papel organizacional: vale para todas as comunidades da organização.
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id),
    role_id UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

-- Um papel por comunidade.
CREATE TABLE community_users (
    user_id      UUID NOT NULL REFERENCES users (id),
    community_id UUID NOT NULL REFERENCES communities (id),
    role_id      UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, community_id)
);
CREATE INDEX ix_community_users_community ON community_users (community_id);

-- Tokens de renovação: apenas o hash SHA-256 é armazenado (ADR-0005).
CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id),
    chain_id       UUID        NOT NULL,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(24),
    CONSTRAINT ck_refresh_tokens_reason
        CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'USER_DISABLED'))
);
CREATE INDEX ix_refresh_tokens_chain ON refresh_tokens (chain_id);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at);

-- Papéis de sistema (ver docs/security.md).
INSERT INTO roles (id, organization_id, code, name, description, system, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000101', NULL, 'ORG_ADMIN', 'Administrador da organização',
        'Gerencia organização, comunidades, usuários e permissões', TRUE, now(), now()),
       ('00000000-0000-0000-0000-000000000102', NULL, 'COORDINATOR', 'Coordenador comunitário',
        'Acompanha estoque, doações, distribuição e perdas das comunidades autorizadas', TRUE, now(), now()),
       ('00000000-0000-0000-0000-000000000103', NULL, 'STOCK_KEEPER', 'Responsável pelo estoque',
        'Registra recebimentos, lotes, movimentações e perdas', TRUE, now(), now()),
       ('00000000-0000-0000-0000-000000000104', NULL, 'DISTRIBUTOR', 'Distribuidor',
        'Registra entregas às famílias', TRUE, now(), now()),
       ('00000000-0000-0000-0000-000000000105', NULL, 'VIEWER', 'Visualizador',
        'Consulta relatórios e indicadores, sem alterações', TRUE, now(), now());

INSERT INTO role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000101', p
FROM unnest(ARRAY ['ORGANIZATION_MANAGE', 'COMMUNITY_MANAGE', 'USER_MANAGE', 'DEVICE_MANAGE',
    'COMMUNITY_READ', 'FAMILY_READ', 'FAMILY_WRITE', 'DONOR_MANAGE', 'FOOD_MANAGE',
    'DONATION_WRITE', 'STOCK_READ', 'STOCK_WRITE', 'STOCK_ADJUST', 'DISTRIBUTION_WRITE',
    'LOSS_WRITE', 'MONITORING_WRITE', 'ALERT_RESOLVE', 'REPORT_READ',
    'SYNC_CONFLICT_RESOLVE']) AS p;

INSERT INTO role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000102', p
FROM unnest(ARRAY ['COMMUNITY_READ', 'FAMILY_READ', 'FAMILY_WRITE', 'STOCK_READ',
    'MONITORING_WRITE', 'ALERT_RESOLVE', 'REPORT_READ', 'SYNC_CONFLICT_RESOLVE']) AS p;

INSERT INTO role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000103', p
FROM unnest(ARRAY ['COMMUNITY_READ', 'DONOR_MANAGE', 'DONATION_WRITE', 'STOCK_READ',
    'STOCK_WRITE', 'STOCK_ADJUST', 'LOSS_WRITE', 'MONITORING_WRITE']) AS p;

INSERT INTO role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000104', p
FROM unnest(ARRAY ['COMMUNITY_READ', 'FAMILY_READ', 'STOCK_READ', 'DISTRIBUTION_WRITE']) AS p;

INSERT INTO role_permissions (role_id, permission)
SELECT '00000000-0000-0000-0000-000000000105', p
FROM unnest(ARRAY ['COMMUNITY_READ', 'STOCK_READ', 'REPORT_READ']) AS p;
