-- Doadores, alimentos, locais de armazenamento, doações, lotes e livro-razão de estoque.

CREATE TABLE donors (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    type            VARCHAR(16)  NOT NULL CHECK (type IN ('INDIVIDUAL', 'ORGANIZATION')),
    name            VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    notes           VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_donors_org ON donors (organization_id);

CREATE TABLE foods (
    id                      UUID PRIMARY KEY,
    organization_id         UUID          NOT NULL REFERENCES organizations (id),
    name                    VARCHAR(120)  NOT NULL CHECK (length(trim(name)) > 0),
    category                VARCHAR(32)   NOT NULL,
    unit                    VARCHAR(16)   NOT NULL CHECK (unit IN ('KILOGRAM', 'LITER', 'UNIT', 'PACKAGE')),
    description             VARCHAR(500),
    storage_guidance        VARCHAR(1000),
    -- Antecedência, em dias, para o alerta de validade próxima deste alimento.
    expiration_warning_days INTEGER       NOT NULL DEFAULT 7 CHECK (expiration_warning_days BETWEEN 0 AND 365),
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_foods_org_name ON foods (organization_id, lower(name));

CREATE TABLE storage_locations (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    community_id    UUID         NOT NULL REFERENCES communities (id),
    name            VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    description     VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_storage_locations_name ON storage_locations (community_id, lower(name));

CREATE TABLE donations (
    id                 UUID PRIMARY KEY,
    organization_id    UUID          NOT NULL REFERENCES organizations (id),
    community_id       UUID          NOT NULL REFERENCES communities (id),
    donor_id           UUID REFERENCES donors (id),
    origin_description VARCHAR(200),
    received_at        TIMESTAMPTZ   NOT NULL,
    notes              VARCHAR(1000),
    registered_by      UUID          NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ   NOT NULL,
    -- RN03: toda entrada tem origem identificável.
    CONSTRAINT ck_donations_origin CHECK (donor_id IS NOT NULL OR length(trim(origin_description)) > 0)
);
CREATE INDEX ix_donations_community_time ON donations (community_id, received_at DESC);

CREATE TABLE donation_items (
    id          UUID PRIMARY KEY,
    donation_id UUID          NOT NULL REFERENCES donations (id),
    food_id     UUID          NOT NULL REFERENCES foods (id),
    quantity    NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    unit        VARCHAR(16)   NOT NULL,
    notes       VARCHAR(500),
    position    INTEGER       NOT NULL
);
CREATE INDEX ix_donation_items_donation ON donation_items (donation_id);

CREATE TABLE food_batches (
    id                  UUID PRIMARY KEY,
    organization_id     UUID          NOT NULL REFERENCES organizations (id),
    community_id        UUID          NOT NULL REFERENCES communities (id),
    food_id             UUID          NOT NULL REFERENCES foods (id),
    storage_location_id UUID REFERENCES storage_locations (id),
    donation_id         UUID REFERENCES donations (id),
    donation_item_id    UUID REFERENCES donation_items (id),
    origin_description  VARCHAR(200),
    initial_quantity    NUMERIC(14,3) NOT NULL CHECK (initial_quantity > 0),
    -- RN01: saldo nunca negativo (última linha de defesa; a regra vive no domínio).
    available_quantity  NUMERIC(14,3) NOT NULL CHECK (available_quantity >= 0),
    unit                VARCHAR(16)   NOT NULL,
    received_at         TIMESTAMPTZ   NOT NULL,
    expiration_status   VARCHAR(16)   NOT NULL CHECK (expiration_status IN ('KNOWN', 'NOT_APPLICABLE', 'UNKNOWN')),
    expiration_date     DATE,
    notes               VARCHAR(500),
    status              VARCHAR(16)   NOT NULL CHECK (status IN ('AVAILABLE', 'BLOCKED', 'DEPLETED')),
    status_reason       VARCHAR(500),
    created_by          UUID          NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_food_batches_expiration CHECK ((expiration_status = 'KNOWN') = (expiration_date IS NOT NULL)),
    CONSTRAINT ck_food_batches_origin CHECK (donation_item_id IS NOT NULL OR length(trim(origin_description)) > 0)
);
CREATE INDEX ix_food_batches_community_food ON food_batches (community_id, food_id);
CREATE INDEX ix_food_batches_expiring ON food_batches (community_id, expiration_date)
    WHERE status = 'AVAILABLE' AND expiration_date IS NOT NULL;
CREATE INDEX ix_food_batches_donation ON food_batches (donation_id);

-- Livro-razão: cada alteração de saldo tem exatamente uma movimentação.
CREATE TABLE stock_movements (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organizations (id),
    community_id    UUID          NOT NULL REFERENCES communities (id),
    batch_id        UUID          NOT NULL REFERENCES food_batches (id),
    food_id         UUID          NOT NULL REFERENCES foods (id),
    type            VARCHAR(16)   NOT NULL,
    quantity_delta  NUMERIC(14,3) NOT NULL CHECK (quantity_delta <> 0),
    balance_after   NUMERIC(14,3) NOT NULL CHECK (balance_after >= 0),
    source_type     VARCHAR(24)   NOT NULL,
    source_id       UUID,
    justification   VARCHAR(500),
    performed_by    UUID          NOT NULL REFERENCES users (id),
    occurred_at     TIMESTAMPTZ   NOT NULL,
    recorded_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_stock_movements_direction CHECK (
        (type = 'ENTRY' AND quantity_delta > 0)
        OR (type IN ('DISTRIBUTION', 'LOSS') AND quantity_delta < 0)
        OR type = 'ADJUSTMENT'),
    -- RN10: ajuste exige justificativa.
    CONSTRAINT ck_stock_movements_justification CHECK (
        type <> 'ADJUSTMENT' OR length(trim(justification)) >= 5)
);
CREATE INDEX ix_stock_movements_batch ON stock_movements (batch_id, occurred_at);
CREATE INDEX ix_stock_movements_community_time ON stock_movements (community_id, recorded_at DESC);

-- RN09: movimentações nunca são alteradas nem apagadas.
CREATE FUNCTION prevent_stock_movement_changes() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'stock_movements is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION prevent_stock_movement_changes();

CREATE TABLE stock_minimums (
    community_id     UUID          NOT NULL REFERENCES communities (id),
    food_id          UUID          NOT NULL REFERENCES foods (id),
    organization_id  UUID          NOT NULL REFERENCES organizations (id),
    minimum_quantity NUMERIC(14,3) NOT NULL CHECK (minimum_quantity >= 0),
    updated_at       TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (community_id, food_id)
);
