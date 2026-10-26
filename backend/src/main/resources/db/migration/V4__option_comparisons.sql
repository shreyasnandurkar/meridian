-- Compare tab (spec §5.6): side-by-side options with provider, price and when the price was checked.
CREATE TABLE option_comparisons (
    id         UUID PRIMARY KEY,
    trip_id    UUID         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    kind       VARCHAR(12)  NOT NULL CHECK (kind IN ('STAY', 'FLIGHT', 'ACTIVITY', 'TRANSPORT', 'OTHER')),
    title      VARCHAR(120) NOT NULL,
    created_by UUID         NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX option_comparisons_trip_idx ON option_comparisons (trip_id, created_at);

CREATE TABLE option_rows (
    id            UUID PRIMARY KEY,
    comparison_id UUID           NOT NULL REFERENCES option_comparisons (id) ON DELETE CASCADE,
    provider      VARCHAR(80)    NOT NULL,
    title         VARCHAR(300),
    price         NUMERIC(14, 2) CHECK (price >= 0),
    currency      VARCHAR(3),
    -- when someone last confirmed the price; required whenever there is a price
    checked_at    TIMESTAMPTZ,
    source_url    VARCHAR(2048),
    -- set by members when a listing was promoted; shown as a label, never used for ordering
    sponsored     BOOLEAN        NOT NULL DEFAULT false,
    notes         VARCHAR(1000),
    -- which provider adapter filled the row ('manual' when typed in)
    adapter       VARCHAR(30)    NOT NULL DEFAULT 'manual',
    added_by      UUID           NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT option_rows_price_ck CHECK (
        (price IS NULL AND checked_at IS NULL) OR (price IS NOT NULL AND currency IS NOT NULL AND checked_at IS NOT NULL))
);
CREATE INDEX option_rows_comparison_idx ON option_rows (comparison_id, created_at);
