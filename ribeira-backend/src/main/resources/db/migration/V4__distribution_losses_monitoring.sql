-- Famílias, distribuições, perdas, monitoramento e alertas.

-- Minimização de dados: família identificada por código comunitário, sem nome nem documento.
CREATE TABLE families (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    community_id    UUID         NOT NULL REFERENCES communities (id),
    code            VARCHAR(40)  NOT NULL CHECK (length(trim(code)) > 0),
    member_count    INTEGER      NOT NULL CHECK (member_count BETWEEN 1 AND 50),
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_families_community_code ON families (community_id, lower(code));

CREATE TABLE distributions (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organizations (id),
    community_id    UUID          NOT NULL REFERENCES communities (id),
    distributed_at  TIMESTAMPTZ   NOT NULL,
    notes           VARCHAR(1000),
    performed_by    UUID          NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_distributions_community_time ON distributions (community_id, distributed_at DESC);

CREATE TABLE distribution_items (
    id              UUID PRIMARY KEY,
    distribution_id UUID          NOT NULL REFERENCES distributions (id),
    batch_id        UUID          NOT NULL REFERENCES food_batches (id),
    food_id         UUID          NOT NULL REFERENCES foods (id),
    quantity        NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    unit            VARCHAR(16)   NOT NULL,
    movement_id     UUID          NOT NULL UNIQUE REFERENCES stock_movements (id),
    position        INTEGER       NOT NULL
);
CREATE INDEX ix_distribution_items_distribution ON distribution_items (distribution_id);

CREATE TABLE distribution_families (
    distribution_id UUID NOT NULL REFERENCES distributions (id),
    family_id       UUID NOT NULL REFERENCES families (id),
    PRIMARY KEY (distribution_id, family_id)
);
CREATE INDEX ix_distribution_families_family ON distribution_families (family_id);

CREATE TABLE food_losses (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organizations (id),
    community_id    UUID          NOT NULL REFERENCES communities (id),
    -- RN05: toda perda está vinculada a um lote.
    batch_id        UUID          NOT NULL REFERENCES food_batches (id),
    food_id         UUID          NOT NULL REFERENCES foods (id),
    quantity        NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    unit            VARCHAR(16)   NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    reason          VARCHAR(32)   NOT NULL CHECK (reason IN ('HUMIDITY', 'HEAT_OR_SUN_EXPOSURE',
        'DAMAGED_PACKAGING', 'EXPIRATION', 'PESTS', 'TRANSPORT', 'SUSPECTED_CONTAMINATION', 'OTHER',
        'UNIDENTIFIED')),
    description     VARCHAR(1000),
    status          VARCHAR(16)   NOT NULL CHECK (status IN ('REGISTERED', 'UNDER_REVIEW', 'CLOSED')),
    movement_id     UUID          NOT NULL UNIQUE REFERENCES stock_movements (id),
    recorded_by     UUID          NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_food_losses_community_time ON food_losses (community_id, occurred_at DESC);

-- Critérios configuráveis por local ou por categoria (RN15). Nunca um limite universal.
CREATE TABLE storage_conditions (
    id                          UUID PRIMARY KEY,
    organization_id             UUID         NOT NULL REFERENCES organizations (id),
    storage_location_id         UUID REFERENCES storage_locations (id),
    food_category               VARCHAR(32),
    min_temperature             NUMERIC(5,2),
    max_temperature             NUMERIC(5,2),
    min_humidity                NUMERIC(5,2) CHECK (min_humidity BETWEEN 0 AND 100),
    max_humidity                NUMERIC(5,2) CHECK (max_humidity BETWEEN 0 AND 100),
    max_days_without_inspection INTEGER CHECK (max_days_without_inspection BETWEEN 1 AND 365),
    -- Fonte técnica que fundamenta os limites (obrigatória).
    guidance_source             VARCHAR(500) NOT NULL CHECK (length(trim(guidance_source)) > 0),
    active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_storage_conditions_scope CHECK (storage_location_id IS NOT NULL OR food_category IS NOT NULL),
    CONSTRAINT ck_storage_conditions_temperature CHECK (min_temperature IS NULL OR max_temperature IS NULL OR min_temperature <= max_temperature),
    CONSTRAINT ck_storage_conditions_humidity CHECK (min_humidity IS NULL OR max_humidity IS NULL OR min_humidity <= max_humidity)
);

CREATE TABLE environmental_readings (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id),
    community_id        UUID         NOT NULL REFERENCES communities (id),
    storage_location_id UUID         NOT NULL REFERENCES storage_locations (id),
    temperature         NUMERIC(5,2) CHECK (temperature BETWEEN -50 AND 80),
    humidity            NUMERIC(5,2) CHECK (humidity BETWEEN 0 AND 100),
    source              VARCHAR(16)  NOT NULL CHECK (source IN ('MANUAL', 'SENSOR')),
    inspection          BOOLEAN      NOT NULL,
    notes               VARCHAR(1000),
    recorded_at         TIMESTAMPTZ  NOT NULL,
    recorded_by         UUID         NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_environmental_readings_content CHECK (temperature IS NOT NULL OR humidity IS NOT NULL OR inspection)
);
CREATE INDEX ix_environmental_readings_location_time ON environmental_readings (storage_location_id, recorded_at DESC);

CREATE TABLE alerts (
    id                   UUID PRIMARY KEY,
    organization_id      UUID          NOT NULL REFERENCES organizations (id),
    community_id         UUID          NOT NULL REFERENCES communities (id),
    type                 VARCHAR(32)   NOT NULL,
    severity             VARCHAR(16)   NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    status               VARCHAR(16)   NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    subject_type         VARCHAR(32)   NOT NULL,
    subject_id           UUID,
    dedup_key            VARCHAR(160)  NOT NULL,
    message              VARCHAR(1000) NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    resolved_at          TIMESTAMPTZ,
    resolved_by          UUID REFERENCES users (id),
    resolution_notes     VARCHAR(1000),
    inspection_performed BOOLEAN,
    version              BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_alerts_open_dedup ON alerts (dedup_key) WHERE status = 'OPEN';
CREATE INDEX ix_alerts_community_status ON alerts (community_id, status);
