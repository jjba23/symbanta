-- symbanta
--
-- SPDX-License-Identifier: MIT
-- Copyright © Josep Bigorra <jjbigorra@gmail.com>

-- #7: coarse client location and an allow-listed subset of request headers.
-- No IP address, cookie or authorization header is ever stored.
ALTER TABLE events ADD COLUMN IF NOT EXISTS country VARCHAR(2);
ALTER TABLE events ADD COLUMN IF NOT EXISTS headers JSONB;

COMMENT ON COLUMN events.country IS 'Coarse ISO-3166 alpha-2 country of the client, derived from CDN/proxy headers.';
COMMENT ON COLUMN events.headers IS 'Allow-listed request headers (user-agent, accept-language, referer, sec-ch-ua, ...).';

CREATE INDEX IF NOT EXISTS idx_events_property_country ON events (property_id, country);
CREATE INDEX IF NOT EXISTS idx_events_property_name_created ON events (property_id, event_name, created_at DESC);

-- #4: per-application event type registry. Event names stay free-form in events.event_name;
-- this table exists so dashboards can discover and describe the events an application emits.
CREATE TABLE IF NOT EXISTS event_types
(
    id          UUID        NOT NULL PRIMARY KEY,
    property_id UUID        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT event_types_property_name_unique UNIQUE (property_id, name)
);

COMMENT ON TABLE event_types IS 'Event names registered for an application (page-view, click, signup-click, console, ...).';
COMMENT ON COLUMN event_types.name IS 'Event name as sent by the instrumented application.';
COMMENT ON COLUMN event_types.description IS 'Optional human readable description shown in dashboards.';

CREATE INDEX IF NOT EXISTS idx_event_types_property ON event_types (property_id);

-- Backfill the registry from the events already stored, so existing deployments immediately
-- expose their known event names.
INSERT INTO event_types (id, property_id, name, description, created_at)
SELECT gen_random_uuid(), e.property_id, e.event_name, NULL, MIN(e.created_at)
FROM events e
GROUP BY e.property_id, e.event_name
ON CONFLICT (property_id, name) DO NOTHING;
