-- One itinerary per trip. Regenerating replaces its days; every change bumps version so proposals know what they
-- were made against.
CREATE TABLE itineraries (
    id           UUID PRIMARY KEY,
    trip_id      UUID        NOT NULL UNIQUE REFERENCES trips (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'LOCKED')),
    version      INT         NOT NULL DEFAULT 1,
    pace         VARCHAR(10) NOT NULL CHECK (pace IN ('SLOW', 'BALANCED', 'PACKED')),
    -- shortlist items that are not on any day, with the reason (no location, a stay, did not fit)
    unplaced     JSONB       NOT NULL DEFAULT '[]',
    generated_by UUID        NOT NULL REFERENCES users (id),
    generated_at TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE itinerary_days (
    id             UUID PRIMARY KEY,
    itinerary_id   UUID         NOT NULL REFERENCES itineraries (id) ON DELETE CASCADE,
    day_index      INT          NOT NULL CHECK (day_index >= 1),
    date           DATE         NOT NULL,
    summary        VARCHAR(400),
    source         VARCHAR(10)  NOT NULL CHECK (source IN ('AI', 'RULES')),
    model          VARCHAR(200),
    prompt_version VARCHAR(80),
    -- what code changed after the model answered ("moved lunch to 13:10 to allow travel"), shown under "Why?"
    repairs        JSONB        NOT NULL DEFAULT '[]',
    CONSTRAINT itinerary_days_uq UNIQUE (itinerary_id, day_index)
);

CREATE TABLE itinerary_items (
    id                UUID PRIMARY KEY,
    itinerary_id      UUID         NOT NULL REFERENCES itineraries (id) ON DELETE CASCADE,
    day_id            UUID         NOT NULL REFERENCES itinerary_days (id) ON DELETE CASCADE,
    shortlist_item_id UUID         NOT NULL REFERENCES shortlist_items (id),
    position          INT          NOT NULL,
    start_time        TIME         NOT NULL,
    duration_min      INT          NOT NULL CHECK (duration_min > 0),
    -- from the previous stop of the same day; 0 for the first stop
    travel_min        INT          NOT NULL DEFAULT 0,
    travel_km         DOUBLE PRECISION,
    note              VARCHAR(300),
    CONSTRAINT itinerary_items_once UNIQUE (itinerary_id, shortlist_item_id)
);
CREATE INDEX itinerary_items_day_idx ON itinerary_items (day_id, position);

-- A member's suggested change to the itinerary. Targets are shortlist items and day numbers, which survive edits.
CREATE TABLE proposals (
    id           UUID PRIMARY KEY,
    trip_id      UUID         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    itinerary_id UUID         NOT NULL REFERENCES itineraries (id) ON DELETE CASCADE,
    type         VARCHAR(10)  NOT NULL CHECK (type IN ('MOVE', 'SWAP', 'REMOVE', 'ADD')),
    payload      JSONB        NOT NULL,
    -- written by code from the payload, e.g. "Move Fort Aguada from day 2 to day 4"
    summary      VARCHAR(300) NOT NULL,
    reason       VARCHAR(500),
    status       VARCHAR(12)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'WITHDRAWN', 'APPROVED', 'REJECTED', 'STALE')),
    base_version INT          NOT NULL,
    created_by   UUID         NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL,
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX proposals_trip_idx ON proposals (trip_id, created_at);

-- Flat comments on a shortlisted place (also shown on its itinerary stop) or on a proposal.
CREATE TABLE comments (
    id          UUID PRIMARY KEY,
    trip_id     UUID          NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    target_type VARCHAR(20)   NOT NULL CHECK (target_type IN ('SHORTLIST_ITEM', 'PROPOSAL')),
    target_id   UUID          NOT NULL,
    author_id   UUID          NOT NULL REFERENCES users (id),
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX comments_target_idx ON comments (trip_id, target_type, target_id, created_at);
