CREATE TABLE traveler_profiles (
    user_id     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    profile     JSONB        NOT NULL,
    quiz        JSONB,
    provenance  JSONB        NOT NULL,
    edited      BOOLEAN      NOT NULL DEFAULT false,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE trips (
    id                   UUID PRIMARY KEY,
    name                 VARCHAR(120)  NOT NULL,
    destination_name     VARCHAR(300)  NOT NULL,
    destination_lat      DOUBLE PRECISION,
    destination_lng      DOUBLE PRECISION,
    start_date           DATE          NOT NULL,
    end_date             DATE          NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    budget_total         NUMERIC(12, 2),
    -- NULL = simple majority of members (spec §5.8 default)
    quorum_min_approvals INT,
    created_by           UUID          NOT NULL REFERENCES users (id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT trips_dates_ck CHECK (end_date >= start_date),
    CONSTRAINT trips_budget_ck CHECK (budget_total IS NULL OR budget_total >= 0)
);

CREATE TABLE trip_members (
    trip_id   UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL CHECK (role IN ('ORGANIZER', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id)
);
CREATE INDEX trip_members_user_idx ON trip_members (user_id);

CREATE TABLE invites (
    id         UUID PRIMARY KEY,
    trip_id    UUID        NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX invites_trip_idx ON invites (trip_id);

CREATE TABLE trip_preferences (
    trip_id        UUID          NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    user_id        UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    budget_min     NUMERIC(12, 2),
    budget_max     NUMERIC(12, 2),
    must_haves     JSONB         NOT NULL DEFAULT '[]',
    budget_visible BOOLEAN       NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id),
    CONSTRAINT trip_prefs_budget_ck CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)
);

CREATE TABLE ai_cache (
    input_hash     VARCHAR(64) PRIMARY KEY,
    feature        VARCHAR(40)  NOT NULL,
    prompt_version VARCHAR(40)  NOT NULL,
    model          VARCHAR(200) NOT NULL,
    result         JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ai_call_log (
    id                BIGSERIAL PRIMARY KEY,
    feature           VARCHAR(40)  NOT NULL,
    prompt_version    VARCHAR(40)  NOT NULL,
    model             VARCHAR(200) NOT NULL,
    input_hash        VARCHAR(64)     NOT NULL,
    attempt           INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    prompt_tokens     INT,
    completion_tokens INT,
    latency_ms        INT,
    error             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ai_call_log_created_idx ON ai_call_log (created_at DESC);
