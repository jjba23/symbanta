-- symbanta
--
-- SPDX-License-Identifier: MIT
-- Copyright © Josep Bigorra <jjbigorra@gmail.com>

-- Pre-aggregated analytics, maintained by StatsRollupRefresher.
--
-- The dashboard used to aggregate the raw `events` log on every read, so its cost grew with the
-- whole reporting window. These tables hold the same facts one bucket at a time, keyed by session:
-- session ids are kept (instead of only counts) because visitors are distinct sessions, and a tab
-- can be active across many buckets; summing per-bucket counts would count that tab twice.
--
-- `bucket_width` carries both grains the dashboard needs: `hour` for intra-day reads and `day` for
-- the whole days before today, so a seven-day dashboard reads one row per session per day instead
-- of one row per event.

CREATE TABLE stats_session_bucket
(
    property_id UUID        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    bucket_width TEXT       NOT NULL,
    bucket      TIMESTAMPTZ NOT NULL,
    session_id  TEXT        NOT NULL,
    events      BIGINT      NOT NULL,
    page_views  BIGINT      NOT NULL,
    active_us   BIGINT      NOT NULL,
    first_at    TIMESTAMPTZ NOT NULL,
    last_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (property_id, bucket_width, bucket, session_id),
    CONSTRAINT stats_session_bucket_width_check CHECK (bucket_width IN ('hour', 'day'))
);

COMMENT ON TABLE stats_session_bucket IS 'Per-session analytics per hour and per local day; the dashboard read source.';
COMMENT ON COLUMN stats_session_bucket.bucket_width IS 'Rollup grain: hour (UTC-aligned) or day (reporting time zone midnight).';
COMMENT ON COLUMN stats_session_bucket.bucket IS 'Start of the bucket; UTC hour start, or local midnight for day rows.';
COMMENT ON COLUMN stats_session_bucket.session_id IS 'Session id as sent by the tracker; the empty string stands for events without one.';
COMMENT ON COLUMN stats_session_bucket.events IS 'Events recorded in this bucket for the session.';
COMMENT ON COLUMN stats_session_bucket.page_views IS 'Page-view events recorded in this bucket for the session.';
COMMENT ON COLUMN stats_session_bucket.active_us IS 'Active time in microseconds between consecutive events inside the bucket (gaps of at most 30 seconds).';
COMMENT ON COLUMN stats_session_bucket.first_at IS 'Timestamp of the first event of the session in this bucket.';
COMMENT ON COLUMN stats_session_bucket.last_at IS 'Timestamp of the last event of the session in this bucket.';

CREATE INDEX idx_stats_session_bucket_bucket ON stats_session_bucket (bucket_width, bucket);

-- Breakdown dimensions, one row per session per bucket per dimension value. `value_is_null`
-- preserves the difference between a missing value (no URL, no user agent) and an empty one,
-- which the raw queries expressed as a NULL group.
CREATE TABLE stats_dimension_bucket
(
    property_id   UUID        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    dimension     TEXT        NOT NULL,
    bucket_width  TEXT        NOT NULL,
    bucket        TIMESTAMPTZ NOT NULL,
    value         TEXT        NOT NULL,
    value_is_null BOOLEAN     NOT NULL,
    session_id    TEXT        NOT NULL,
    events        BIGINT      NOT NULL,
    page_views    BIGINT      NOT NULL,
    PRIMARY KEY (property_id, dimension, bucket_width, bucket, value, value_is_null, session_id),
    CONSTRAINT stats_dimension_bucket_width_check CHECK (bucket_width IN ('hour', 'day'))
);

COMMENT ON TABLE stats_dimension_bucket IS 'Per-session breakdown counts per bucket for routes, countries, screen sizes, user agents and event names.';
COMMENT ON COLUMN stats_dimension_bucket.dimension IS 'One of route, country, screen, user_agent, event.';
COMMENT ON COLUMN stats_dimension_bucket.value IS 'Dimension value; the empty string when the source column was NULL (see value_is_null).';
COMMENT ON COLUMN stats_dimension_bucket.value_is_null IS 'True when the source value was NULL, so reads can group it separately.';
COMMENT ON COLUMN stats_dimension_bucket.events IS 'Events in this bucket for the session with this dimension value.';
COMMENT ON COLUMN stats_dimension_bucket.page_views IS 'Page-view events in this bucket for the session with this dimension value.';

CREATE INDEX idx_stats_dimension_bucket_bucket ON stats_dimension_bucket (bucket_width, bucket);

-- How far the rollups are valid. Reads serve [covered] from these tables and aggregate only the
-- remainder from `events`, so a fresh deployment or a backfill in progress still answers correctly.
-- NULL means nothing has been rolled up yet.
CREATE TABLE stats_rollup_state
(
    name            TEXT PRIMARY KEY,
    covered_through TIMESTAMPTZ
);

COMMENT ON TABLE stats_rollup_state IS 'Coverage watermark of the pre-aggregated analytics tables.';
COMMENT ON COLUMN stats_rollup_state.covered_through IS 'Instants before this one are fully represented in the rollup tables; NULL when none are.';

INSERT INTO stats_rollup_state (name, covered_through)
VALUES ('analytics', NULL)
ON CONFLICT (name) DO NOTHING;
