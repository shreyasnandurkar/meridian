-- Votes on change proposals (spec §5.8): one per member per proposal, changeable while the proposal is open.
CREATE TABLE votes (
    id          UUID PRIMARY KEY,
    proposal_id UUID        NOT NULL REFERENCES proposals (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id),
    value       VARCHAR(10) NOT NULL CHECK (value IN ('APPROVE', 'REJECT')),
    voted_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT votes_once UNIQUE (proposal_id, user_id)
);

-- Each member's verdict on the itinerary as a whole, for the consensus gate. It counts only while `version` matches
-- the itinerary's version; any change makes it out of date (a change the member approved carries it forward).
CREATE TABLE plan_approvals (
    itinerary_id UUID        NOT NULL REFERENCES itineraries (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id),
    value        VARCHAR(10) NOT NULL CHECK (value IN ('APPROVE', 'REJECT')),
    version      INT         NOT NULL,
    voted_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (itinerary_id, user_id)
);

-- Locking closes open proposals unresolved.
ALTER TABLE proposals DROP CONSTRAINT proposals_status_check;
ALTER TABLE proposals ADD CONSTRAINT proposals_status_check
    CHECK (status IN ('OPEN', 'WITHDRAWN', 'APPROVED', 'REJECTED', 'STALE', 'CLOSED'));

ALTER TABLE itineraries ADD COLUMN locked_at TIMESTAMPTZ;
ALTER TABLE itineraries ADD COLUMN locked_by UUID REFERENCES users (id);

-- What the group decided and how (spec §5.8). Append-only: rows are never deleted, and after insert only the
-- summary columns may change (the AI rewrites the template summary once the vote has closed).
CREATE TABLE decision_log (
    id                UUID PRIMARY KEY,
    trip_id           UUID          NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    seq               INT           NOT NULL,
    kind              VARCHAR(20)   NOT NULL
        CHECK (kind IN ('CHANGE_APPROVED', 'CHANGE_REJECTED', 'PLAN_LOCKED', 'LOCK_OVERRIDE', 'PLAN_UNLOCKED')),
    title             VARCHAR(300)  NOT NULL,
    -- what the group chose between, e.g. ["Move Fort Aguada to day 2", "Keep the plan as it is"]
    alternatives      JSONB         NOT NULL DEFAULT '[]',
    -- the votes as they stood when the decision was made: [{userId, displayName, value, at}]
    votes             JSONB         NOT NULL DEFAULT '[]',
    approvals         INT           NOT NULL,
    rejections        INT           NOT NULL,
    required          INT           NOT NULL,
    member_count      INT           NOT NULL,
    -- members who had not voted: [{userId, displayName}]
    not_voted         JSONB         NOT NULL DEFAULT '[]',
    proposal_id       UUID REFERENCES proposals (id),
    reason            VARCHAR(500),
    itinerary_version INT,
    -- shortlist items the decision is about, so itinerary stops can link to it
    item_ids          JSONB         NOT NULL DEFAULT '[]',
    decided_by        UUID REFERENCES users (id),
    decided_at        TIMESTAMPTZ   NOT NULL,
    summary           VARCHAR(600)  NOT NULL,
    summary_source    VARCHAR(10)   NOT NULL CHECK (summary_source IN ('AI', 'RULES')),
    summary_model     VARCHAR(200),
    summary_prompt    VARCHAR(80),
    summary_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT decision_log_seq UNIQUE (trip_id, seq)
);
CREATE INDEX decision_log_trip_idx ON decision_log (trip_id, seq);

CREATE FUNCTION decision_log_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- only the demo seed's reset may purge, and it says so explicitly
        IF current_setting('meridian.purge', true) = 'on' THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'decision_log is append-only';
    END IF;
    IF (NEW.id, NEW.trip_id, NEW.seq, NEW.kind, NEW.title, NEW.alternatives, NEW.votes, NEW.approvals,
        NEW.rejections, NEW.required, NEW.member_count, NEW.not_voted, NEW.proposal_id, NEW.reason,
        NEW.itinerary_version, NEW.item_ids, NEW.decided_by, NEW.decided_at)
        IS DISTINCT FROM
       (OLD.id, OLD.trip_id, OLD.seq, OLD.kind, OLD.title, OLD.alternatives, OLD.votes, OLD.approvals,
        OLD.rejections, OLD.required, OLD.member_count, OLD.not_voted, OLD.proposal_id, OLD.reason,
        OLD.itinerary_version, OLD.item_ids, OLD.decided_by, OLD.decided_at) THEN
        RAISE EXCEPTION 'decision_log is append-only: only the summary can be rewritten';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER decision_log_append_only
    BEFORE UPDATE OR DELETE ON decision_log
    FOR EACH ROW EXECUTE FUNCTION decision_log_append_only();
