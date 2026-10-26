-- Places fetched from a PlacesClient (OpenStreetMap today), shared across trips.
CREATE TABLE places (
    id               UUID PRIMARY KEY,
    source           VARCHAR(20)      NOT NULL,
    source_id        VARCHAR(80)      NOT NULL,
    name             VARCHAR(300)     NOT NULL,
    category         VARCHAR(20)      NOT NULL CHECK (category IN ('SIGHTS', 'FOOD', 'STAYS', 'ACTIVITIES', 'OTHER')),
    lat              DOUBLE PRECISION NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,
    address          VARCHAR(500),
    website          VARCHAR(2048),
    tags             JSONB            NOT NULL DEFAULT '{}',
    -- higher = more famous (wiki links, attraction tags, chain brand); feeds the anti-tourist-trap scorer
    popularity_proxy INT              NOT NULL DEFAULT 0,
    fetched_at       TIMESTAMPTZ      NOT NULL,
    CONSTRAINT places_source_uq UNIQUE (source, source_id)
);
CREATE INDEX places_lat_lng_idx ON places (lat, lng);

-- Which places a provider query returned, so repeated queries (and provider outages) are served from the database.
CREATE TABLE place_search_cache (
    cache_key  VARCHAR(400) PRIMARY KEY,
    place_ids  JSONB        NOT NULL,
    fetched_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE shortlist_items (
    id         UUID PRIMARY KEY,
    trip_id    UUID             NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    kind       VARCHAR(10)      NOT NULL CHECK (kind IN ('PLACE', 'LINK', 'PIN')),
    place_id   UUID REFERENCES places (id),
    url        VARCHAR(2048),
    -- link title or pin name; PLACE items use the place's name
    title      VARCHAR(300),
    lat        DOUBLE PRECISION,
    lng        DOUBLE PRECISION,
    category   VARCHAR(20)      NOT NULL CHECK (category IN ('SIGHTS', 'FOOD', 'STAYS', 'ACTIVITIES', 'OTHER')),
    added_by   UUID             NOT NULL REFERENCES users (id),
    reason     VARCHAR(500),
    notes      VARCHAR(2000),
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT shortlist_kind_ck CHECK (
        (kind = 'PLACE' AND place_id IS NOT NULL AND url IS NULL)
        OR (kind = 'LINK' AND url IS NOT NULL AND place_id IS NULL AND title IS NOT NULL)
        OR (kind = 'PIN' AND place_id IS NULL AND url IS NULL AND title IS NOT NULL
            AND lat IS NOT NULL AND lng IS NOT NULL))
);
CREATE INDEX shortlist_trip_idx ON shortlist_items (trip_id, created_at);
CREATE UNIQUE INDEX shortlist_trip_place_uq ON shortlist_items (trip_id, place_id) WHERE place_id IS NOT NULL;
CREATE UNIQUE INDEX shortlist_trip_url_uq ON shortlist_items (trip_id, url) WHERE url IS NOT NULL;

CREATE TABLE reactions (
    item_id    UUID         NOT NULL REFERENCES shortlist_items (id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    value      VARCHAR(10)  NOT NULL CHECK (value IN ('LOVE', 'OK', 'NO_WAY')),
    reason     VARCHAR(300),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (item_id, user_id)
);
